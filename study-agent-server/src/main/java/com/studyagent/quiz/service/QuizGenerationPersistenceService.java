package com.studyagent.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.ai.client.AiClient;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.quiz.dto.GenerateQuizRequest;
import com.studyagent.quiz.entity.QuizEntity;
import com.studyagent.quiz.entity.QuizQuestionEntity;
import com.studyagent.quiz.mapper.QuizMapper;
import com.studyagent.quiz.mapper.QuizQuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizGenerationPersistenceService {
    private final QuizMapper quizMapper;
    private final QuizQuestionMapper questionMapper;
    private final ObjectMapper objectMapper;

    public QuizGenerationPersistenceService(QuizMapper quizMapper, QuizQuestionMapper questionMapper,
                                            ObjectMapper objectMapper) {
        this.quizMapper = quizMapper;
        this.questionMapper = questionMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QuizEntity create(Long userId, GenerateQuizRequest request) {
        QuizEntity entity = new QuizEntity();
        entity.setUserId(userId);
        entity.setKnowledgeBaseId(request.knowledgeBaseId());
        entity.setTitle(request.title());
        entity.setTopic(request.topic());
        entity.setDifficulty(request.difficulty());
        entity.setQuestionCount(request.questionCount());
        entity.setTypesJson(writeJson(request.types()));
        entity.setStatus("PROCESSING");
        quizMapper.insert(entity);
        return entity;
    }

    @Transactional
    public void complete(Long userId, Long quizId, AiClient.QuizGenerateResponse response) {
        QuizEntity quiz = quizMapper.findByIdAndUserId(quizId, userId);
        if (quiz == null || !"PROCESSING".equals(quiz.getStatus())) return;
        if (response.questions() == null || response.questions().size() != quiz.getQuestionCount()) {
            throw new BusinessException(ErrorCode.STRUCTURED_OUTPUT_INVALID, "生成题目数量不匹配");
        }
        int index = 0;
        for (AiClient.GeneratedQuestion source : response.questions()) {
            QuizQuestionEntity question = new QuizQuestionEntity();
            question.setQuizId(quizId);
            question.setTopic(source.topic());
            question.setType(source.type());
            question.setContent(source.content());
            question.setOptionsJson(source.options() == null ? null : writeJson(source.options()));
            question.setCorrectAnswerJson(writeJson(source.answer()));
            question.setExplanation(source.explanation());
            question.setSortOrder(index++);
            questionMapper.insert(question);
        }
        quizMapper.markReady(quizId, userId);
    }

    @Transactional
    public void fail(Long userId, Long quizId, String message) {
        quizMapper.markFailed(quizId, userId, truncate(message));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.STRUCTURED_OUTPUT_INVALID);
        }
    }

    private String truncate(String value) {
        String message = value == null || value.isBlank() ? ErrorCode.QUIZ_GENERATION_FAILED.message() : value;
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
