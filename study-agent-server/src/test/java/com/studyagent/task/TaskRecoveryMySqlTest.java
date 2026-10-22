package com.studyagent.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.ai.client.AiClient;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.config.TaskRecoveryProperties;
import com.studyagent.insight.mapper.CapabilityReportMapper;
import com.studyagent.document.mapper.DocumentVersionMapper;
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
    private DocumentVersionMapper documents;
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
            for (String migration : List.of(
                    "V1__init_schema.sql",
                    "V2__long_term_memory.sql",
                    "V3__focus_learning_records.sql",
                    "V4__project_workspace.sql",
                    "V5__knowledge_project_ownership.sql",
                    "V6__document_versions_and_knowledge_snapshots.sql",
                    "V7__conversation_snapshot_citations.sql")) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/" + migration));
            }
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
        documents = session.getMapper(DocumentVersionMapper.class);
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
        jdbc.update("UPDATE sa_knowledge_base SET current_snapshot_id=NULL");
        jdbc.update("UPDATE sa_document SET current_published_version_id=NULL");
        jdbc.update("DELETE FROM sa_knowledge_snapshot_item");
        jdbc.update("DELETE FROM sa_document_version");
        jdbc.update("DELETE FROM sa_document");
        jdbc.update("DELETE FROM sa_knowledge_snapshot");
        jdbc.update("DELETE FROM sa_knowledge_base");
        jdbc.update("DELETE FROM sa_quiz_question");
        jdbc.update("DELETE FROM sa_quiz WHERE id=50");
        jdbc.update("DELETE FROM sa_study_plan_task WHERE plan_id=60");
        jdbc.update("DELETE FROM sa_study_plan WHERE id=60");
        jdbc.update("DELETE FROM sa_audit_log");
        jdbc.update("DELETE FROM sa_project_member");
        jdbc.update("DELETE FROM sa_project");
        jdbc.update("DELETE FROM sa_user");
        jdbc.update("INSERT INTO sa_user(id,username,password_hash,nickname) VALUES (1,'test-user','test-hash','test')");
        jdbc.update("INSERT INTO sa_project(id,name,code,created_by) VALUES (20,'Recovery Test','recovery-test',1)");
        jdbc.update("INSERT INTO sa_project_member(project_id,user_id,role,status) VALUES (20,1,'OWNER','ACTIVE')");
        jdbc.update("INSERT INTO sa_knowledge_base(id,project_id,created_by,name) VALUES (10,20,1,'test-kb')");
    }

    @Test
    void recovery_afterServiceRecreation_shouldCloseAllAbandonedStagesAndRemainIdempotent() {
        long id = 1;
        for (String status : List.of("UPLOADED", "PARSING", "CHUNKING", "EMBEDDING")) document(id++, status, true);
        quiz("PROCESSING", true);
        plan("PROCESSING", true);
        // Only SQL rows survive; a new recovery instance has no prior in-memory task state.
        recovery(100).recover();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_document_version WHERE process_status='FAILED' AND error_message IS NOT NULL", Integer.class)).isEqualTo(4);
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
        assertThat(documents.findById(1L).getProcessStatus()).isEqualTo("EMBEDDING");
        assertThat(documents.findById(2L).getProcessStatus()).isEqualTo("READY");
        assertThat(quizzes.findByIdAndUserId(50L, 1L).getStatus()).isEqualTo("READY");
        assertThat(plans.findByIdAndUserId(60L, 1L).getGenerationStatus()).isEqualTo("READY");
    }

    @Test
    void recovery_shouldBoundEachBatchAndDrainOnLaterScans() {
        document(1, "PARSING", true);
        document(2, "CHUNKING", true);
        assertThat(documents.failStaleTasks(900, 1, "timeout")).isEqualTo(1);
        assertThat(documents.findById(1L).getProcessStatus()).isEqualTo("FAILED");
        assertThat(documents.findById(2L).getProcessStatus()).isEqualTo("CHUNKING");
        assertThat(documents.failStaleTasks(900, 1, "timeout")).isEqualTo(1);
        assertThat(documents.failStaleTasks(900, 1, "timeout")).isZero();
    }

    @Test
    void lateDocumentResult_shouldNotOverwriteTimeoutAndOldTaskShouldNotOverwriteRetry() {
        document(1, "EMBEDDING", true);
        recovery(100).recover();
        assertThat(documents.updateProgress(1L, "task-1", "READY", 100, 3, null, null, true)).isZero();
        assertThat(documents.startTask(1L, "task-1", "task-new", "b".repeat(64))).isEqualTo(1);
        assertThat(documents.startTask(1L, "task-1", "task-new", "b".repeat(64))).isZero();
        assertThat(documents.updateProgress(1L, "task-1", "FAILED", 0, 0, "old error", null, false)).isZero();
        assertThat(documents.findById(1L).getProcessStatus()).isEqualTo("UPLOADED");
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
        return new TaskRecoveryService(documents, quizzes, plans, mock(CapabilityReportMapper.class),
                new TaskRecoveryProperties(true, Duration.ofMinutes(15), Duration.ofMinutes(5), Duration.ofMinutes(5), batchSize));
    }

    private void document(long id, String status, boolean expired) {
        jdbc.update("INSERT INTO sa_document(id,project_id,knowledge_base_id,created_by,title,status,user_id,file_name,file_type,file_size,storage_key,file_url) "
                + "VALUES (?,20,10,1,'test','ACTIVE',1,'test.txt','txt',10,'test-key','http://spring/internal/files/test')", id);
        String hash = "FAILED".equals(status) ? null : "a".repeat(64);
        String error = "FAILED".equals(status) ? "previous processing failure" : null;
        int progress = "READY".equals(status) ? 100 : 0;
        jdbc.update("INSERT INTO sa_document_version(id,document_id,project_id,knowledge_base_id,version_no,file_name,file_type,file_size,content_hash,storage_key,process_status,publish_status,progress,chunk_count,current_task_id,error_message) "
                        + "VALUES (?,?,20,10,1,'test.txt','txt',10,?,'test-key',?,'DRAFT',?,0,?,?)",
                id, id, hash, status, progress, "task-" + id, error);
        if (expired) jdbc.update("UPDATE sa_document_version SET update_time=TIMESTAMPADD(HOUR,-1,CURRENT_TIMESTAMP) WHERE id=?", id);
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
