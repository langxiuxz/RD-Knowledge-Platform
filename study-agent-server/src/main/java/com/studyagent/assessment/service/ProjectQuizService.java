package com.studyagent.assessment.service;

import com.studyagent.assessment.dto.*;
import com.studyagent.assessment.entity.*;
import com.studyagent.assessment.mapper.AssessmentMapper;
import com.studyagent.assessment.vo.AssessmentVOs;
import com.studyagent.common.result.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static com.studyagent.assessment.service.AssessmentSupport.*;

@Service
public class ProjectQuizService {
    private final AssessmentMapper mapper;
    private final AssessmentSupport support;
    private final AssessmentViews views;
    private final ProjectQuizGenerationWorker worker;
    public ProjectQuizService(AssessmentMapper mapper,AssessmentSupport support,AssessmentViews views,ProjectQuizGenerationWorker worker) {
        this.mapper=mapper;this.support=support;this.views=views;this.worker=worker;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AssessmentVOs.Generated generate(Long userId,Long projectId,AssessmentRequests.Generate request) {
        support.lock(userId,projectId,true);
        support.snapshot(projectId,request.knowledgeSnapshotId(),true);
        if(support.versions(projectId,request.knowledgeSnapshotId()).isEmpty()) throw invalid("不能基于空知识快照生成题目");
        if(new HashSet<>(request.types()).size()!=request.types().size()) throw invalid("题型不能重复");
        var quiz=new ProjectQuizEntity();quiz.setProjectId(projectId);quiz.setKnowledgeSnapshotId(request.knowledgeSnapshotId());
        quiz.setTitle(request.title().strip());quiz.setTopic(request.topic().strip());quiz.setQuestionCount(request.questionCount());
        quiz.setDifficulty(request.difficulty());quiz.setTypesJson(support.write(request.types()));quiz.setPassScore(request.passScore());
        quiz.setVersionNo(1);quiz.setRubricVersion(1);quiz.setRubricJson("{}");quiz.setCreatedBy(userId);quiz.setStatus("GENERATING");
        quiz.setCurrentTaskId(UUID.randomUUID().toString());mapper.insertQuiz(quiz);
        support.audit(projectId,userId,"QUIZ_GENERATE","QUIZ",quiz.getId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { worker.dispatch(quiz); }
        });
        return new AssessmentVOs.Generated(quiz.getId(),"GENERATING");
    }
    public PageResult<AssessmentVOs.Quiz> list(Long userId,Long projectId,AssessmentQuery query) {
        boolean manager=support.manager(userId,projectId);
        String status=manager?query.status():"PUBLISHED";
        return new PageResult<>(mapper.countQuizzes(projectId,query.keyword(),status),mapper.quizzes(projectId,query.keyword(),status,
            (query.page().longValue()-1)*query.pageSize(),query.pageSize()).stream().map(views::quiz).toList());
    }
    public boolean isProjectQuiz(Long id) { return mapper.quiz(id)!=null; }
    public AssessmentVOs.QuizDetail detail(Long userId,Long id) {
        var quiz=find(id);boolean manager=support.manager(userId,quiz.getProjectId());
        if(!manager && !"PUBLISHED".equals(quiz.getStatus())) throw missing();
        return new AssessmentVOs.QuizDetail(views.quiz(quiz),manager?mapper.questions(id).stream().map(q->views.question(q,true)).toList():List.of());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void updateQuestion(Long userId,Long id,AssessmentRequests.Question request) {
        var original=mapper.question(id);if(original==null) throw missing();
        var quiz=find(original.getQuizId());support.lock(userId,quiz.getProjectId(),true);quiz=mapper.lockQuiz(quiz.getId());
        if(!Set.of("DRAFT","READY").contains(quiz.getStatus())) throw conflict("只有未发布题目可以修改");
        support.snapshot(quiz.getProjectId(),quiz.getKnowledgeSnapshotId(),true);
        var q=mapper.question(id); q.setKnowledgeKey(request.knowledgeKey());q.setContent(request.content());q.setOptionsJson(support.write(request.options()));
        q.setCorrectAnswerJson(support.write(request.answer()));q.setExplanation(request.explanation());q.setMaxScore(request.maxScore());q.setSourceRefsJson(support.write(request.sourceRefs()));
        QuizScoring.validate(q,request.options(),request.answer());support.validateSources(quiz.getProjectId(),quiz.getKnowledgeSnapshotId(),request.sourceRefs());
        if(mapper.updateQuestion(q)!=1) throw conflict("题目状态已变化");
        depend(quiz,q);support.audit(quiz.getProjectId(),userId,"QUIZ_QUESTION_UPDATE","QUIZ",quiz.getId());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void publish(Long userId,Long id) {
        var quiz=find(id);support.lock(userId,quiz.getProjectId(),true);quiz=mapper.lockQuiz(id);
        if("PUBLISHED".equals(quiz.getStatus())) return;
        if(!Set.of("DRAFT","READY").contains(quiz.getStatus())) throw conflict("测验尚未生成完成");
        support.snapshot(quiz.getProjectId(),quiz.getKnowledgeSnapshotId(),true);
        var questions=mapper.questions(id);
        if(questions.size()!=quiz.getQuestionCount()) throw invalid("题量不完整");
        for(var q:questions) {
            QuizScoring.validate(q,support.read(q.getOptionsJson()),support.read(q.getCorrectAnswerJson()));
            support.validateSources(quiz.getProjectId(),quiz.getKnowledgeSnapshotId(),support.read(q.getSourceRefsJson()));depend(quiz,q);
        }
        support.requireCurrent("QUIZ",id);if(mapper.publishQuiz(id)!=1) throw conflict("测验状态已变化");
        support.audit(quiz.getProjectId(),userId,"QUIZ_PUBLISH","QUIZ",id);
    }
    private void depend(ProjectQuizEntity quiz,AssessmentQuestionEntity question) {
        List<Long> ids=new ArrayList<>();support.read(question.getSourceRefsJson()).forEach(r->ids.add(r.get("documentVersionId").longValue()));
        support.depend(quiz.getProjectId(),"QUIZ",quiz.getId(),ids);
    }
    ProjectQuizEntity find(Long id) { var q=mapper.quiz(id);if(q==null) throw missing();return q; }
}
