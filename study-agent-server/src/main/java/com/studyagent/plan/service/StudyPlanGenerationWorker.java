package com.studyagent.plan.service;

import com.studyagent.ai.client.AiClient;
import com.studyagent.internal.entity.ToolDataRow;
import com.studyagent.internal.mapper.ToolDataMapper;
import com.studyagent.plan.dto.GenerateStudyPlanRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class StudyPlanGenerationWorker {
    private final AiClient aiClient;
    private final ToolDataMapper toolDataMapper;
    private final StudyPlanGenerationPersistenceService persistenceService;

    public StudyPlanGenerationWorker(AiClient aiClient, ToolDataMapper toolDataMapper,
                                     StudyPlanGenerationPersistenceService persistenceService) {
        this.aiClient = aiClient;
        this.toolDataMapper = toolDataMapper;
        this.persistenceService = persistenceService;
    }

    @Async("aiTaskExecutor")
    public void generate(Long userId, Long planId, GenerateStudyPlanRequest request) {
        try {
            Map<String, Object> profile = buildProfile(userId, request.focusTopics());
            AiClient.PlanGenerateResponse response = aiClient.generatePlan(new AiClient.PlanGenerateRequest(
                    userId, request.goal(), request.startDate().toString(), request.endDate().toString(),
                    request.dailyMinutes(), profile));
            persistenceService.complete(userId, planId, response);
        } catch (Exception exception) {
            persistenceService.fail(userId, planId, exception.getMessage());
        }
    }

    private Map<String, Object> buildProfile(Long userId, List<String> focusTopics) {
        LinkedHashSet<String> weakTopics = new LinkedHashSet<>();
        if (focusTopics != null) weakTopics.addAll(focusTopics);
        toolDataMapper.findWeakTopics(userId, 10).stream().map(ToolDataRow::getTopic).forEach(weakTopics::add);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("weakTopics", new ArrayList<>(weakTopics));
        profile.put("learningProgress", toolDataMapper.findLearningProgress(userId, 20).stream()
                .map(this::progressMap).toList());
        profile.put("recentQuizResults", toolDataMapper.findRecentQuizResults(userId, 10).stream()
                .map(this::quizMap).toList());
        return profile;
    }

    private Map<String, Object> progressMap(ToolDataRow row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("topic", row.getTopic());
        value.put("mastery", row.getMastery());
        value.put("quizAccuracy", row.getQuizAccuracy());
        value.put("studyMinutes", row.getStudyMinutes());
        value.put("wrongCount", row.getWrongCount());
        return value;
    }

    private Map<String, Object> quizMap(ToolDataRow row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("quizId", row.getId());
        value.put("title", row.getTitle());
        value.put("topic", row.getTopic());
        value.put("difficulty", row.getDifficulty());
        value.put("score", row.getScore());
        value.put("correctCount", row.getCorrectCount());
        value.put("wrongCount", row.getWrongCount());
        return value;
    }
}
