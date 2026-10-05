package com.studyagent.quiz;

import com.studyagent.learning.mapper.LearningRecordMapper;
import com.studyagent.learning.mapper.TopicProgressMapper;
import com.studyagent.quiz.entity.QuizEntity;
import com.studyagent.quiz.entity.QuizQuestionEntity;
import com.studyagent.quiz.mapper.QuizAnswerMapper;
import com.studyagent.quiz.mapper.QuizMapper;
import com.studyagent.quiz.service.QuizSubmissionPersistenceService;
import com.studyagent.wrong.mapper.WrongQuestionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizSubmissionPersistenceServiceTest {
    @Mock QuizMapper quizMapper;
    @Mock QuizAnswerMapper answerMapper;
    @Mock WrongQuestionMapper wrongQuestionMapper;
    @Mock TopicProgressMapper progressMapper;
    @Mock LearningRecordMapper learningRecordMapper;

    @Test
    void commit_whenTopicExceedsDerivedColumnLength_shouldBoundTopicTo128CodePoints() {
        QuizSubmissionPersistenceService service = new QuizSubmissionPersistenceService(
                quizMapper, answerMapper, wrongQuestionMapper, progressMapper, learningRecordMapper);
        String longTopic = "😀".repeat(130);
        QuizEntity quiz = new QuizEntity();
        quiz.setId(51L);
        quiz.setStatus("READY");
        quiz.setTitle("Quiz");
        quiz.setTopic(longTopic);
        QuizQuestionEntity question = new QuizQuestionEntity();
        question.setId(12L);
        when(quizMapper.findByIdAndUserId(51L, 1L)).thenReturn(quiz);
        when(quizMapper.markCompleted(any(), any(), any(), any(Integer.class), any(Integer.class), any())).thenReturn(1);

        service.commit(1L, 51L, List.of(
                new QuizSubmissionPersistenceService.GradedAnswer(question, "\"B\"", false)));

        String expectedTopic = "😀".repeat(128);
        verify(wrongQuestionMapper).upsertWrong(1L, 12L, expectedTopic);
        verify(progressMapper).recordQuizAnswer(1L, expectedTopic, 0, 1);
        org.mockito.ArgumentCaptor<com.studyagent.learning.entity.LearningRecordEntity> recordCaptor =
                org.mockito.ArgumentCaptor.forClass(com.studyagent.learning.entity.LearningRecordEntity.class);
        verify(learningRecordMapper).insert(recordCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(recordCaptor.getValue().getTopic()).isEqualTo(expectedTopic);
    }

    @Test
    void commit_whenAnswerIsWrong_shouldUpsertWrongQuestionAndProgress() {
        QuizSubmissionPersistenceService service = new QuizSubmissionPersistenceService(
                quizMapper, answerMapper, wrongQuestionMapper, progressMapper, learningRecordMapper);
        QuizEntity quiz = new QuizEntity();
        quiz.setId(50L);
        quiz.setStatus("READY");
        quiz.setTitle("Quiz");
        quiz.setTopic("Spring AOP");
        QuizQuestionEntity question = new QuizQuestionEntity();
        question.setId(11L);
        question.setTopic("Spring AOP");
        when(quizMapper.findByIdAndUserId(50L, 1L)).thenReturn(quiz);
        when(quizMapper.markCompleted(any(), any(), any(), any(Integer.class), any(Integer.class), any())).thenReturn(1);

        service.commit(1L, 50L, List.of(
                new QuizSubmissionPersistenceService.GradedAnswer(question, "\"B\"", false)));

        verify(wrongQuestionMapper).upsertWrong(1L, 11L, "Spring AOP");
        verify(progressMapper).recordQuizAnswer(1L, "Spring AOP", 0, 1);
        verify(answerMapper).insert(any());
        verify(learningRecordMapper).insert(any());
    }
}
