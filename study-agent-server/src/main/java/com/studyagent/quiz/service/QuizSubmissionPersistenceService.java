package com.studyagent.quiz.service;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.learning.entity.LearningRecordEntity;
import com.studyagent.learning.mapper.LearningRecordMapper;
import com.studyagent.learning.mapper.TopicProgressMapper;
import com.studyagent.quiz.entity.QuizAnswerEntity;
import com.studyagent.quiz.entity.QuizEntity;
import com.studyagent.quiz.entity.QuizQuestionEntity;
import com.studyagent.quiz.mapper.QuizAnswerMapper;
import com.studyagent.quiz.mapper.QuizMapper;
import com.studyagent.wrong.mapper.WrongQuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class QuizSubmissionPersistenceService {
    private static final int MAX_TOPIC_LENGTH = 128;

    private final QuizMapper quizMapper;
    private final QuizAnswerMapper answerMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final TopicProgressMapper progressMapper;
    private final LearningRecordMapper learningRecordMapper;

    public QuizSubmissionPersistenceService(QuizMapper quizMapper, QuizAnswerMapper answerMapper,
                                            WrongQuestionMapper wrongQuestionMapper,
                                            TopicProgressMapper progressMapper,
                                            LearningRecordMapper learningRecordMapper) {
        this.quizMapper = quizMapper;
        this.answerMapper = answerMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.progressMapper = progressMapper;
        this.learningRecordMapper = learningRecordMapper;
    }

    @Transactional
    public SubmissionResult commit(Long userId, Long quizId, List<GradedAnswer> gradedAnswers) {
        QuizEntity quiz = quizMapper.findByIdAndUserId(quizId, userId);
        if (quiz == null) throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);
        if ("COMPLETED".equals(quiz.getStatus())) throw new BusinessException(ErrorCode.QUIZ_ALREADY_SUBMITTED);
        if (!"READY".equals(quiz.getStatus())) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "测验尚未就绪");
        int correctCount = 0;
        for (GradedAnswer graded : gradedAnswers) {
            QuizAnswerEntity answer = new QuizAnswerEntity();
            answer.setQuizId(quizId);
            answer.setQuestionId(graded.question().getId());
            answer.setUserId(userId);
            answer.setUserAnswerJson(graded.userAnswerJson());
            answer.setIsCorrect(graded.correct());
            answerMapper.insert(answer);
            String topic = topicOf(graded.question(), quiz);
            if (graded.correct()) correctCount++;
            else wrongQuestionMapper.upsertWrong(userId, graded.question().getId(), topic);
            progressMapper.recordQuizAnswer(userId, topic, graded.correct() ? 1 : 0, graded.correct() ? 0 : 1);
        }
        int wrongCount = gradedAnswers.size() - correctCount;
        BigDecimal score = BigDecimal.valueOf(correctCount).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(gradedAnswers.size()), 2, RoundingMode.HALF_UP);
        String analysis = "答对 " + correctCount + " 道，答错 " + wrongCount + " 道。";
        if (quizMapper.markCompleted(quizId, userId, score, correctCount, wrongCount, analysis) == 0) {
            throw new BusinessException(ErrorCode.QUIZ_ALREADY_SUBMITTED);
        }
        LearningRecordEntity record = new LearningRecordEntity();
        record.setUserId(userId);
        record.setType("QUIZ");
        record.setTopic(normalizeTopic(quiz.getTopic(), "综合测验"));
        record.setDurationMinutes(0);
        record.setSourceId(quizId);
        record.setRemark("完成测验：" + quiz.getTitle());
        learningRecordMapper.insert(record);
        return new SubmissionResult(score, correctCount, wrongCount, analysis);
    }

    private String topicOf(QuizQuestionEntity question, QuizEntity quiz) {
        if (question.getTopic() != null && !question.getTopic().isBlank()) {
            return normalizeTopic(question.getTopic(), "综合知识");
        }
        return normalizeTopic(quiz.getTopic(), "综合知识");
    }

    private String normalizeTopic(String topic, String fallback) {
        String value = topic == null || topic.isBlank() ? fallback : topic;
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= MAX_TOPIC_LENGTH) return value;
        return value.substring(0, value.offsetByCodePoints(0, MAX_TOPIC_LENGTH));
    }

    public record GradedAnswer(QuizQuestionEntity question, String userAnswerJson, boolean correct) {}
    public record SubmissionResult(BigDecimal score, int correctCount, int wrongCount, String analysis) {}
}
