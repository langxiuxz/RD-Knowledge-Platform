package com.studyagent.assessment.service;

import com.studyagent.assessment.dto.AssessmentRequests.Review;
import com.studyagent.assessment.entity.AssessmentReviewEntity;
import com.studyagent.assessment.mapper.AssessmentMapper;
import com.studyagent.assessment.vo.ReviewVO;
import com.studyagent.onboarding.mapper.OnboardingMapper;
import com.studyagent.onboarding.service.AssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

import static com.studyagent.assessment.service.AssessmentSupport.*;

@Service
public class AssessmentReviewService {
    private final AssessmentMapper mapper;
    private final OnboardingMapper onboarding;
    private final AssessmentSupport support;
    private final AssignmentService assignments;
    public AssessmentReviewService(AssessmentMapper mapper, OnboardingMapper onboarding,
                                   AssessmentSupport support, AssignmentService assignments) {
        this.mapper=mapper; this.onboarding=onboarding; this.support=support; this.assignments=assignments;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReviewVO quiz(Long userId, Long id, Review request) {
        var initial=mapper.attempt(id); if(initial==null) throw missing();
        support.lock(userId,initial.getProjectId(),true);
        var attempt=mapper.reviewAttemptForUpdate(id);
        var prior=existing("QUIZ_ATTEMPT",id,request);
        if(prior!=null) return prior;
        if(!Set.of("REVIEW_PENDING","COMPLETED").contains(attempt.getStatus())) throw conflict("测验尚未提交，不能复核");
        var quiz=mapper.quizForAttempt(id);
        BigDecimal finalScore=request.scoreOverride()==null?attempt.getAutoScore():request.scoreOverride();
        if(mapper.reviewAttempt(id,finalScore)!=1) throw conflict("测验状态已变化");
        if(attempt.getAssignmentTaskId()!=null)
            assignments.completeFromAssessment(attempt.getUserId(),attempt.getAssignmentTaskId(),"QUIZ",attempt.getQuizId(),
                id,attempt.getProjectId(),quiz.getKnowledgeSnapshotId(),"PASS".equals(request.decision()));
        return insert(userId,attempt.getProjectId(),"QUIZ_ATTEMPT",id,request);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReviewVO drill(Long userId, Long id, Review request) {
        var initial=mapper.run(id); if(initial==null) throw missing();
        support.lock(userId,initial.getProjectId(),true);
        var run=mapper.reviewRunForUpdate(id);
        var prior=existing("DRILL_RUN",id,request);
        if(prior!=null) return prior;
        if(!"REVIEW_PENDING".equals(run.getStatus())) throw conflict("演练尚未提交或已完成复核");
        var scenario=mapper.scenarioForRun(id);
        BigDecimal finalScore=request.scoreOverride()==null?run.getProgramScore():request.scoreOverride();
        if(mapper.reviewRun(id,finalScore)!=1) throw conflict("演练状态已变化");
        if(run.getAssignmentTaskId()!=null)
            assignments.completeFromAssessment(run.getUserId(),run.getAssignmentTaskId(),"DRILL",run.getScenarioId(),
                id,run.getProjectId(),scenario.getKnowledgeSnapshotId(),"PASS".equals(request.decision()));
        return insert(userId,run.getProjectId(),"DRILL_RUN",id,request);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReviewVO assignment(Long userId, Long id, Review request) {
        if(request.scoreOverride()!=null) throw invalid("Assignment 复核只记录决定，不设置分数");
        var initial=onboarding.findAssignment(id); if(initial==null) throw missing();
        support.lock(userId,initial.getProjectId(),true);
        onboarding.lockAssignment(id);
        var prior=existing("ASSIGNMENT",id,request);
        if(prior!=null) return prior;
        assignments.review(userId,id,request.decision());
        return insert(userId,initial.getProjectId(),"ASSIGNMENT",id,request);
    }

    private ReviewVO existing(String type, Long id, Review request) {
        var prior=mapper.latestReview(type,id);
        if(prior==null) return null;
        boolean scoreSame=prior.getScoreOverride()==null?request.scoreOverride()==null:
            request.scoreOverride()!=null && prior.getScoreOverride().compareTo(request.scoreOverride())==0;
        if(!prior.getDecision().equals(request.decision()) || !scoreSame || !Objects.equals(prior.getComment(),request.comment()))
            throw conflict("该结果已复核；冲突请求不能覆盖历史复核");
        return ReviewVO.from(prior);
    }
    private ReviewVO insert(Long userId, Long projectId, String type, Long id, Review request) {
        var row=new AssessmentReviewEntity(); row.setProjectId(projectId);row.setReviewerId(userId);row.setTargetType(type);
        row.setTargetId(id);row.setDecision(request.decision());row.setScoreOverride(request.scoreOverride());row.setComment(request.comment());
        if(mapper.insertReview(row)!=1) throw conflict("复核未保存");
        support.audit(projectId,userId,"ASSESSMENT_REVIEW","ASSESSMENT_REVIEW",row.getId());
        return ReviewVO.from(mapper.latestReview(type,id));
    }
}
