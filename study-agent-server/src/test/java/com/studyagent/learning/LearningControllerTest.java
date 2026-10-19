package com.studyagent.learning;

import com.studyagent.common.exception.GlobalExceptionHandler;
import com.studyagent.learning.controller.LearningController;
import com.studyagent.learning.entity.LearningRecordEntity;
import com.studyagent.learning.mapper.LearningRecordMapper;
import com.studyagent.learning.mapper.TopicProgressMapper;
import com.studyagent.learning.service.LearningService;
import com.studyagent.security.UserPrincipal;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LearningControllerTest {
    @Mock LearningRecordMapper records;
    @Mock TopicProgressMapper progress;
    MockMvc mvc;
    private static final String BODY = """
            {"type":"FOCUS","topic":"Java","durationMinutes":50,"remark":"Read streams",
             "clientRecordId":"d86efc94-2975-4b88-a756-0b4c35c850a6","completedAt":"2026-10-18T10:50:00+08:00"}
            """;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LearningController(new LearningService(records, progress)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
        var principal = new UserPrincipal(7L, "learner");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    void create_validFocusJson_shouldUseAuthenticatedUser() throws Exception {
        when(records.insert(any())).thenAnswer(call -> {
            LearningRecordEntity record = call.getArgument(0);
            record.setId(100L);
            return 1;
        });
        mvc.perform(post("/api/learning/records").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(100));
        verify(records).insert(argThat(r -> r.getUserId().equals(7L) && r.getType().equals("FOCUS")));
    }

    @Test
    void create_invalidIdDurationTimestampOrType_shouldRejectWithoutWriting() throws Exception {
        for (String body : new String[] { BODY.replace("d86efc94-2975-4b88-a756-0b4c35c850a6", "bad-id"),
                BODY.replace("\"durationMinutes\":50", "\"durationMinutes\":-1"),
                BODY.replace("2026-10-18T10:50:00+08:00", "2026-10-18T10:50:00"),
                BODY.replace("FOCUS", "REST"), BODY.replace("\"topic\":\"Java\"", "\"topic\":\"\"") }) {
            mvc.perform(post("/api/learning/records").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(records, progress);
    }

    @Test
    void create_unauthenticated_shouldReturn401WithoutWriting() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/learning/records").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(records, progress);
    }
}
