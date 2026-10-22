package com.studyagent.knowledge.service;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.common.result.PageResult;
import com.studyagent.document.entity.DocumentVersionEntity;
import com.studyagent.document.mapper.DocumentMapper;
import com.studyagent.document.mapper.DocumentVersionMapper;
import com.studyagent.knowledge.entity.KnowledgeSnapshotEntity;
import com.studyagent.knowledge.mapper.KnowledgeBaseMapper;
import com.studyagent.knowledge.mapper.KnowledgeSnapshotMapper;
import com.studyagent.knowledge.vo.KnowledgeSnapshotVOs.*;
import com.studyagent.project.entity.AuditLogEntity;
import com.studyagent.project.mapper.AuditLogMapper;
import com.studyagent.project.service.ProjectAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
public class KnowledgeSnapshotService {
    private final KnowledgeBaseService knowledge;
    private final KnowledgeBaseMapper bases;
    private final KnowledgeSnapshotMapper snapshots;
    private final DocumentMapper documents;
    private final DocumentVersionMapper versions;
    private final ProjectAccessService access;
    private final AuditLogMapper audits;

    public KnowledgeSnapshotService(KnowledgeBaseService knowledge, KnowledgeBaseMapper bases,
            KnowledgeSnapshotMapper snapshots, DocumentMapper documents, DocumentVersionMapper versions,
            ProjectAccessService access, AuditLogMapper audits) {
        this.knowledge = knowledge; this.bases = bases; this.snapshots = snapshots;
        this.documents = documents; this.versions = versions; this.access = access; this.audits = audits;
    }

    public PageResult<Summary> list(Long userId, Long knowledgeBaseId, int page, int pageSize) {
        knowledge.requireMember(userId, knowledgeBaseId);
        long total = snapshots.countByKnowledgeBase(knowledgeBaseId);
        var rows = snapshots.findPageByKnowledgeBase(knowledgeBaseId, (long) (page - 1) * pageSize, pageSize);
        return new PageResult<>(total, rows.stream().map(s ->
                new Summary(s.getId(), s.getSnapshotNo(), s.getItemCount(), s.getCreatedBy(), s.getCreateTime(), s.getReason())).toList());
    }

    public Detail get(Long userId, Long id) {
        var s = requireSnapshot(userId, id);
        return new Detail(id, s.getProjectId(), s.getKnowledgeBaseId(), s.getSnapshotNo(), s.getReason(), snapshots.findItems(id));
    }

    public RagScope scope(Long userId, Long projectId, Long knowledgeBaseId, Long snapshotId) {
        access.requireMember(userId, projectId);
        if (snapshotId == null) throw new BusinessException(ErrorCode.KNOWLEDGE_SNAPSHOT_NOT_FOUND);
        var s = requireSnapshot(userId, snapshotId);
        if (!Objects.equals(projectId, s.getProjectId()) || !Objects.equals(knowledgeBaseId, s.getKnowledgeBaseId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_SNAPSHOT_NOT_FOUND);
        }
        return new RagScope(projectId, knowledgeBaseId, snapshotId, snapshots.findVersionIds(snapshotId, projectId, knowledgeBaseId));
    }

    @Transactional
    public Publication publish(Long userId, Long versionId) {
        DocumentVersionEntity initial = versions.findById(versionId);
        if (initial == null) throw new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND);
        knowledge.requireEditor(userId, initial.getKnowledgeBaseId());
        // KB before document before version: concurrent publishers share this order.
        var kb = bases.lockById(initial.getKnowledgeBaseId());
        if (kb == null || !Objects.equals(initial.getProjectId(), kb.getProjectId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND);
        }
        knowledge.requireEditor(userId, kb.getId());
        var doc = documents.findByIdForUpdate(initial.getDocumentId());
        var version = versions.findByIdForUpdate(versionId);
        if (doc == null || version == null
                || !Objects.equals(doc.getId(), version.getDocumentId())
                || !Objects.equals(kb.getId(), doc.getKnowledgeBaseId())
                || !Objects.equals(kb.getProjectId(), doc.getProjectId())
                || !Objects.equals(kb.getId(), version.getKnowledgeBaseId())
                || !Objects.equals(kb.getProjectId(), version.getProjectId())
                || !"ACTIVE".equals(doc.getStatus()) || !"READY".equals(version.getProcessStatus())
                || !"APPROVED".equals(version.getPublishStatus())) {
            throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        }
        Long previous = doc.getCurrentPublishedVersionId();
        if (previous != null) {
            if (versions.supersede(previous) != 1) throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
            versions.markDependencies(previous);
        }
        if (versions.publish(versionId) != 1) throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        if (documents.publish(doc.getId(), versionId) != 1) throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        KnowledgeSnapshotEntity snapshot = new KnowledgeSnapshotEntity();
        snapshot.setProjectId(kb.getProjectId()); snapshot.setKnowledgeBaseId(kb.getId());
        Integer currentSnapshotNo = bases.currentSnapshotNo(kb.getId());
        snapshot.setSnapshotNo((currentSnapshotNo == null ? 0 : currentSnapshotNo) + 1);
        snapshot.setReason("DOCUMENT_PUBLISH"); snapshot.setCreatedBy(userId);
        if (snapshots.insert(snapshot) != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        var items = snapshots.currentPublishedItems(kb.getId());
        if (!items.isEmpty() && snapshots.insertItems(snapshot.getId(), items) != items.size()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if (bases.updateSnapshot(kb.getId(), snapshot.getId()) != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        AuditLogEntity audit = new AuditLogEntity();
        audit.setProjectId(kb.getProjectId()); audit.setActorUserId(userId);
        audit.setAction("KNOWLEDGE_VERSION_PUBLISHED"); audit.setResourceType("DOCUMENT_VERSION"); audit.setResourceId(versionId);
        audit.setDetailJson("{\"snapshotId\":" + snapshot.getId() + "}");
        if (audits.insert(audit) != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        var published = versions.findByIdForUpdate(versionId);
        return new Publication(doc.getId(), versionId, "PUBLISHED", snapshot.getId(), snapshot.getSnapshotNo(), published.getPublishedAt());
    }

    private KnowledgeSnapshotEntity requireSnapshot(Long userId, Long id) {
        var s = snapshots.find(id);
        if (s == null) throw new BusinessException(ErrorCode.KNOWLEDGE_SNAPSHOT_NOT_FOUND);
        access.requireMember(userId, s.getProjectId());
        return s;
    }
}
