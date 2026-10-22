package com.studyagent.document.service;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.common.result.PageResult;
import com.studyagent.document.dto.*;
import com.studyagent.document.entity.*;
import com.studyagent.document.mapper.*;
import com.studyagent.document.vo.*;
import com.studyagent.file.service.FileStorageService;
import com.studyagent.knowledge.mapper.KnowledgeBaseMapper;
import com.studyagent.knowledge.service.KnowledgeBaseService;
import com.studyagent.project.entity.AuditLogEntity;
import com.studyagent.project.mapper.AuditLogMapper;
import com.studyagent.project.service.ProjectAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

@Service
public class DocumentService {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("pdf", "docx", "txt", "md");
    private final DocumentMapper documents;
    private final DocumentVersionMapper versions;
    private final KnowledgeBaseService knowledge;
    private final KnowledgeBaseMapper bases;
    private final FileStorageService storage;
    private final DocumentIndexDispatcher dispatcher;
    private final ProjectAccessService access;
    private final AuditLogMapper audits;

    public DocumentService(DocumentMapper documents, DocumentVersionMapper versions, KnowledgeBaseService knowledge,
            KnowledgeBaseMapper bases, FileStorageService storage, DocumentIndexDispatcher dispatcher,
            ProjectAccessService access, AuditLogMapper audits) {
        this.documents=documents; this.versions=versions; this.knowledge=knowledge; this.bases=bases;
        this.storage=storage; this.dispatcher=dispatcher; this.access=access; this.audits=audits;
    }

    @Transactional
    public DocumentIdVO upload(Long userId, Long kbId, String title, String changeNote, MultipartFile file) {
        var kb=knowledge.requireEditor(userId,kbId);
        bases.lockById(kbId);
        knowledge.requireEditor(userId,kbId);
        validateFile(file);
        DocumentEntity document=new DocumentEntity();
        document.setProjectId(kb.getProjectId()); document.setKnowledgeBaseId(kbId);
        document.setCreatedBy(userId); document.setTitle(title); document.setStatus("ACTIVE");
        documents.insert(document);
        return storeVersion(document,1,changeNote,file);
    }

    @Transactional
    public DocumentIdVO uploadVersion(Long userId, Long documentId, String changeNote, MultipartFile file) {
        var initial=requireOwned(userId,documentId);
        knowledge.requireEditor(userId,initial.getKnowledgeBaseId());
        bases.lockById(initial.getKnowledgeBaseId());
        knowledge.requireEditor(userId,initial.getKnowledgeBaseId());
        var document=documents.findByIdForUpdate(documentId);
        if (!"ACTIVE".equals(document.getStatus())) throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        validateFile(file);
        var latest=versions.latestForUpdate(documentId);
        return storeVersion(document,latest==null?1:latest.getVersionNo()+1,changeNote,file);
    }

