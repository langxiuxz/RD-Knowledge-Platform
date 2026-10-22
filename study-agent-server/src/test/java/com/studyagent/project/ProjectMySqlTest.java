package com.studyagent.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.auth.mapper.UserMapper;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.project.dto.ProjectQueries;
import com.studyagent.project.dto.ProjectRequests;
import com.studyagent.project.mapper.AuditLogMapper;
import com.studyagent.project.mapper.ProjectMapper;
import com.studyagent.project.service.ProjectAccessService;
import com.studyagent.project.service.ProjectService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Opt-in real MySQL checks. Migrations and writes run only in a generated disposable schema. */
@EnabledIfEnvironmentVariable(named = "TEST_MYSQL_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectMySqlTest {
    private final String schema = "studyagent_project_test_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    private ProjectMapper projects;
    private AuditLogMapper audit;
    private UserMapper users;
    private ProjectService service;
    private boolean schemaCreated;
    private long legacyProjectId;

    @BeforeAll
    void migrateInIsolatedSchema() throws Exception {
        String adminUrl = System.getenv("TEST_MYSQL_URL");
        String username = System.getenv("TEST_MYSQL_USERNAME");
        String password = System.getenv("TEST_MYSQL_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(adminUrl, username, password));
        admin.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        schemaCreated = true;
        URI source = URI.create(adminUrl.substring("jdbc:".length()));
        String url = "jdbc:mysql://" + source.getRawAuthority() + "/" + schema
                + (source.getRawQuery() == null ? "" : "?" + source.getRawQuery());
        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("3").load().migrate();
        for (int id = 1; id <= 5; id++) {
            jdbc.update("INSERT INTO sa_user(id,username,password_hash,nickname) VALUES (?,?,'test_hash',?)",
                    id, "user" + id, "User " + id);
        }
        jdbc.update("INSERT INTO sa_knowledge_base(user_id,name) VALUES (5,'Legacy private knowledge')");
        jdbc.update("""
                INSERT INTO sa_document(knowledge_base_id,user_id,file_name,file_type,mime_type,file_size,
                    storage_key,file_url,status,progress,chunk_count,current_task_id,processed_at)
                VALUES (1,5,'legacy-guide.pdf','pdf','application/pdf',1024,'legacy/guide.pdf',
                    'http://localhost/internal/files/legacy/guide.pdf','READY',100,2,'legacy-task',CURRENT_TIMESTAMP)
                """);
        Flyway.configure().dataSource(dataSource).load().migrate();
        Flyway.configure().dataSource(dataSource).load().validate();
        legacyProjectId = jdbc.queryForObject("SELECT project_id FROM sa_knowledge_base WHERE name='Legacy private knowledge'", Long.class);

        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));
        var session = new SqlSessionTemplate(factory.getObject());
        projects = session.getMapper(ProjectMapper.class);
        audit = session.getMapper(AuditLogMapper.class);
        users = session.getMapper(UserMapper.class);
        service = transactionalService(audit);
    }

    @BeforeEach
    void clearOnlyProjectFixtures() {
        jdbc.update("DELETE FROM sa_audit_log");
        jdbc.update("DELETE FROM sa_project_member WHERE project_id<>?", legacyProjectId);
        jdbc.update("DELETE FROM sa_project WHERE id<>?", legacyProjectId);
    }

    @AfterAll
    void dropOnlyGeneratedSchema() {
        if (!schemaCreated) return;
        if (!schema.matches("studyagent_project_test_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test schema");
        admin.execute("DROP DATABASE `" + schema + "`");
    }

    @Test
    void migrationPreservesLegacyDataAndEnforcesProjectConstraints() {
        assertThat(jdbc.queryForObject("SELECT user_id FROM sa_knowledge_base WHERE name='Legacy private knowledge'", Long.class))
                .isEqualTo(5L);
        var legacyVersion = jdbc.queryForMap("""
                SELECT v.process_status, v.publish_status, v.current_task_id, v.storage_key, v.file_name
                FROM sa_document_version v JOIN sa_document d ON d.id=v.document_id
                WHERE d.knowledge_base_id=(SELECT id FROM sa_knowledge_base WHERE name='Legacy private knowledge')
                """);
        assertThat(legacyVersion).containsEntry("process_status", "FAILED")
                .containsEntry("publish_status", "DRAFT")
                .containsEntry("current_task_id", null)
                .containsEntry("storage_key", "legacy/guide.pdf")
                .containsEntry("file_name", "legacy-guide.pdf");
        assertThat(jdbc.queryForObject("SELECT current_snapshot_id FROM sa_knowledge_base WHERE name='Legacy private knowledge'", Long.class))
                .isNull();
        long id = create(1L, "orders");
        assertThat(service.get(1L, id).myRole()).isEqualTo("OWNER");
        assertThat(service.get(1L, id).memberCount()).isEqualTo(1);
        assertThat(projects.lockMember(id, 1L).getStatus()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_audit_log WHERE action='PROJECT_CREATED' AND actor_user_id=1", Long.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO sa_project_member(project_id,user_id,role) VALUES (?,2,'ADMIN')", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO sa_project_member(project_id,user_id,role) VALUES (?,1,'OWNER')", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE sa_project SET status='DELETED' WHERE id=?", id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void auditFailureRollsBackProjectCreationAndMemberChanges() {
        AuditLogMapper failingAudit = mock(AuditLogMapper.class);
        when(failingAudit.insert(any())).thenThrow(new DataAccessResourceFailureException("test audit failure"));
        ProjectService failing = transactionalService(failingAudit);
        assertThatThrownBy(() -> failing.create(1L, new ProjectRequests.Create("Orders", "orders", null)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_project WHERE id<>?", Long.class, legacyProjectId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_project_member WHERE project_id<>?", Long.class, legacyProjectId)).isZero();
        long id = create(1L, "orders");
        assertThatThrownBy(() -> failing.addMember(1L, id, new ProjectRequests.AddMember("user2", "OWNER")))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(projects.lockMember(id, 2L)).isNull();
        service.addMember(1L, id, new ProjectRequests.AddMember("user2", "OWNER"));
        assertThatThrownBy(() -> failing.changeRole(1L, id, 2L, new ProjectRequests.ChangeRole("LEARNER")))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(service.get(2L, id).myRole()).isEqualTo("OWNER");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_audit_log WHERE action='PROJECT_MEMBER_ROLE_CHANGED'", Long.class))
                .isZero();
    }

    @Test
    void projectIsolationRemovalAndRejoiningUseOneMembershipAndRetainAudit() {
        long id = create(1L, "orders");
        create(4L, "payments");
        service.addMember(1L, id, new ProjectRequests.AddMember("user2", "LEARNER"));
        long membershipId = projects.lockMember(id, 2L).getId();
        assertThat(service.list(2L, new ProjectQueries.Projects(null, null, null, 1, 10)).rows())
                .extracting(row -> row.id()).containsExactly(id);
        expect(ErrorCode.PROJECT_NOT_MEMBER, () -> service.get(4L, id));
        expect(ErrorCode.PROJECT_ROLE_INSUFFICIENT, () -> service.auditLogs(2L, id, auditQuery(null)));
        service.removeMember(1L, id, 2L);
        service.removeMember(1L, id, 2L);
        expect(ErrorCode.PROJECT_NOT_MEMBER, () -> service.members(2L, id, new ProjectQueries.Members(null, null, 1, 10)));
        assertThat(service.list(2L, new ProjectQueries.Projects(null, null, null, 1, 10)).rows()).isEmpty();
        assertThat(projects.lockMember(id, 2L).getRemovedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_audit_log WHERE action='PROJECT_MEMBER_REMOVED'", Long.class))
                .isEqualTo(1);
        service.addMember(1L, id, new ProjectRequests.AddMember("user2", "MAINTAINER"));
        assertThat(projects.lockMember(id, 2L).getId()).isEqualTo(membershipId);
        assertThat(projects.lockMember(id, 2L).getRemovedAt()).isNull();
        assertThat(service.get(2L, id).myRole()).isEqualTo("MAINTAINER");
        assertThat(service.auditLogs(2L, id, auditQuery("PROJECT_MEMBER_ADDED")).total()).isEqualTo(2);
        expect(ErrorCode.PROJECT_ROLE_INSUFFICIENT, () -> service.addMember(2L, id,
                new ProjectRequests.AddMember("user3", "LEARNER")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEMOTE", "REMOVE", "MIXED"})
    void concurrentOwnerChangesAlwaysKeepOneActiveOwner(String operation) throws Exception {
        long id = create(1L, "orders");
        service.addMember(1L, id, new ProjectRequests.AddMember("user2", "OWNER"));
        var results = race(
                () -> ownerChange(id, 1L, "REMOVE".equals(operation)),
                () -> ownerChange(id, 2L, !"DEMOTE".equals(operation)));
        assertThat(results).containsExactlyInAnyOrder("OK", ErrorCode.PROJECT_LAST_OWNER.name());
        assertThat(projects.countActiveOwners(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_audit_log WHERE action IN "
                + "('PROJECT_MEMBER_REMOVED','PROJECT_MEMBER_ROLE_CHANGED')", Long.class)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateMemberRequestsCreateOneMembershipAndOneAudit() throws Exception {
        long id = create(1L, "orders");
        service.addMember(1L, id, new ProjectRequests.AddMember("user2", "OWNER"));
        var request = new ProjectRequests.AddMember("user3", "LEARNER");
        assertThat(race(() -> service.addMember(1L, id, request), () -> service.addMember(2L, id, request)))
                .containsExactlyInAnyOrder("OK", ErrorCode.PROJECT_MEMBER_EXISTS.name());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_project_member WHERE project_id=? AND user_id=3", Long.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_audit_log WHERE action='PROJECT_MEMBER_ADDED' AND resource_id=3", Long.class))
                .isEqualTo(1);
    }

    @Test
    void concurrentProjectCreationHonorsUniqueCodeWithoutOrphanMembers() throws Exception {
        assertThat(race(() -> create(1L, "orders"), () -> create(2L, "orders")))
                .containsExactlyInAnyOrder("OK", ErrorCode.PROJECT_CODE_EXISTS.name());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_project WHERE id<>?", Long.class, legacyProjectId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_project_member WHERE project_id<>?", Long.class, legacyProjectId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_audit_log", Long.class)).isEqualTo(1);
    }

    @Test
    void codeUpdateIsAuditedAndDuplicateCodeRollsBack() {
        long id = create(1L, "orders");
        create(4L, "payments");
        service.update(1L, id, new ProjectRequests.Update("Order Service", "order-service", "Runbooks"));
        var logs = service.auditLogs(1L, id, auditQuery("PROJECT_UPDATED"));
        assertThat(logs.total()).isEqualTo(1);
        assertThat(logs.rows().get(0).detail()).isEqualTo(java.util.Map.of("oldCode", "orders", "code", "order-service", "name", "Order Service"));
        expect(ErrorCode.PROJECT_CODE_EXISTS, () -> service.update(1L, id,
                new ProjectRequests.Update("Changed", "payments", null)));
        assertThat(service.get(1L, id).code()).isEqualTo("order-service");
        assertThat(service.auditLogs(1L, id, auditQuery("PROJECT_UPDATED")).total()).isEqualTo(1);
    }

    @Test
    void archiveIsIdempotentAndKeepsHistoryReadable() {
        long id = create(1L, "orders");
        service.addMember(1L, id, new ProjectRequests.AddMember("user2", "LEARNER"));
        service.archive(1L, id);
        service.archive(1L, id);
        assertThat(service.get(2L, id).status()).isEqualTo("ARCHIVED");
        assertThat(service.members(2L, id, new ProjectQueries.Members(null, null, 1, 10)).total()).isEqualTo(2);
        assertThat(service.auditLogs(1L, id, auditQuery("PROJECT_ARCHIVED")).total()).isEqualTo(1);
        expect(ErrorCode.PROJECT_ARCHIVED, () -> service.removeMember(1L, id, 2L));
        expect(ErrorCode.PROJECT_ARCHIVED, () -> service.update(1L, id, new ProjectRequests.Update("Orders", "orders", null)));
    }

    private long create(long userId, String code) {
        return service.create(userId, new ProjectRequests.Create("Orders", code, null)).id();
    }

    private void ownerChange(long projectId, long actorId, boolean remove) {
        if (remove) service.removeMember(actorId, projectId, actorId);
        else service.changeRole(actorId, projectId, actorId, new ProjectRequests.ChangeRole("LEARNER"));
    }

    private ProjectQueries.Audit auditQuery(String action) {
        return new ProjectQueries.Audit(action, null, null, null, null, 1, 10);
    }

    private List<String> race(Runnable first, Runnable second) throws Exception {
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> one = () -> attempt(barrier, first);
            Callable<String> two = () -> attempt(barrier, second);
            var resultOne = executor.submit(one);
            var resultTwo = executor.submit(two);
            return List.of(resultOne.get(20, TimeUnit.SECONDS), resultTwo.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private String attempt(CyclicBarrier barrier, Runnable action) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            action.run();
            return "OK";
        } catch (BusinessException exception) {
            return exception.errorCode().name();
        }
    }

    private ProjectService transactionalService(AuditLogMapper auditMapper) {
        var target = new ProjectService(projects, auditMapper, users, new ProjectAccessService(projects), new ObjectMapper());
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new DataSourceTransactionManager(dataSource));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(interceptor);
        return (ProjectService) proxy.getProxy();
    }

    private void expect(ErrorCode code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.errorCode()).isEqualTo(code));
    }
}
