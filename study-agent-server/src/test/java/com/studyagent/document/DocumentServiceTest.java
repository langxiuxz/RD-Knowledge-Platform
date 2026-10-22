package com.studyagent.document;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.document.dto.DocumentProgressRequest;
import com.studyagent.document.entity.DocumentVersionEntity;
import com.studyagent.document.mapper.DocumentMapper;
import com.studyagent.document.mapper.DocumentVersionMapper;
import com.studyagent.document.service.DocumentIndexDispatcher;
import com.studyagent.document.service.DocumentService;
import com.studyagent.file.service.FileStorageService;
import com.studyagent.knowledge.mapper.KnowledgeBaseMapper;
import com.studyagent.knowledge.service.KnowledgeBaseService;
import com.studyagent.project.mapper.AuditLogMapper;
import com.studyagent.project.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    @Mock DocumentMapper documents;
    @Mock DocumentVersionMapper versions;
    @Mock KnowledgeBaseService knowledge;
    @Mock KnowledgeBaseMapper bases;
    @Mock FileStorageService storage;
    @Mock DocumentIndexDispatcher dispatcher;
    @Mock ProjectAccessService access;
    @Mock AuditLogMapper audits;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(documents, versions, knowledge, bases, storage, dispatcher, access, audits);
    }

    @Test
    void progress_ignoresStaleTaskAndAcceptsDuplicateCurrentCallback() {
        var version = version("task-current", "EMBEDDING", 60);
        when(versions.findByIdForUpdate(101L)).thenReturn(version);
        when(versions.updateProgress(101L, "task-current", "READY", 100, 3, null, null, true)).thenReturn(1);

        service.progress(101L, new DocumentProgressRequest("task-old", "READY", 100, 3, null));
        service.progress(101L, new DocumentProgressRequest("task-current", "READY", 100, 3, null));
        service.progress(101L, new DocumentProgressRequest("task-current", "READY", 100, 3, null));

        verify(versions, times(3)).findByIdForUpdate(101L);
        verify(versions, times(2)).updateProgress(101L, "task-current", "READY", 100, 3, null, null, true);
    }

    @Test
    void progress_rejectsReadyBeforePreviousStagesComplete() {
        when(versions.findByIdForUpdate(101L)).thenReturn(version("task-current", "UPLOADED", 0));

        assertThatThrownBy(() -> service.progress(101L,
                new DocumentProgressRequest("task-current", "READY", 100, 3, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(versions, never()).updateProgress(anyLong(), anyString(), anyString(), anyInt(), any(), any(), any(), anyBoolean());
    }

    @Test
    void progress_requiresErrorMessageForFailure() {
        when(versions.findByIdForUpdate(101L)).thenReturn(version("task-current", "EMBEDDING", 60));

        assertThatThrownBy(() -> service.progress(101L,
                new DocumentProgressRequest("task-current", "FAILED", 0, 3, " ")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(versions, never()).updateProgress(anyLong(), anyString(), anyString(), anyInt(), any(), any(), any(), anyBoolean());
    }

    @Test
    void progress_doesNotChangeTerminalVersion() {
        when(versions.findByIdForUpdate(101L)).thenReturn(version("task-current", "READY", 100));

        service.progress(101L, new DocumentProgressRequest("task-current", "FAILED", 0, 3, "late failure"));

        verify(versions, never()).updateProgress(anyLong(), anyString(), anyString(), anyInt(), any(), any(), any(), anyBoolean());
    }

    private DocumentVersionEntity version(String taskId, String status, int progress) {
        var entity = new DocumentVersionEntity();
        entity.setId(101L);
        entity.setCurrentTaskId(taskId);
        entity.setProcessStatus(status);
        entity.setProgress(progress);
        return entity;
    }
}
