package com.studyagent.assessment.service;

import com.studyagent.ai.client.AiClient;
import com.studyagent.assessment.entity.ProjectQuizEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ProjectQuizGenerationWorker {
    private final AiClient ai;
    private final AssessmentSupport support;
    private final ProjectQuizGenerationPersistence persistence;
    private final TaskExecutor executor;
    public ProjectQuizGenerationWorker(AiClient ai,AssessmentSupport support,ProjectQuizGenerationPersistence persistence,
                                       @Qualifier("aiTaskExecutor") TaskExecutor executor) {
        this.ai=ai;this.support=support;this.persistence=persistence;this.executor=executor;
    }
    public void dispatch(ProjectQuizEntity quiz) {
        try { executor.execute(()->generate(quiz)); }
        catch(RuntimeException ex) { persistence.fail(quiz.getId(),quiz.getCurrentTaskId()); }
    }
    private void generate(ProjectQuizEntity q) {
        try {
            var snapshot=support.snapshot(q.getProjectId(),q.getKnowledgeSnapshotId(),false);
            var response=ai.generateQuiz(new AiClient.QuizGenerateRequest(null,q.getTopic(),snapshot.getKnowledgeBaseId(),q.getQuestionCount(),
                q.getDifficulty(),support.strings(q.getTypesJson()),q.getProjectId(),q.getKnowledgeSnapshotId(),support.versions(q.getProjectId(),q.getKnowledgeSnapshotId())));
            persistence.complete(q.getId(),q.getCurrentTaskId(),response);
        } catch(RuntimeException ex) { persistence.fail(q.getId(),q.getCurrentTaskId()); }
    }
}
