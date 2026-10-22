package com.studyagent.assessment.service;

import com.studyagent.assessment.entity.*;
import com.studyagent.assessment.vo.AssessmentVOs;
import org.springframework.stereotype.Component;

@Component
public class AssessmentViews {
    private final AssessmentSupport support;
    public AssessmentViews(AssessmentSupport support) { this.support=support; }
    public AssessmentVOs.Quiz quiz(ProjectQuizEntity q) {
        return new AssessmentVOs.Quiz(q.getId(),q.getProjectId(),q.getKnowledgeSnapshotId(),q.getTitle(),q.getTopic(),
            q.getDifficulty(),q.getQuestionCount(),q.getStatus(),q.getVersionNo(),q.getRubricVersion(),q.getPassScore(),q.getErrorMessage(),q.getUpdateTime());
    }
    public AssessmentVOs.Question question(AssessmentQuestionEntity q,boolean solutions) {
        return new AssessmentVOs.Question(q.getId(),q.getType(),q.getKnowledgeKey(),q.getContent(),support.read(q.getOptionsJson()),
            solutions?support.read(q.getCorrectAnswerJson()):null,solutions?q.getExplanation():null,q.getMaxScore(),
            solutions?support.read(q.getSourceRefsJson()):null);
    }
    public AssessmentVOs.Answer answer(AssessmentAnswerEntity a) {
        return new AssessmentVOs.Answer(a.getQuestionId(),support.read(a.getUserAnswerJson()),a.getRuleScore(),a.getAiSuggestedScore(),
            a.getFinalScore(),a.getIsCorrect(),a.getScoringSource(),a.getAiFeedback(),support.read(a.getSourceRefsJson()));
    }
    public AssessmentVOs.Evidence evidence(DrillEvidenceEntity e,boolean manager) {
        return new AssessmentVOs.Evidence(e.getCode(),e.getEvidenceType(),e.getTitle(),e.getContent(),manager?e.getUnlockRuleCode():null);
    }
}
