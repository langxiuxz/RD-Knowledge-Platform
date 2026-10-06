package com.studyagent.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.ai.client.AiClient;
import com.studyagent.common.exception.GlobalExceptionHandler;
import com.studyagent.knowledge.service.KnowledgeBaseService;
import com.studyagent.learning.mapper.LearningRecordMapper;
import com.studyagent.learning.mapper.TopicProgressMapper;
import com.studyagent.quiz.controller.QuizController;
import com.studyagent.quiz.entity.QuizAnswerEntity;
import com.studyagent.quiz.entity.QuizEntity;
import com.studyagent.quiz.entity.QuizQuestionEntity;
import com.studyagent.quiz.mapper.QuizAnswerMapper;
import com.studyagent.quiz.mapper.QuizMapper;
import com.studyagent.quiz.mapper.QuizQuestionMapper;
import com.studyagent.quiz.service.*;
import com.studyagent.security.UserPrincipal;
import com.studyagent.wrong.mapper.WrongQuestionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the real Jackson 3 HTTP boundary, not only direct service calls. */
@ExtendWith(MockitoExtension.class)
class QuizControllerTest {
    @Mock QuizMapper quizMapper;
    @Mock QuizQuestionMapper questionMapper;
    @Mock QuizAnswerMapper answerMapper;
    @Mock QuizGenerationPersistenceService generationPersistence;
    @Mock QuizGenerationWorker generationWorker;
    @Mock KnowledgeBaseService knowledgeBaseService;
    @Mock AiClient aiClient;
    @Mock WrongQuestionMapper wrongMapper;
    @Mock TopicProgressMapper progressMapper;
    @Mock LearningRecordMapper learningRecordMapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var persistence = new QuizSubmissionPersistenceService(quizMapper, answerMapper, wrongMapper,
                progressMapper, learningRecordMapper);
        var service = new QuizService(quizMapper, questionMapper, answerMapper, generationPersistence,
                generationWorker, persistence, knowledgeBaseService, aiClient, new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(new QuizController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
        var principal = new UserPrincipal(1L, "learner");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    void submit_withJsonStringsAndArrays_shouldPersistWrongAnswersAndReturnResult() throws Exception {
        QuizEntity quiz = readyQuiz();
        var questions = List.of(question(11L, "SINGLE", "\"A\""),
                question(12L, "MULTIPLE", "[\"A\",\"C\"]"),
                question(13L, "JUDGE", "\"true\""));
        when(questionMapper.findByQuizIdAndUserId(50L, 1L)).thenReturn(questions);
        List<QuizAnswerEntity> saved = new ArrayList<>();
        when(answerMapper.insert(any())).thenAnswer(call -> { saved.add(call.getArgument(0)); return 1; });
        when(quizMapper.markCompleted(eq(50L), eq(1L), any(), eq(2), eq(1), anyString()))
                .thenAnswer(call -> { quiz.setStatus("COMPLETED"); quiz.setScore(call.getArgument(2)); return 1; });

        mvc.perform(post("/api/quizzes/50/submit").contentType(MediaType.APPLICATION_JSON).content("""
                {"answers":[{"questionId":11,"answer":"B"},
                  {"questionId":12,"answer":["C","A"]},{"questionId":13,"answer":"true"}]}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.correctCount").value(2))
                .andExpect(jsonPath("$.data.wrongCount").value(1))
                .andExpect(jsonPath("$.data.score").value(66.67));
        verify(wrongMapper).upsertWrong(1L, 11L, "Spring");
        verify(progressMapper).recordQuizAnswer(1L, "Spring", 0, 1);
        verify(learningRecordMapper).insert(any());

        when(answerMapper.findByQuizIdAndUserId(50L, 1L)).thenReturn(saved);
        mvc.perform(get("/api/quizzes/50/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].userAnswer").value("B"))
                .andExpect(jsonPath("$.data.questions[0].correctAnswer").value("A"))
                .andExpect(jsonPath("$.data.questions[0].correct").value(false))
                .andExpect(jsonPath("$.data.questions[1].userAnswer[0]").value("C"))
                .andExpect(jsonPath("$.data.questions[1].correct").value(true));
    }

    @Test
    void submit_withNullAnswer_shouldReturnValidationErrorWithoutWrites() throws Exception {
        mvc.perform(post("/api/quizzes/50/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":11,\"answer\":null}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(quizMapper, answerMapper, wrongMapper);
    }

    @Test
    void submit_withoutAuthentication_shouldReturn401() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/quizzes/50/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":11,\"answer\":\"A\"}]}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(1004));
        verifyNoInteractions(quizMapper, answerMapper);
    }

    @Test
    void submit_withForeignOrMissingQuiz_shouldReturn404WithoutWrites() throws Exception {
        mvc.perform(post("/api/quizzes/50/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":11,\"answer\":\"A\"}]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(4001));
        verify(quizMapper).findByIdAndUserId(50L, 1L);
        verifyNoInteractions(answerMapper, wrongMapper);
    }

    @Test
    void submit_whenAlreadyCompleted_shouldReturn409WithoutWrites() throws Exception {
        readyQuiz().setStatus("COMPLETED");
        mvc.perform(post("/api/quizzes/50/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":11,\"answer\":\"A\"}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(4002));
        verifyNoInteractions(questionMapper, answerMapper, wrongMapper);
    }

    @Test
    void submit_withMalformedJson_shouldReturn400WithoutWrites() throws Exception {
        mvc.perform(post("/api/quizzes/50/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(quizMapper, answerMapper);
    }

    @Test
    void submit_withDuplicateQuestionIds_shouldReturn400WithoutWrites() throws Exception {
        readyQuiz();
        when(questionMapper.findByQuizIdAndUserId(50L, 1L))
                .thenReturn(List.of(question(11L, "SINGLE", "\"A\"")));
        mvc.perform(post("/api/quizzes/50/submit").contentType(MediaType.APPLICATION_JSON).content("""
                {"answers":[{"questionId":11,"answer":"A"},{"questionId":11,"answer":"B"}]}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(answerMapper, wrongMapper, aiClient);
    }

    @Test
    void submit_withMissingQuestion_shouldReturn400WithoutWrites() throws Exception {
        readyQuiz();
        when(questionMapper.findByQuizIdAndUserId(50L, 1L))
                .thenReturn(List.of(question(11L, "SINGLE", "\"A\""), question(12L, "JUDGE", "\"true\"")));
        mvc.perform(post("/api/quizzes/50/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":11,\"answer\":\"A\"}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(answerMapper, wrongMapper, aiClient);
    }

    @Test
    void submit_withInvalidMultipleAnswer_shouldReturn400WithoutWrites() throws Exception {
        readyQuiz();
        when(questionMapper.findByQuizIdAndUserId(50L, 1L))
                .thenReturn(List.of(question(11L, "MULTIPLE", "[\"A\",\"C\"]")));
        mvc.perform(post("/api/quizzes/50/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":11,\"answer\":\"A\"}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(answerMapper, wrongMapper, aiClient);
    }

    @Test
    void submit_whenShortGradingFails_shouldReturn503WithoutPartialWrites() throws Exception {
        QuizEntity quiz = readyQuiz();
        when(questionMapper.findByQuizIdAndUserId(50L, 1L))
                .thenReturn(List.of(question(11L, "SINGLE", "\"A\""),
                        question(12L, "SHORT", "\"Objects managed by container\"")));
        when(aiClient.gradeShort(any())).thenThrow(new IllegalStateException("AI timeout"));
        mvc.perform(post("/api/quizzes/50/submit").contentType(MediaType.APPLICATION_JSON).content("""
                {"answers":[{"questionId":11,"answer":"B"},
                  {"questionId":12,"answer":"Objects managed by container"}]}
                """))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(3001));
        org.assertj.core.api.Assertions.assertThat(quiz.getStatus()).isEqualTo("READY");
        verifyNoInteractions(answerMapper, wrongMapper, progressMapper, learningRecordMapper);
        verify(quizMapper, never()).markCompleted(any(), any(), any(), anyInt(), anyInt(), any());
    }

    private QuizEntity readyQuiz() {
        QuizEntity quiz = new QuizEntity();
        quiz.setId(50L); quiz.setUserId(1L); quiz.setStatus("READY");
        quiz.setTitle("Spring Quiz"); quiz.setTopic("Spring");
        when(quizMapper.findByIdAndUserId(50L, 1L)).thenReturn(quiz);
        return quiz;
    }

    private QuizQuestionEntity question(Long id, String type, String correctAnswer) {
        QuizQuestionEntity question = new QuizQuestionEntity();
        question.setId(id); question.setType(type); question.setContent("Question " + id);
        question.setTopic("Spring"); question.setCorrectAnswerJson(correctAnswer);
        question.setExplanation("Explanation " + id);
        return question;
    }
}
