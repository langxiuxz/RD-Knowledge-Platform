package com.studyagent.learning;

import com.studyagent.learning.dto.CreateLearningRecordRequest;
import com.studyagent.learning.mapper.LearningRecordMapper;
import com.studyagent.learning.mapper.TopicProgressMapper;
import com.studyagent.learning.service.LearningService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in real MySQL checks. Every run creates and removes its own isolated schema. */
@EnabledIfEnvironmentVariable(named = "STUDY_AGENT_MYSQL_TEST_URL", matches = ".+")
class FocusMySqlTest {
    @Test
    void migrateAndRetryFocus_shouldKeepLegacyRowsAndCountConcurrentRequestsOnce() throws Exception {
        String adminUrl = System.getenv("STUDY_AGENT_MYSQL_TEST_URL");
        String username = System.getenv("STUDY_AGENT_MYSQL_TEST_USER");
        String password = System.getenv("STUDY_AGENT_MYSQL_TEST_PASSWORD");
        var admin = new JdbcTemplate(new DriverManagerDataSource(adminUrl, username, password));
        String schema = "study_agent_pomodoro_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        try {
            String baseUrl = adminUrl.substring(0, adminUrl.indexOf('/', "jdbc:mysql://".length()));
            String query = adminUrl.contains("?") ? adminUrl.substring(adminUrl.indexOf('?')) : "";
            String url = baseUrl + "/" + schema + query;
            var dataSource = new DriverManagerDataSource(url, username, password);
            var jdbc = new JdbcTemplate(dataSource);
            Flyway.configure().dataSource(dataSource).target("2").load().migrate();
            jdbc.update("INSERT INTO sa_user (id,username,password_hash,nickname) VALUES (1,'test_one','test_hash','Test')");
            jdbc.update("INSERT INTO sa_user (id,username,password_hash,nickname) VALUES (2,'test_two','test_hash','Test')");
            jdbc.update("INSERT INTO sa_learning_record (user_id,type,topic,duration_minutes) VALUES (1,'READ','Legacy',10)");
            Flyway.configure().dataSource(dataSource).load().migrate();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_learning_record WHERE client_record_id IS NULL", Integer.class)).isEqualTo(1);

            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            var config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(config);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/learning/*.xml"));
            var template = new SqlSessionTemplate(factory.getObject());
            var records = template.getMapper(LearningRecordMapper.class);
            var progress = template.getMapper(TopicProgressMapper.class);
            var service = new LearningService(records, progress);
            var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            var request = new CreateLearningRecordRequest("FOCUS", "Java", 50, null, "Read streams",
                    UUID.randomUUID().toString(), OffsetDateTime.now().minusDays(1).withNano(0));

            var barrier = new CyclicBarrier(2);
            var pool = Executors.newFixedThreadPool(2);
            try {
                java.util.concurrent.Callable<Long> submit = () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return tx.execute(status -> service.create(1L, request).id());
                };
                var first = pool.submit(submit);
                var second = pool.submit(submit);
                assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
            } finally { pool.shutdownNow(); }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_learning_record WHERE type='FOCUS'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT study_minutes FROM sa_topic_progress WHERE user_id=1 AND topic='Java'", Integer.class)).isEqualTo(50);
            assertThat(jdbc.queryForObject("SELECT DATE(create_time) < CURRENT_DATE FROM sa_learning_record WHERE type='FOCUS'", Boolean.class)).isTrue();
            // The identical UUID belongs to a different user and must create a separate fact.
            tx.execute(status -> service.create(2L, request));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_learning_record WHERE type='FOCUS'", Integer.class)).isEqualTo(2);

            var failingProgress = mock(TopicProgressMapper.class);
            doThrow(new IllegalStateException("projection failed")).when(failingProgress).addStudyMinutes(anyLong(), anyString(), anyInt());
            var failingService = new LearningService(records, failingProgress);
            var rollback = new CreateLearningRecordRequest("FOCUS", "Rollback", 5, null, null,
                    UUID.randomUUID().toString(), request.completedAt());
            assertThatThrownBy(() -> tx.execute(status -> failingService.create(1L, rollback))).isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_learning_record WHERE topic='Rollback'", Integer.class)).isZero();
        } finally {
            // Only the exact random schema created by this test is removed.
            admin.execute("DROP DATABASE `" + schema + "`");
        }
    }
}
