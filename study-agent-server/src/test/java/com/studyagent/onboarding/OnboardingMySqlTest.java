package com.studyagent.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.assessment.mapper.AssessmentMapper;
import com.studyagent.common.exception.*;
import com.studyagent.knowledge.mapper.*;
import com.studyagent.onboarding.dto.*;
import com.studyagent.onboarding.mapper.OnboardingMapper;
import com.studyagent.onboarding.service.*;
import com.studyagent.project.mapper.*;
import com.studyagent.project.service.ProjectAccessService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Runs all migrations and real mapper/transaction checks only in a disposable database. */
@EnabledIfEnvironmentVariable(named="TEST_MYSQL_URL", matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OnboardingMySqlTest {
    private final String schema="studyagent_onboarding_test_"+UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate admin,jdbc;
    private DriverManagerDataSource dataSource;
    private OnboardingMapper mapper;
    private ProjectMapper projects;
    private AuditLogMapper audit;
    private KnowledgeSnapshotMapper snapshots;
    private KnowledgeBaseMapper bases;
    private AssessmentMapper assessments;
    private OnboardingPathService paths;
    private AssignmentService assignments;
    private boolean schemaCreated;

    @BeforeAll void migrateAndSeed() throws Exception {
        String url=System.getenv("TEST_MYSQL_URL"), user=System.getenv("TEST_MYSQL_USERNAME"), password=System.getenv("TEST_MYSQL_PASSWORD");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        admin.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");schemaCreated=true;
        var source=URI.create(url.substring(5));
        String isolated="jdbc:mysql://"+source.getRawAuthority()+"/"+schema+(source.getRawQuery()==null?"":"?"+source.getRawQuery());
        dataSource=new DriverManagerDataSource(isolated,user,password);jdbc=new JdbcTemplate(dataSource);
        var flyway=Flyway.configure().dataSource(dataSource).load();flyway.migrate();flyway.validate();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(dataSource);
        var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        mapper=session.getMapper(OnboardingMapper.class);projects=session.getMapper(ProjectMapper.class);
        audit=session.getMapper(AuditLogMapper.class);snapshots=session.getMapper(KnowledgeSnapshotMapper.class);bases=session.getMapper(KnowledgeBaseMapper.class);
        assessments=session.getMapper(AssessmentMapper.class);
        paths=pathService(audit);assignments=assignmentService(audit);
        for(int id=1;id<=5;id++) jdbc.update("INSERT INTO sa_user(id,username,password_hash,nickname) VALUES (?,?,'test-hash',?)",id,"onboarding"+id,"User "+id);
        jdbc.update("INSERT INTO sa_project(id,name,code,created_by) VALUES (11,'Orders','orders',1),(12,'Other','other',5)");
        jdbc.update("INSERT INTO sa_project_member(project_id,user_id,role) VALUES (11,1,'OWNER'),(11,2,'LEARNER'),(11,3,'LEARNER'),(11,4,'MAINTAINER'),(12,5,'OWNER')");
        jdbc.update("INSERT INTO sa_knowledge_base(id,project_id,created_by,name) VALUES (21,11,1,'Orders'),(22,12,5,'Other')");
        jdbc.update("INSERT INTO sa_document(id,project_id,knowledge_base_id,title,created_by,file_size) VALUES (31,11,21,'Guide',1,100),(32,12,22,'Other',5,100)");
        jdbc.update("""
            INSERT INTO sa_document_version(id,document_id,project_id,knowledge_base_id,version_no,file_name,file_type,file_size,content_hash,storage_key,process_status,publish_status,progress)
            VALUES (41,31,11,21,1,'guide.md','md',100,REPEAT('a',64),'test/guide','READY','PUBLISHED',100),
                   (42,32,12,22,1,'other.md','md',100,REPEAT('b',64),'test/other','READY','PUBLISHED',100)
            """);
        jdbc.update("INSERT INTO sa_knowledge_snapshot(id,project_id,knowledge_base_id,snapshot_no,reason,created_by) VALUES (51,11,21,1,'DOCUMENT_PUBLISH',1),(52,12,22,1,'DOCUMENT_PUBLISH',5)");
        jdbc.update("INSERT INTO sa_knowledge_snapshot_item(snapshot_id,document_id,document_version_id) VALUES (51,31,41),(52,32,42)");
    }
    @BeforeEach void resetOnboardingFixtures() {
        jdbc.update("DELETE FROM sa_audit_log");jdbc.update("DELETE FROM sa_onboarding_assignment");
        jdbc.update("DELETE FROM sa_content_dependency");jdbc.update("DELETE FROM sa_onboarding_path");
        jdbc.update("UPDATE sa_project SET status='ACTIVE'");jdbc.update("UPDATE sa_knowledge_base SET status='ACTIVE'");
        jdbc.update("UPDATE sa_project_member SET status='ACTIVE',role=CASE user_id WHEN 1 THEN 'OWNER' WHEN 4 THEN 'MAINTAINER' WHEN 5 THEN 'OWNER' ELSE 'LEARNER' END");
    }
    @AfterAll void dropGeneratedSchema() {
        if(!schemaCreated)return;
        if(!schema.matches("studyagent_onboarding_test_[a-f0-9]{32}"))throw new IllegalStateException("Unsafe test schema");
        admin.execute("DROP DATABASE `"+schema+"`");
    }
    @Test void migrationCreatesOnlyPhaseThreeTablesAndEnforcesConstraints() {
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='8'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'sa_onboarding_%'")).isEqualTo(5);
        long path=create(),task=checkpoint(path,"First",true,List.of());
        assertThatThrownBy(()->jdbc.update("INSERT INTO sa_onboarding_task_dependency(path_id,task_id,prerequisite_task_id) VALUES (?,?,?)",path,task,task)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void assignmentCopiesResourcesAndRemapsDependenciesPerLearnerAndBatch() throws Exception {
        long path=create(),read=read(path),end=checkpoint(path,"Confirm",true,List.of(read));paths.publish(1L,path);
        var ids=assignments.assign(1L,path,new OnboardingRequests.Assign(List.of(2L,3L),null)).assignmentIds();
        for(int i=0;i<ids.size();i++) {
            var detail=assignments.get(1L,ids.get(i));var tasks=detail.tasks();
            assertThat(detail.assignment().pathVersion()).isEqualTo(1);assertThat(detail.assignment().knowledgeSnapshotId()).isEqualTo(51L);
            assertThat(tasks).hasSize(2);assertThat(tasks.get(0).resourceId()).isEqualTo(41L);
            assertThat(tasks.get(1).prerequisiteTaskIds()).containsExactly(tasks.get(0).id());
            assertThat(tasks.get(1).status()).isEqualTo("LOCKED");assertThat(tasks.get(1).sourcePathTaskId()).isEqualTo(end);
        }
        // Simulate a future template edit: execution facts must not read through the template.
        jdbc.update("UPDATE sa_onboarding_path_task SET title='Changed',resource_id=NULL,resource_type='NONE',type='CHECKPOINT',required=0 WHERE id=?",read);
        jdbc.update("DELETE FROM sa_onboarding_task_dependency WHERE path_id=?",path);
        jdbc.update("UPDATE sa_onboarding_path SET version_no=2 WHERE id=?",path);
        var frozen=assignments.get(2L,ids.get(0));assertThat(frozen.assignment().pathVersion()).isEqualTo(1);
        assertThat(frozen.tasks().get(0).title()).isEqualTo("Read guide");assertThat(frozen.tasks().get(0).resourceId()).isEqualTo(41L);
        assertThat(frozen.tasks().get(0).required()).isTrue();assertThat(frozen.tasks().get(1).prerequisiteTaskIds()).hasSize(1);
        var next=assignments.assign(1L,path,new OnboardingRequests.Assign(List.of(2L),null));
        assertThat(next.assignmentIds().get(0)).isNotIn(ids);assertThat(count("SELECT COUNT(*) FROM sa_onboarding_assignment")).isEqualTo(3);
    }
    @Test void dependenciesUnlockAndCompletionAndSubmissionAreIdempotentWithoutAutomaticPass() throws Exception {
        long path=create(),first=read(path);checkpoint(path,"Confirm",true,List.of(first));paths.publish(1L,path);
        long id=assign(path);var tasks=assignments.get(2L,id).tasks();long firstId=tasks.get(0).id(),lastId=tasks.get(1).id();
        expect(ErrorCode.ONBOARDING_PREREQUISITE_NOT_MET,()->assignments.complete(2L,lastId,new OnboardingRequests.Complete("too soon")));
        assignments.start(2L,id);assignments.start(2L,id);
        expect(ErrorCode.ONBOARDING_ASSIGNMENT_INCOMPLETE,()->assignments.submit(2L,id));
        assignments.complete(2L,firstId,new OnboardingRequests.Complete("original"));
        var done=assignments.get(2L,id).tasks().get(0);assertThat(assignments.get(2L,id).tasks().get(1).status()).isEqualTo("PENDING");
        assignments.complete(2L,firstId,new OnboardingRequests.Complete("replacement"));
        assertThat(assignments.get(2L,id).tasks().get(0).completedAt()).isEqualTo(done.completedAt());
        assertThat(assignments.get(2L,id).tasks().get(0).completionNote()).isEqualTo("original");
        assignments.complete(2L,lastId,new OnboardingRequests.Complete(null));
        assertThat(assignments.get(2L,id).assignment().progress()).isEqualByComparingTo("100");
        assertThat(assignments.get(2L,id).assignment().status()).isEqualTo("IN_PROGRESS");
        assignments.submit(2L,id);assignments.submit(2L,id);
        assertThat(assignments.get(2L,id).assignment().status()).isEqualTo("REVIEW_PENDING");
        assertThat(count("SELECT COUNT(*) FROM sa_audit_log WHERE action='ONBOARDING_ASSIGNMENT_START'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM sa_audit_log WHERE action='ONBOARDING_TASK_COMPLETE'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM sa_audit_log WHERE action='ONBOARDING_ASSIGNMENT_SUBMIT'")).isEqualTo(1);
    }
    @Test void learnerCannotEscapeOwnAssignmentsOrProjectAndRemovedMembersCannotWrite() {
        long path=create();checkpoint(path,"Check",true,List.of());paths.publish(1L,path);
        var ids=assignments.assign(1L,path,new OnboardingRequests.Assign(List.of(2L,3L),null)).assignmentIds();
        var query=new OnboardingQueries.Assignments(3L,null,null,null,1,10);
        assertThat(assignments.list(2L,11L,query).rows()).extracting(a->a.assigneeUserId()).containsExactly(2L);
        expect(ErrorCode.FORBIDDEN,()->assignments.get(3L,ids.get(0)));
        expect(ErrorCode.FORBIDDEN,()->assignments.start(1L,ids.get(0)));
        expect(ErrorCode.PROJECT_NOT_MEMBER,()->paths.get(5L,path));
        expect(ErrorCode.PROJECT_ROLE_INSUFFICIENT,()->paths.create(2L,11L,metadata(51L)));
        expect(ErrorCode.FORBIDDEN,()->paths.create(1L,11L,metadata(52L)));
        jdbc.update("UPDATE sa_project_member SET status='REMOVED' WHERE project_id=11 AND user_id=2");
        expect(ErrorCode.PROJECT_NOT_MEMBER,()->assignments.start(2L,ids.get(0)));
        assertThat(assignments.get(1L,ids.get(0)).assignment().status()).isEqualTo("ASSIGNED");
    }
    @Test void invalidAssignmentBatchAndAuditFailureRollBackAllEffects() {
        long path=create();checkpoint(path,"Check",true,List.of());paths.publish(1L,path);
        expect(ErrorCode.ONBOARDING_PATH_INVALID,()->assignments.assign(1L,path,new OnboardingRequests.Assign(List.of(2L,5L),null)));
        expect(ErrorCode.ONBOARDING_PATH_INVALID,()->assignments.assign(1L,path,new OnboardingRequests.Assign(List.of(2L,2L),null)));
        assertThat(count("SELECT COUNT(*) FROM sa_onboarding_assignment")).isZero();
        var broken=mock(AuditLogMapper.class);when(broken.insert(any())).thenReturn(0);
        expect(ErrorCode.INTERNAL_ERROR,()->assignmentService(broken).assign(1L,path,new OnboardingRequests.Assign(List.of(2L,3L),null)));
        assertThat(count("SELECT COUNT(*) FROM sa_onboarding_assignment")).isZero();
        assertThat(count("SELECT COUNT(*) FROM sa_onboarding_assignment_task")).isZero();
    }
    @Test void contentReviewBlocksPublicationAndResourceChangeRegistersNewDependency() {
        long path=create(),task=read(path);
        jdbc.update("UPDATE sa_content_dependency SET review_status='NEEDS_REVIEW' WHERE target_id=?",task);
        expect(ErrorCode.ONBOARDING_PATH_INVALID,()->paths.publish(1L,path));
        assertThat(mapper.findPath(path).getStatus()).isEqualTo("DRAFT");
        paths.updateTask(1L,task,new OnboardingRequests.Task("READ","Retitle",null,"DOCUMENT_VERSION",41L,"MANUAL",20,true,1,List.of()));
        assertThat(count("SELECT COUNT(*) FROM sa_content_dependency WHERE review_status='NEEDS_REVIEW'")).isEqualTo(1);
        paths.updateTask(1L,task,new OnboardingRequests.Task("CHECKPOINT","Confirm",null,"NONE",null,"MANUAL",0,true,1,List.of()));
        assertThat(count("SELECT COUNT(*) FROM sa_content_dependency")).isZero();paths.publish(1L,path);
    }
    @Test void pathEditsRejectCyclesPartialReorderAndPublishedMutation() {
        long path=create(),a=checkpoint(path,"A",true,List.of()),b=checkpoint(path,"B",true,List.of(a));
        expect(ErrorCode.ONBOARDING_PATH_INVALID,()->paths.updateTask(1L,a,new OnboardingRequests.Task("CHECKPOINT","A",null,"NONE",null,"MANUAL",0,true,1,List.of(b))));
        expect(ErrorCode.ONBOARDING_PATH_INVALID,()->paths.order(1L,path,new OnboardingRequests.Order(List.of(a))));
        paths.order(1L,path,new OnboardingRequests.Order(List.of(b,a)));
        assertThat(paths.get(1L,path).tasks()).extracting(t->t.id()).containsExactly(b,a);
        paths.publish(1L,path);expect(ErrorCode.ONBOARDING_PATH_INVALID,()->paths.update(1L,path,metadata(51L)));
    }
    @Test void assessmentTasksCannotBePublishedWithoutVerifiedResourcesOrCompletedManually() {
        for(String type:List.of("QUIZ","DRILL")) {
            long path=create();paths.addTask(1L,path,new OnboardingRequests.Task(type,type,null,type,999L,"AUTO",20,true,1,List.of()));
            expect(ErrorCode.ONBOARDING_PATH_INVALID,()->paths.publish(1L,path));
        }
        long path=create();checkpoint(path,"Check",true,List.of());paths.publish(1L,path);long id=assign(path);
        long task=assignments.get(2L,id).tasks().get(0).id();
        for(String type:List.of("QUIZ","DRILL")) {
            jdbc.update("UPDATE sa_onboarding_assignment_task SET type=?,resource_type=?,resource_id=999,completion_rule='AUTO' WHERE id=?",type,type,task);
            expect(ErrorCode.ONBOARDING_PATH_INVALID,()->assignments.complete(2L,task,new OnboardingRequests.Complete(null)));
        }
    }
    @Test void terminalAssignmentAndArchivedProjectRejectNewWrites() {
        long path=create();checkpoint(path,"Check",true,List.of());paths.publish(1L,path);long id=assign(path),task=assignments.get(2L,id).tasks().get(0).id();
        for(String status:List.of("REVIEW_PENDING","PASSED","NEEDS_WORK","CANCELLED")) {
            jdbc.update("UPDATE sa_onboarding_assignment SET status=? WHERE id=?",status,id);
            expect(ErrorCode.ONBOARDING_PATH_INVALID,()->assignments.complete(2L,task,new OnboardingRequests.Complete(null)));
            expect(ErrorCode.ONBOARDING_PATH_INVALID,()->assignments.start(2L,id));
        }
        jdbc.update("UPDATE sa_project SET status='ARCHIVED' WHERE id=11");
        expect(ErrorCode.PROJECT_ARCHIVED,()->assignments.assign(1L,path,new OnboardingRequests.Assign(List.of(2L),null)));
    }
    @Test void simultaneousPublishStartCompleteAndSubmitApplyEachTransitionOnce() throws Exception {
        long path=create();checkpoint(path,"Check",true,List.of());
        race(()->paths.publish(1L,path));assertThat(mapper.findPath(path).getVersionNo()).isEqualTo(1);
        long id=assign(path);race(()->assignments.start(2L,id));long task=assignments.get(2L,id).tasks().get(0).id();
        race(()->assignments.complete(2L,task,new OnboardingRequests.Complete("done")));
        race(()->assignments.submit(2L,id));
        for(String action:List.of("ONBOARDING_PATH_PUBLISH","ONBOARDING_ASSIGNMENT_START","ONBOARDING_TASK_COMPLETE","ONBOARDING_ASSIGNMENT_SUBMIT"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sa_audit_log WHERE action=?",Long.class,action)).isEqualTo(1);
        assertThat(assignments.get(2L,id).assignment().status()).isEqualTo("REVIEW_PENDING");
    }
    @Test void optionalTasksDoNotBlockSubmissionButCannotBeCompletedAfterSubmission() {
        long path=create();checkpoint(path,"Required",true,List.of());checkpoint(path,"Optional",false,List.of());paths.publish(1L,path);
        long id=assign(path);var tasks=assignments.get(2L,id).tasks();
        assignments.complete(2L,tasks.get(0).id(),new OnboardingRequests.Complete(null));assignments.submit(2L,id);
        assertThat(assignments.get(2L,id).assignment().progress()).isEqualByComparingTo("50");
        expect(ErrorCode.ONBOARDING_PATH_INVALID,()->assignments.complete(2L,tasks.get(1).id(),new OnboardingRequests.Complete(null)));
    }
    private long create(){return paths.create(1L,11L,metadata(51L)).id();}
    private OnboardingRequests.Path metadata(long snapshot){return new OnboardingRequests.Path("Orders takeover","MAINTAINER","Scope",snapshot);}
    private long checkpoint(long path,String title,boolean required,List<Long> pre){return paths.addTask(1L,path,new OnboardingRequests.Task("CHECKPOINT",title,null,"NONE",null,"MANUAL",0,required,2,pre)).id();}
    private long read(long path){return paths.addTask(1L,path,new OnboardingRequests.Task("READ","Read guide","Guide details","DOCUMENT_VERSION",41L,"MANUAL",30,true,1,List.of())).id();}
    private long assign(long path){return assignments.assign(1L,path,new OnboardingRequests.Assign(List.of(2L),null)).assignmentIds().get(0);}
    private long count(String sql){return jdbc.queryForObject(sql,Long.class);}
    private OnboardingSupport support(AuditLogMapper events){return new OnboardingSupport(projects,new ProjectAccessService(projects),events);}
    private OnboardingPathService pathService(AuditLogMapper events){return transactional(new OnboardingPathService(mapper,snapshots,support(events),bases,assessments));}
    private AssignmentService assignmentService(AuditLogMapper events){return transactional(new AssignmentService(mapper,paths,support(events),new ObjectMapper()));}
    @SuppressWarnings("unchecked") private <T>T transactional(T target){
        var advice=new TransactionInterceptor();advice.setTransactionManager(new DataSourceTransactionManager(dataSource));
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(advice);return (T)proxy.getProxy();
    }
    private void expect(ErrorCode code,org.assertj.core.api.ThrowableAssert.ThrowingCallable action){
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.errorCode()).isEqualTo(code));
    }
    private void race(Runnable action)throws Exception {
        var barrier=new CyclicBarrier(2);var executor=Executors.newFixedThreadPool(2);
        try{Callable<Void> call=()->{barrier.await(10,TimeUnit.SECONDS);action.run();return null;};
            var first=executor.submit(call);var second=executor.submit(call);first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);
        }finally{executor.shutdownNow();}
    }
}
