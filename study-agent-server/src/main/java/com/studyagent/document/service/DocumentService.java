package com.studyagent.document.service;

import com.studyagent.ai.client.AiClient;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.common.result.PageResult;
import com.studyagent.config.AppProperties;
import com.studyagent.document.dto.DocumentProgressRequest;
import com.studyagent.document.dto.DocumentQuery;
import com.studyagent.document.entity.DocumentEntity;
import com.studyagent.document.mapper.DocumentMapper;
import com.studyagent.document.vo.DocumentIdVO;
import com.studyagent.document.vo.DocumentStatusVO;
import com.studyagent.document.vo.DocumentTaskVO;
import com.studyagent.document.vo.DocumentVO;
import com.studyagent.file.service.FileStorageService;
import com.studyagent.knowledge.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DocumentService {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("pdf", "docx", "txt", "md");
    private static final Set<String> STATUSES = Set.of("UPLOADED", "PARSING", "CHUNKING", "EMBEDDING", "READY", "FAILED");
    private final DocumentMapper mapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final FileStorageService fileStorageService;
    private final AiClient aiClient;
    private final AppProperties properties;

    public DocumentService(DocumentMapper mapper, KnowledgeBaseService knowledgeBaseService,
                           FileStorageService fileStorageService, AiClient aiClient, AppProperties properties) {
        this.mapper = mapper; this.knowledgeBaseService = knowledgeBaseService;
        this.fileStorageService = fileStorageService; this.aiClient = aiClient; this.properties = properties;
    }

    public DocumentIdVO upload(Long userId, Long knowledgeBaseId, MultipartFile file) {
        knowledgeBaseService.requireOwned(userId, knowledgeBaseId);
        validateFile(file);
        FileStorageService.StoredFile stored;
        try { stored = fileStorageService.store(file); }
        catch (IOException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败"); }
        String taskId = newTaskId();
        DocumentEntity entity = new DocumentEntity();
        entity.setKnowledgeBaseId(knowledgeBaseId); entity.setUserId(userId);
        entity.setFileName(stored.originalName()); entity.setFileType(typeOf(stored.originalName()));
        entity.setMimeType(stored.contentType()); entity.setFileSize(stored.size()); entity.setStorageKey(stored.storageKey());
        entity.setFileUrl(joinUrl(properties.storage().publicBaseUrl(), stored.storageKey()));
        entity.setStatus("UPLOADED"); entity.setProgress(0); entity.setChunkCount(0); entity.setCurrentTaskId(taskId);
        try { mapper.insert(entity); }
        catch (RuntimeException e) { safeDelete(stored.storageKey()); throw e; }
        dispatch(entity);
        return new DocumentIdVO(entity.getId(), "PROCESSING");
    }

    public PageResult<DocumentVO> list(Long userId, Long knowledgeBaseId, DocumentQuery query) {
        knowledgeBaseService.requireOwned(userId, knowledgeBaseId);
        validateStatus(query.status());
        long total = mapper.countByKnowledgeBaseAndUserId(knowledgeBaseId, userId, query.fileName(), query.status());
        var rows = mapper.findPageByKnowledgeBaseAndUserId(knowledgeBaseId, userId, query.fileName(), query.status(),
                (query.page() - 1) * query.pageSize(), query.pageSize()).stream().map(DocumentVO::from).toList();
        return new PageResult<>(total, rows);
    }

    public DocumentStatusVO status(Long userId, Long documentId) { return DocumentStatusVO.from(requireOwned(userId, documentId)); }

    public DocumentTaskVO reprocess(Long userId, Long documentId) {
        DocumentEntity entity = requireOwned(userId, documentId);
        if (!"FAILED".equals(entity.getStatus())) throw new BusinessException(ErrorCode.DOCUMENT_PROCESSING);
        entity.setCurrentTaskId(newTaskId()); entity.setStatus("UPLOADED"); entity.setProgress(0); entity.setChunkCount(0);
        if (mapper.updateTaskStart(entity) == 0) throw new BusinessException(ErrorCode.DOCUMENT_PROCESSING);
        dispatch(entity);
        return new DocumentTaskVO(entity.getCurrentTaskId(), "PROCESSING");
    }

    @Transactional
    public void delete(Long userId, Long documentId) {
        DocumentEntity entity = requireOwned(userId, documentId);
        try { aiClient.deleteDocument(userId, entity.getKnowledgeBaseId(), documentId); }
        catch (RuntimeException e) { throw new BusinessException(ErrorCode.VECTOR_DELETE_FAILED, "向量删除失败，请稍后重试"); }
        try { fileStorageService.delete(entity.getStorageKey()); }
        catch (IOException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件删除失败，请稍后重试"); }
        mapper.deleteByIdAndUserId(documentId, userId);
    }

    @Transactional
    public void progress(Long documentId, DocumentProgressRequest request) {
        DocumentEntity current = mapper.findByIdForUpdate(documentId);
        if (current == null) throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        if (!request.taskId().equals(current.getCurrentTaskId())) return;
        if ("READY".equals(current.getStatus()) || "FAILED".equals(current.getStatus())) return;
        validateProgress(current.getStatus(), current.getProgress(), request.status(), request.progress(), request.errorMessage());
        boolean ready = "READY".equals(request.status());
        int updated = mapper.updateProgressIfCurrentTask(documentId, request.taskId(), request.status(), request.progress(),
                request.chunkCount(), request.errorMessage(), ready);
        if (updated == 0) return;
    }

    public DocumentEntity requireOwned(Long userId, Long documentId) {
        DocumentEntity e = mapper.findByIdAndUserId(documentId, userId);
        if (e == null) throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        return e;
    }

    public org.springframework.core.io.Resource loadRegistered(String storageKey) {
        if (!mapper.existsByStorageKey(storageKey)) throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        try { return fileStorageService.load(storageKey); }
        catch (IOException e) { throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND); }
    }

    private void dispatch(DocumentEntity entity) {
        try {
            AiClient.IndexResponse response = aiClient.index(new AiClient.IndexRequest(entity.getUserId(), entity.getKnowledgeBaseId(),
                    entity.getId(), entity.getCurrentTaskId(), entity.getFileUrl(), entity.getFileType(), entity.getFileName()));
            if (response == null || response.taskId() == null || !entity.getCurrentTaskId().equals(response.taskId()))
                throw new IllegalStateException("AI task was not accepted");
        } catch (RuntimeException e) {
            mapper.updateProgressIfCurrentTask(entity.getId(), entity.getCurrentTaskId(), "FAILED", 0, 0,
                    "AI服务未接受文档处理任务", false);
        }
    }

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
    private void validateStatus(String status) { if (status != null && !STATUSES.contains(status)) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status不合法"); }
    private void validateProgress(String current, Integer currentProgress, String next, Integer progress, String error) {
        if (progress == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "progress不能为空");
        if (!STATUSES.contains(next)) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status不合法");
        if (!"FAILED".equals(next) && currentProgress != null && progress < currentProgress)
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "progress不能回退");
        if ("READY".equals(next) && progress != 100) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "READY必须为100");
        if ("FAILED".equals(next) && (error == null || error.isBlank())) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "FAILED必须有错误信息");
        boolean allowed = next.equals(current) || ("UPLOADED".equals(current) && "PARSING".equals(next))
                || ("PARSING".equals(current) && "CHUNKING".equals(next))
                || ("CHUNKING".equals(current) && "EMBEDDING".equals(next))
                || ("EMBEDDING".equals(current) && "READY".equals(next))
                || (!"READY".equals(current) && "FAILED".equals(next));
        if (!allowed) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "非法文档状态迁移");
    }
    private void safeDelete(String key) { try { fileStorageService.delete(key); } catch (IOException ignored) { } }
}
