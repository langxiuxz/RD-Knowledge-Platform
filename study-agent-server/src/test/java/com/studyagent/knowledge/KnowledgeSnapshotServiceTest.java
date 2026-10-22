package com.studyagent.knowledge;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.document.entity.DocumentEntity;
import com.studyagent.document.entity.DocumentVersionEntity;
import com.studyagent.document.mapper.DocumentMapper;
import com.studyagent.document.mapper.DocumentVersionMapper;
import com.studyagent.knowledge.entity.KnowledgeBaseEntity;
import com.studyagent.knowledge.entity.KnowledgeSnapshotEntity;
import com.studyagent.knowledge.mapper.KnowledgeBaseMapper;
import com.studyagent.knowledge.mapper.KnowledgeSnapshotMapper;
import com.studyagent.knowledge.service.KnowledgeBaseService;
import com.studyagent.knowledge.service.KnowledgeSnapshotService;
import com.studyagent.knowledge.vo.KnowledgeSnapshotVOs.Item;
import com.studyagent.project.entity.AuditLogEntity;
import com.studyagent.project.mapper.AuditLogMapper;
import com.studyagent.project.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeSnapshotServiceTest {
    @Mock KnowledgeBaseService knowledge;
    @Mock KnowledgeBaseMapper bases;
    @Mock KnowledgeSnapshotMapper snapshots;
    @Mock DocumentMapper documents;
    @Mock DocumentVersionMapper versions;
    @Mock ProjectAccessService access;
    @Mock AuditLogMapper audits;
    private KnowledgeSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeSnapshotService(knowledge, bases, snapshots, documents, versions, access, audits);
    }

    @Test
    void scope_usesOnlyVersionsInRequestedSnapshot() {
        when(snapshots.find(501L)).thenReturn(snapshot(501L));
        when(snapshots.findVersionIds(501L, 20L, 10L)).thenReturn(List.of(1001L));

        var scope = service.scope(1L, 20L, 10L, 501L);

        assertThat(scope.allowedDocumentVersionIds()).containsExactly(1001L);
        assertThat(scope.knowledgeSnapshotId()).isEqualTo(501L);
        verify(access, atLeastOnce()).requireMember(1L, 20L);
        verifyNoInteractions(versions, documents);
    }

    @Test
    void scope_rejectsAnotherProjectOrKnowledgeBase() {
        when(snapshots.find(501L)).thenReturn(snapshot(501L));
        assertThatThrownBy(() -> service.scope(1L, 21L, 10L, 501L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.KNOWLEDGE_SNAPSHOT_NOT_FOUND);
        assertThatThrownBy(() -> service.scope(1L, 20L, 11L, 501L))
                .isInstanceOf(BusinessException.class);
        verify(snapshots, never()).findVersionIds(anyLong(), anyLong(), anyLong());
    }

    @Test
    void scope_rejectsRemovedProjectMemberBeforeReadingSnapshot() {
        when(access.requireMember(1L, 20L)).thenThrow(new BusinessException(ErrorCode.PROJECT_NOT_MEMBER));
        assertThatThrownBy(() -> service.scope(1L, 20L, 10L, 501L))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(snapshots);
    }

    @Test
    void publish_readyDraftIsNotPublished() {
        publicationFixture("DRAFT");
        assertThatThrownBy(() -> service.publish(1L, 1001L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DOCUMENT_STATE_CONFLICT);
        verify(versions, never()).publish(anyLong());
        verifyNoInteractions(snapshots, audits);
    }

    @Test
    void publish_learnerCannotPublish() {
        when(versions.findById(1001L)).thenReturn(version("APPROVED"));
        when(knowledge.requireEditor(2L, 10L)).thenThrow(new BusinessException(ErrorCode.PROJECT_NOT_MEMBER));
        assertThatThrownBy(() -> service.publish(2L, 1001L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(bases, documents, snapshots, audits);
    }

    @Test
    void publish_createsImmutableSnapshotAndAuditAfterApproval() {
        publicationFixture("APPROVED");
        when(versions.publish(1001L)).thenReturn(1);
        when(documents.publish(101L, 1001L)).thenReturn(1);
        when(bases.currentSnapshotNo(10L)).thenReturn(3);
        when(snapshots.insert(any())).thenAnswer(invocation -> {
            KnowledgeSnapshotEntity snapshot = invocation.getArgument(0);
            snapshot.setId(501L);
            return 1;
        });
        var items = List.of(new Item(101L, 1001L, 1, "Runbook"));
        when(snapshots.currentPublishedItems(10L)).thenReturn(items);
        when(snapshots.insertItems(501L, items)).thenReturn(1);
        when(bases.updateSnapshot(10L, 501L)).thenReturn(1);
        when(audits.insert(any())).thenReturn(1);

        var result = service.publish(1L, 1001L);

        assertThat(result.snapshotId()).isEqualTo(501L);
        assertThat(result.snapshotNo()).isEqualTo(4);
        assertThat(result.publishStatus()).isEqualTo("PUBLISHED");
        var order = inOrder(bases, documents, versions);
        order.verify(bases).lockById(10L);
        order.verify(documents).findByIdForUpdate(101L);
        order.verify(versions).findByIdForUpdate(1001L);
        var audit = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(audits).insert(audit.capture());
        assertThat(audit.getValue().getActorUserId()).isEqualTo(1L);
        assertThat(audit.getValue().getProjectId()).isEqualTo(20L);
        verify(snapshots).insertItems(501L, items);
    }

    @Test
    void publish_duplicateRequestCannotCreateAnotherSnapshot() {
        publicationFixture("PUBLISHED");
        assertThatThrownBy(() -> service.publish(1L, 1001L)).isInstanceOf(BusinessException.class);
        verify(versions, never()).publish(anyLong());
        verifyNoInteractions(snapshots, audits);
    }

    private void publicationFixture(String publishStatus) {
        var v = version(publishStatus);
        when(versions.findById(1001L)).thenReturn(v);
        when(versions.findByIdForUpdate(1001L)).thenReturn(v);
        var kb = new KnowledgeBaseEntity();
        kb.setId(10L); kb.setProjectId(20L);
        when(bases.lockById(10L)).thenReturn(kb);
        var document = new DocumentEntity();
        document.setId(101L); document.setProjectId(20L); document.setKnowledgeBaseId(10L);
        document.setStatus("ACTIVE");
        when(documents.findByIdForUpdate(101L)).thenReturn(document);
    }

    private DocumentVersionEntity version(String publishStatus) {
        var v = new DocumentVersionEntity();
        v.setId(1001L); v.setDocumentId(101L); v.setProjectId(20L); v.setKnowledgeBaseId(10L);
        v.setProcessStatus("READY"); v.setPublishStatus(publishStatus);
        return v;
    }

    private KnowledgeSnapshotEntity snapshot(Long id) {
        var s = new KnowledgeSnapshotEntity();
        s.setId(id); s.setProjectId(20L); s.setKnowledgeBaseId(10L);
        return s;
    }
}
