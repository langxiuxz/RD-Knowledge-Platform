package com.studyagent.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.ai.client.AiClient;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.config.TaskRecoveryProperties;
import com.studyagent.document.mapper.DocumentMapper;
import com.studyagent.plan.mapper.StudyPlanMapper;
import com.studyagent.plan.mapper.StudyPlanTaskMapper;
import com.studyagent.plan.service.StudyPlanGenerationPersistenceService;
import com.studyagent.quiz.mapper.QuizMapper;
import com.studyagent.quiz.mapper.QuizQuestionMapper;
import com.studyagent.quiz.service.QuizGenerationPersistenceService;
import com.studyagent.task.service.TaskRecoveryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Opt-in real MySQL tests. Always use a new disposable schema, never the application schema. */
@EnabledIfEnvironmentVariable(named = "TEST_MYSQL_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskRecoveryMySqlTest {
    private final String schema = "studyagent_recovery_test_" + UUID.randomUUID().toString().replace("-", "");
    private String adminUrl;
    private String username;
    private String password;
    private boolean schemaCreated;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    private DocumentMapper documents;
    private QuizMapper quizzes;
    private StudyPlanMapper plans;
    private QuizQuestionMapper questions;
    private StudyPlanTaskMapper tasks;

    @BeforeAll
    void createIsolatedSchema() throws Exception {
        adminUrl = System.getenv("TEST_MYSQL_URL");
        username = System.getenv("TEST_MYSQL_USERNAME");
        password = System.getenv("TEST_MYSQL_PASSWORD");
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
            schemaCreated = true;
        }
        URI source = URI.create(adminUrl.substring("jdbc:".length()));
        String targetUrl = "jdbc:mysql://" + source.getRawAuthority() + "/" + schema
                + (source.getRawQuery() == null ? "" : "?" + source.getRawQuery());
        dataSource = new DriverManagerDataSource(targetUrl, username, password);
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__init_schema.sql"));
        }
        jdbc = new JdbcTemplate(dataSource);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        var resolver = new PathMatchingResourcePatternResolver();
        factory.setMapperLocations(resolver.getResources("classpath*:mapper/**/*.xml"));
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        documents = session.getMapper(DocumentMapper.class);
        quizzes = session.getMapper(QuizMapper.class);
        plans = session.getMapper(StudyPlanMapper.class);
        questions = session.getMapper(QuizQuestionMapper.class);
        tasks = session.getMapper(StudyPlanTaskMapper.class);
    }

    @AfterAll
    void dropOnlyTheGeneratedSchema() throws Exception {
        if (!schemaCreated) return;
        if (!schema.matches("studyagent_recovery_test_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test schema");
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE " + schema);
        }
    }

    @BeforeEach
    void seedUser() {
        jdbc.update("DELETE FROM sa_user");
        jdbc.update("INSERT INTO sa_user(id,username,password_hash,nickname) VALUES (1,'test-user','test-hash','test')");
        jdbc.update("INSERT INTO sa_knowledge_base(id,user_id,name) VALUES (10,1,'test-kb')");
    }

    @Test
    void recovery_afterServiceRecreation_shouldCloseAllAbandonedStagesAndRemainIdempotent() {
        long id = 1;
        for (String status : List.of("UPLOADED", "PARSING", "CHUNKING", "EMBEDDING")) document(id++, status, true);
        quiz("PROCESSING", true);
        plan("PROCESSING", true);
        // Only SQL rows survive; a new recovery instance has no prior in-memory task state.
        recovery(100).recover();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_document WHERE status='FAILED' AND error_message IS NOT NULL", Integer.class)).isEqualTo(4);
        assertThat(quizzes.findByIdAndUserId(50L, 1L).getStatus()).isEqualTo("FAILED");
        assertThat(plans.findByIdAndUserId(60L, 1L).getGenerationStatus()).isEqualTo("FAILED");
        assertThat(plans.findByIdAndUserId(60L, 1L).getStatus()).isEqualTo("ACTIVE");
        assertThat(documents.failStaleTasks(900, 100, "timeout")).isZero();
        assertThat(quizzes.failStaleTasks(300, 100, "timeout")).isZero();
        assertThat(plans.failStaleTasks(300, 100, "timeout")).isZero();
    }

    @Test
    void recovery_shouldPreserveRecentTasksAndTerminalResults() {
        document(1, "EMBEDDING", false);
        document(2, "READY", true);
        document(3, "FAILED", true);
        quiz("READY", true);
        plan("READY", true);
        recovery(100).recover();
        assertThat(documents.findById(1L).getStatus()).isEqualTo("EMBEDDING");
        assertThat(documents.findById(2L).getStatus()).isEqualTo("READY");
        assertThat(quizzes.findByIdAndUserId(50L, 1L).getStatus()).isEqualTo("READY");
        assertThat(plans.findByIdAndUserId(60L, 1L).getGenerationStatus()).isEqualTo("READY");
    }

    @Test
    void recovery_shouldBoundEachBatchAndDrainOnLaterScans() {
        document(1, "PARSING", true);
        document(2, "CHUNKING", true);
        assertThat(documents.failStaleTasks(900, 1, "timeout")).isEqualTo(1);
        assertThat(documents.findById(1L).getStatus()).isEqualTo("FAILED");
        assertThat(documents.findById(2L).getStatus()).isEqualTo("CHUNKING");
        assertThat(documents.failStaleTasks(900, 1, "timeout")).isEqualTo(1);
        assertThat(documents.failStaleTasks(900, 1, "timeout")).isZero();
    }

    @Test
    void lateDocumentResult_shouldNotOverwriteTimeoutAndOldTaskShouldNotOverwriteRetry() {
        document(1, "EMBEDDING", true);
        recovery(100).recover();
        assertThat(documents.updateProgressIfCurrentTask(1L, "task-1", "READY", 100, 3, null, true)).isZero();
        var retry = documents.findByIdAndUserId(1L, 1L);
        retry.setCurrentTaskId("task-new");
        retry.setStatus("UPLOADED");
        retry.setProgress(0);
        retry.setChunkCount(0);
        assertThat(documents.updateTaskStart(retry)).isEqualTo(1);
        assertThat(documents.updateTaskStart(retry)).isZero();
        assertThat(documents.updateProgressIfCurrentTask(1L, "task-1", "FAILED", 0, 0, "old error", false)).isZero();
        assertThat(documents.findById(1L).getStatus()).isEqualTo("UPLOADED");
    }

    @Test
    void quizComplete_whenTimeoutWinsBetweenReadAndWrite_shouldRollBackChildQuestions() {
        quiz("PROCESSING", true);
        QuizQuestionMapper intercepted = mock(QuizQuestionMapper.class);
        doAnswer(invocation -> {
            // The service has already read PROCESSING, but has not written any child row.
            expireOutsideTransaction("UPDATE sa_quiz SET status='FAILED',error_message='timeout' "
                    + "WHERE id=50 AND status='PROCESSING'");
            return questions.insert(invocation.getArgument(0));
        }).when(intercepted).insert(any());
        var service = transactional(new QuizGenerationPersistenceService(quizzes, intercepted, new ObjectMapper()));
        var response = new AiClient.QuizGenerateResponse(List.of(
                new AiClient.GeneratedQuestion("JUDGE", "Java", "test?", null, "true", "explanation")));
        assertThatThrownBy(() -> service.complete(1L, 50L, response)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_quiz_question", Integer.class)).isZero();
        assertThat(quizzes.findByIdAndUserId(50L, 1L).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void planComplete_whenTimeoutWinsBetweenReadAndWrite_shouldRollBackChildTasks() {
        plan("PROCESSING", true);
        StudyPlanTaskMapper intercepted = mock(StudyPlanTaskMapper.class);
        doAnswer(invocation -> {
            expireOutsideTransaction("UPDATE sa_study_plan SET generation_status='FAILED',error_message='timeout' "
                    + "WHERE id=60 AND generation_status='PROCESSING'");
            return tasks.insert(invocation.getArgument(0));
        }).when(intercepted).insert(any());
        var service = transactional(new StudyPlanGenerationPersistenceService(plans, intercepted, new ObjectMapper()));
        String date = jdbc.queryForObject("SELECT CAST(CURRENT_DATE AS CHAR)", String.class);
        var response = new AiClient.PlanGenerateResponse("test", List.of(
                new AiClient.GeneratedPlanTask(date, "Java", "READ", 15)));
        assertThatThrownBy(() -> service.complete(1L, 60L, response)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_study_plan_task", Integer.class)).isZero();
        assertThat(plans.findByIdAndUserId(60L, 1L).getGenerationStatus()).isEqualTo("FAILED");
    }

    private void expireOutsideTransaction(String sql) throws Exception {
        // A separate autocommit connection models the scheduler's independent transaction.
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            assertThat(statement.executeUpdate(sql)).isEqualTo(1);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T transactional(T service) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new DataSourceTransactionManager(dataSource));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(interceptor);
        return (T) proxy.getProxy();
    }

    private TaskRecoveryService recovery(int batchSize) {
        return new TaskRecoveryService(documents, quizzes, plans,
                new TaskRecoveryProperties(true, Duration.ofMinutes(15), Duration.ofMinutes(5), batchSize));
    }

    private void document(long id, String status, boolean expired) {
        jdbc.update("INSERT INTO sa_document(id,knowledge_base_id,user_id,file_name,file_type,storage_key,file_url,status,progress,current_task_id) "
                + "VALUES (?,10,1,'test.txt','txt','test-key','http://spring/internal/files/test',?,0,?)", id, status, "task-" + id);
        if (expired) jdbc.update("UPDATE sa_document SET update_time=TIMESTAMPADD(HOUR,-1,CURRENT_TIMESTAMP) WHERE id=?", id);
    }

    private void quiz(String status, boolean expired) {
        jdbc.update("INSERT INTO sa_quiz(id,user_id,title,difficulty,question_count,types_json,status) "
                + "VALUES (50,1,'test','EASY',1,JSON_ARRAY('JUDGE'),?)", status);
        if (expired) jdbc.update("UPDATE sa_quiz SET update_time=TIMESTAMPADD(HOUR,-1,CURRENT_TIMESTAMP) WHERE id=50");
    }

    private void plan(String status, boolean expired) {
        jdbc.update("INSERT INTO sa_study_plan(id,user_id,title,goal,start_date,end_date,daily_minutes,generation_status,status) "
                + "VALUES (60,1,'test','learn',CURRENT_DATE,CURRENT_DATE,30,?,'ACTIVE')", status);
        if (expired) jdbc.update("UPDATE sa_study_plan SET update_time=TIMESTAMPADD(HOUR,-1,CURRENT_TIMESTAMP) WHERE id=60");
    }
}
