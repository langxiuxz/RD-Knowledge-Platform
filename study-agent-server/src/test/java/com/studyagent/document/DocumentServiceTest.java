package com.studyagent.document;

import com.studyagent.ai.client.AiClient;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.config.AppProperties;
import com.studyagent.document.dto.DocumentProgressRequest;
import com.studyagent.document.entity.DocumentEntity;
import com.studyagent.document.mapper.DocumentMapper;
import com.studyagent.document.service.DocumentService;
import com.studyagent.file.service.FileStorageService;
import com.studyagent.knowledge.service.KnowledgeBaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    @Mock DocumentMapper mapper;
    @Mock KnowledgeBaseService knowledgeBaseService;
    @Mock FileStorageService fileStorageService;
    @Mock AiClient aiClient;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("phase3-test-secret-with-at-least-32-characters", Duration.ofMinutes(5), Duration.ofDays(1)),
                new AppProperties.Ai("http://localhost:8000", Duration.ofSeconds(1), Duration.ofSeconds(5)),
                new AppProperties.Storage("./data/files", "http://localhost:8080/internal/files"),
                new AppProperties.Internal("phase3-internal-token"));
        service = new DocumentService(mapper, knowledgeBaseService, fileStorageService, aiClient, properties);
    }

    @Test
    void progress_shouldIgnoreOldTaskAndAcceptDuplicateCurrentCallback() {
        DocumentEntity entity = document("task-current", "EMBEDDING", 60);
        when(mapper.findByIdForUpdate(101L)).thenReturn(entity);
        when(mapper.updateProgressIfCurrentTask(101L, "task-current", "READY", 100, 3, null, true)).thenReturn(1);

        service.progress(101L, new DocumentProgressRequest("task-old", "READY", 100, 3, null));
        service.progress(101L, new DocumentProgressRequest("task-current", "READY", 100, 3, null));
        service.progress(101L, new DocumentProgressRequest("task-current", "READY", 100, 3, null));

        verify(mapper, times(3)).findByIdForUpdate(101L);
        verify(mapper, times(2)).updateProgressIfCurrentTask(101L, "task-current", "READY", 100, 3, null, true);
    }

    @Test
    void progress_shouldRejectProgressRegression() {
        DocumentEntity entity = document("task-current", "EMBEDDING", 60);
        when(mapper.findByIdForUpdate(101L)).thenReturn(entity);

        assertThatThrownBy(() -> service.progress(101L,
                new DocumentProgressRequest("task-current", "EMBEDDING", 30, 2, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(mapper, never()).updateProgressIfCurrentTask(anyLong(), anyString(), anyString(), anyInt(), any(), any(), anyBoolean());
    }

    @Test
    void upload_whenPdfContentIsSpoofed_shouldRejectBeforeStorage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.pdf", "application/pdf", "not a pdf".getBytes());

        assertThatThrownBy(() -> service.upload(1L, 10L, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.FILE_INVALID);
        verify(fileStorageService, never()).store(any());
    }

    @Test
    void upload_whenTextContainsBinaryNullBytes_shouldRejectBeforeStorage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", new byte[]{'a', 0, 'b'});

        assertThatThrownBy(() -> service.upload(1L, 10L, file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.FILE_INVALID);
        verify(fileStorageService, never()).store(any());
    }

    @Test
    void progress_whenAlreadyTerminal_shouldIgnoreLateResultAndDuplicateFailure() {
        for (String terminal : new String[]{"READY", "FAILED"}) {
            when(mapper.findByIdForUpdate(101L)).thenReturn(document("task-current", terminal, 60));
            service.progress(101L, new DocumentProgressRequest("task-current", "READY", 100, 3, null));
            service.progress(101L, new DocumentProgressRequest("task-current", "FAILED", 0, null, "late failure"));
        }
        verify(mapper, never()).updateProgressIfCurrentTask(anyLong(), anyString(), anyString(), anyInt(), any(), any(), anyBoolean());
    }

    @Test
    void reprocess_whenConcurrentRequestAlreadyClaimedTask_shouldNotDispatchAgain() {
        when(mapper.findByIdAndUserId(101L, 1L)).thenReturn(document("old", "FAILED", 0));
        when(mapper.updateTaskStart(any())).thenReturn(0);
        assertThatThrownBy(() -> service.reprocess(1L, 101L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(aiClient);
    }

    private DocumentEntity document(String taskId, String status, int progress) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(101L);
        entity.setCurrentTaskId(taskId);
        entity.setStatus(status);
        entity.setProgress(progress);
        return entity;
    }
}