    private DocumentIdVO storeVersion(DocumentEntity document,int number,String note,MultipartFile file) {
        FileStorageService.StoredFile stored;
        String hash;
        try (InputStream input=file.getInputStream()) { hash=hash(input); stored=storage.store(file); }
        catch (IOException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR,"文件保存失败"); }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { if (status!=STATUS_COMMITTED) safeDelete(stored.storageKey()); }
            });
        }
        DocumentVersionEntity v=new DocumentVersionEntity();
        v.setDocumentId(document.getId()); v.setProjectId(document.getProjectId()); v.setKnowledgeBaseId(document.getKnowledgeBaseId());
        v.setVersionNo(number); v.setFileName(stored.originalName()); v.setFileType(typeOf(stored.originalName()));
        v.setMimeType(stored.contentType()); v.setFileSize(stored.size()); v.setContentHash(hash); v.setStorageKey(stored.storageKey());
        v.setProcessStatus("UPLOADED"); v.setPublishStatus("DRAFT"); v.setProgress(0); v.setChunkCount(0);
        v.setCurrentTaskId(newTaskId()); v.setChangeNote(note);
        try { versions.insert(v); documents.touch(document.getId()); }
        catch (RuntimeException e) { safeDelete(stored.storageKey()); throw e; }
        afterCommit(v);
        return new DocumentIdVO(document.getId(),v.getId(),number,"UPLOADED",v.getCurrentTaskId());
    }

    public PageResult<DocumentVO> list(Long userId,Long kbId,DocumentQuery q) {
        var kb=knowledge.requireMember(userId,kbId);
        boolean learner="LEARNER".equals(access.requireMember(userId,kb.getProjectId()).getMyRole());
        if (q.status()!=null&&!Set.of("ACTIVE","ARCHIVED").contains(q.status())) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        var rows=documents.findPage(kbId,q.keyword(),q.status(),(long)(q.page()-1)*q.pageSize(),q.pageSize(),learner)
                .stream().map(DocumentVO::from).toList();
        return new PageResult<>(documents.count(kbId,q.keyword(),q.status(),learner),rows);
    }

    public DocumentDetailVO get(Long userId,Long id) {
        var doc=requireOwned(userId,id);
        boolean learner=isLearner(userId,doc.getProjectId());
        var visible=versions.findByDocumentId(id).stream().filter(v->!learner||published(v)).map(DocumentVersionVO::from).toList();
        if (learner && visible.isEmpty()) throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        return new DocumentDetailVO(DocumentVO.from(doc),visible);
    }

    public DocumentVersionVO version(Long userId,Long id) { return DocumentVersionVO.from(requireVersion(userId,id,false)); }
    public DocumentStatusVO status(Long userId,Long id) { return DocumentStatusVO.from(requireVersion(userId,id,false)); }

    @Transactional
    public DocumentTaskVO reprocess(Long userId,Long id) {
        var v=requireVersion(userId,id,true);
        if (!"FAILED".equals(v.getProcessStatus())) throw new BusinessException(ErrorCode.DOCUMENT_PROCESSING);
        String hash;
        try (InputStream input=storage.load(v.getStorageKey()).getInputStream()) { hash=hash(input); }
        catch (IOException e) { throw new BusinessException(ErrorCode.FILE_NOT_FOUND); }
        String taskId=newTaskId();
        if (versions.startTask(id,v.getCurrentTaskId(),taskId,hash)!=1) throw new BusinessException(ErrorCode.DOCUMENT_PROCESSING);
        v.setCurrentTaskId(taskId); v.setContentHash(hash); afterCommit(v);
        return new DocumentTaskVO(taskId,"PROCESSING");
    }

    @Transactional
    public void submitReview(Long userId,Long id) {
        requireVersion(userId,id,true);
        if (versions.submitReview(id)!=1) throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
    }

    @Transactional
    public void review(Long userId,Long id,ReviewVersionRequest request) {
        var v=requireVersion(userId,id,true);
        if (versions.review(id,request.decision(),userId,request.comment())!=1) throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        AuditLogEntity audit=new AuditLogEntity(); audit.setProjectId(v.getProjectId()); audit.setActorUserId(userId);
        audit.setAction("KNOWLEDGE_VERSION_REVIEWED"); audit.setResourceType("DOCUMENT_VERSION"); audit.setResourceId(id);
        audits.insert(audit);
    }

    @Transactional
    public void archive(Long userId,Long id) {
        var doc=requireOwned(userId,id); knowledge.requireEditor(userId,doc.getKnowledgeBaseId());
        bases.lockById(doc.getKnowledgeBaseId()); documents.archive(id);
    }

    @Transactional
    public void progress(Long versionId,DocumentProgressRequest request) {
        var v=versions.findByIdForUpdate(versionId);
        if (v==null) throw new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND);
        if (!Objects.equals(v.getCurrentTaskId(),request.taskId()) || Set.of("READY","FAILED").contains(v.getProcessStatus())) return;
        String next=request.status();
        List<String> stages=List.of("UPLOADED","PARSING","CHUNKING","EMBEDDING","READY");
        int current=stages.indexOf(v.getProcessStatus()), target=stages.indexOf(next);
        if (!"FAILED".equals(next) && target<current) return; // delayed duplicate delivery
        if (!"FAILED".equals(next) && (target<0 || target>current+1)) throw new BusinessException(ErrorCode.VALIDATION_FAILED,"非法文档状态迁移");
        if (request.progress()==null || request.progress()<0 || request.progress()>100) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        if (!"FAILED".equals(next) && request.progress()<v.getProgress()) return;
        if ("READY".equals(next) && request.progress()!=100) throw new BusinessException(ErrorCode.VALIDATION_FAILED,"READY必须为100");
        if ("FAILED".equals(next) && (request.errorMessage()==null || request.errorMessage().isBlank())) throw new BusinessException(ErrorCode.VALIDATION_FAILED,"FAILED必须有错误信息");
        versions.updateProgress(versionId,request.taskId(),next,"FAILED".equals(next)?v.getProgress():request.progress(),request.chunkCount(),request.errorMessage(),null,"READY".equals(next));
    }

    public DocumentEntity requireOwned(Long userId,Long id) {
        var doc=documents.findById(id);
        if (doc==null) throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        access.requireMember(userId,doc.getProjectId()); return doc;
    }

    private DocumentVersionEntity requireVersion(Long userId,Long id,boolean editor) {
        var v=versions.findById(id);
        if (v==null) throw new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND);
        if (editor) {
            knowledge.requireEditor(userId,v.getKnowledgeBaseId());
            var doc=documents.findById(v.getDocumentId());
            if (!"ACTIVE".equals(doc.getStatus())) throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        } else if (isLearner(userId,v.getProjectId())&&!published(v)) throw new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND);
        return v;
    }
    private boolean isLearner(Long userId,Long projectId) { return "LEARNER".equals(access.requireMember(userId,projectId).getMyRole()); }
    private boolean published(DocumentVersionEntity v) { return Set.of("PUBLISHED","SUPERSEDED").contains(v.getPublishStatus()); }
    public org.springframework.core.io.Resource loadRegistered(String key) {
        if (!versions.existsByStorageKey(key)) throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        try { return storage.load(key); } catch (IOException e) { throw new BusinessException(ErrorCode.FILE_NOT_FOUND); }
    }
    private void afterCommit(DocumentVersionEntity v) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { dispatcher.dispatch(v); }
            });
        } else dispatcher.dispatch(v);
    }
    private String hash(InputStream input) throws IOException {
        try { MessageDigest digest=MessageDigest.getInstance("SHA-256"); byte[] buffer=new byte[8192]; int count;
            while ((count=input.read(buffer))!=-1) digest.update(buffer,0,count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void safeDelete(String key) { try { storage.delete(key); } catch (IOException ignored) {} }
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.FILE_INVALID, "文件不能为空");
        if (file.getSize() > MAX_BYTES) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        String name = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (name.isBlank() || name.length() > 255 || name.contains("..") || name.contains("/") || name.contains("\\"))
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件名不合法");
        String type = typeOf(name);
        if (!TYPES.contains(type)) throw new BusinessException(ErrorCode.FILE_INVALID, "仅支持 PDF/DOCX/TXT/MD");
        String mime = file.getContentType();
        if (mime != null && !mime.isBlank() && !mime.equals("application/octet-stream") && !mimeAllowed(type, mime))
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件MIME类型不合法");
        validateContent(type, file);
    }

    private void validateContent(String type, MultipartFile file) {
        try {
            boolean valid = switch (type) {
                case "pdf" -> hasPdfSignature(file);
                case "docx" -> hasDocxStructure(file);
                case "txt", "md" -> hasNoBinaryNullBytes(file);
                default -> false;
            };
            if (!valid) throw new BusinessException(ErrorCode.FILE_INVALID, "文件内容与类型不匹配");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "无法读取上传文件");
        }
    }

    private boolean hasPdfSignature(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            byte[] signature = input.readNBytes(5);
            return signature.length == 5 && signature[0] == '%' && signature[1] == 'P'
                    && signature[2] == 'D' && signature[3] == 'F' && signature[4] == '-';
        }
    }

    private boolean hasDocxStructure(MultipartFile file) throws IOException {
        boolean contentTypes = false;
        boolean documentXml = false;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                contentTypes |= "[Content_Types].xml".equals(entry.getName());
                documentXml |= "word/document.xml".equals(entry.getName());
                if (contentTypes && documentXml) return true;
            }
        }
        return false;
    }

    private boolean hasNoBinaryNullBytes(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            byte[] sample = input.readNBytes(4096);
            for (byte value : sample) if (value == 0) return false;
            return true;
        }
    }

    private boolean mimeAllowed(String type, String mime) {
        return switch (type) {
            case "pdf" -> mime.equalsIgnoreCase("application/pdf");
            case "docx" -> mime.equalsIgnoreCase("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "txt" -> mime.equalsIgnoreCase("text/plain");
            case "md" -> mime.equalsIgnoreCase("text/markdown") || mime.equalsIgnoreCase("text/plain");
            default -> false;
        };
    }

    private String typeOf(String name) { String ext = StringUtils.getFilenameExtension(name); return ext == null ? "" : ext.toLowerCase(Locale.ROOT); }
    private String newTaskId() { return "task_" + UUID.randomUUID().toString().replace("-", ""); }
    private String joinUrl(String base, String key) { return base.endsWith("/") ? base + key : base + "/" + key; }
}
