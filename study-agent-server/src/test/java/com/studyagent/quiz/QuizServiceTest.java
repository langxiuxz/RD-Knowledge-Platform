package com.studyagent.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.ai.client.AiClient;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.knowledge.service.KnowledgeBaseService;
import com.studyagent.quiz.dto.SubmitQuizRequest;
import com.studyagent.quiz.entity.QuizEntity;
import com.studyagent.quiz.entity.QuizQuestionEntity;
import com.studyagent.quiz.mapper.QuizAnswerMapper;
import com.studyagent.quiz.mapper.QuizMapper;
import com.studyagent.quiz.mapper.QuizQuestionMapper;
import com.studyagent.quiz.service.QuizGenerationPersistenceService;
import com.studyagent.quiz.service.QuizGenerationWorker;
import com.studyagent.quiz.service.QuizService;
import com.studyagent.quiz.service.QuizSubmissionPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {
    @Mock QuizMapper quizMapper;
    @Mock QuizQuestionMapper questionMapper;
    @Mock QuizAnswerMapper answerMapper;
    @Mock QuizGenerationPersistenceService generationPersistence;
    @Mock QuizGenerationWorker generationWorker;
    @Mock QuizSubmissionPersistenceService submissionPersistence;
    @Mock KnowledgeBaseService knowledgeBaseService;
    @Mock AiClient aiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private QuizService service;

    @BeforeEach
    void setUp() {
        service = new QuizService(quizMapper, questionMapper, answerMapper, generationPersistence,
                generationWorker, submissionPersistence, knowledgeBaseService, aiClient, objectMapper);
    }

    @Test
    void submit_shouldDeterministicallyGradeSingleMultipleAndJudge() {
        QuizEntity quiz = readyQuiz();
        List<QuizQuestionEntity> questions = List.of(
                question(11L, "SINGLE", "\"A\""),
                question(12L, "MULTIPLE", "[\"A\",\"C\"]"),
                question(13L, "JUDGE", "\"true\"")
        );
        when(quizMapper.findByIdAndUserId(50L, 1L)).thenReturn(quiz);
        when(questionMapper.findByQuizIdAndUserId(50L, 1L)).thenReturn(questions);
        when(submissionPersistence.commit(eq(1L), eq(50L), anyList()))
                .thenReturn(new QuizSubmissionPersistenceService.SubmissionResult(
                        BigDecimal.valueOf(100), 3, 0, "ok"));

        service.submit(1L, 50L, new SubmitQuizRequest(List.of(
                answer(11L, objectMapper.valueToTree("a")),
                answer(12L, objectMapper.valueToTree(List.of("C", "A"))),
                answer(13L, objectMapper.valueToTree(true))
        )));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuizSubmissionPersistenceService.GradedAnswer>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(submissionPersistence).commit(eq(1L), eq(50L), captor.capture());
        assertThat(captor.getValue()).allMatch(QuizSubmissionPersistenceService.GradedAnswer::correct);
    }

    @Test
    void submit_whenShortGradingFails_shouldNotPersistAnything() {
        when(quizMapper.findByIdAndUserId(50L, 1L)).thenReturn(readyQuiz());
        when(questionMapper.findByQuizIdAndUserId(50L, 1L))
                .thenReturn(List.of(question(14L, "SHORT", "\"IoC reference\"")));
        when(aiClient.gradeShort(any())).thenThrow(new IllegalStateException("LLM unavailable"));

        assertThatThrownBy(() -> service.submit(1L, 50L,
                new SubmitQuizRequest(List.of(answer(14L, objectMapper.valueToTree("my answer"))))))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE);
        verifyNoInteractions(submissionPersistence);
    }

    @Test
    void detail_whenQuizBelongsToAnotherUser_shouldReject() {
        when(quizMapper.findByIdAndUserId(50L, 1L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(1L, 50L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
        verify(questionMapper, never()).findByQuizIdAndUserId(anyLong(), anyLong());
    }

    private QuizEntity readyQuiz() {
        QuizEntity quiz = new QuizEntity();
        quiz.setId(50L);
        quiz.setUserId(1L);
        quiz.setStatus("READY");
        return quiz;
    }

    private QuizQuestionEntity question(Long id, String type, String correctAnswerJson) {
        QuizQuestionEntity question = new QuizQuestionEntity();
        question.setId(id);
        question.setType(type);
        question.setContent("question " + id);
        question.setCorrectAnswerJson(correctAnswerJson);
        return question;
    }

    private SubmitQuizRequest.Answer answer(Long id, JsonNode answer) {
        return new SubmitQuizRequest.Answer(id, answer);
    }
}
