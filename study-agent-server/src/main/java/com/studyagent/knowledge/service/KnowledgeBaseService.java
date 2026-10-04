package com.studyagent.knowledge.service;

import com.studyagent.ai.client.AiClient;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.common.result.PageResult;
import com.studyagent.document.mapper.DocumentMapper;
import com.studyagent.file.service.FileStorageService;
import com.studyagent.knowledge.dto.CreateKnowledgeBaseRequest;
import com.studyagent.knowledge.dto.KnowledgeBaseQuery;
import com.studyagent.knowledge.dto.UpdateKnowledgeBaseRequest;
import com.studyagent.knowledge.entity.KnowledgeBaseEntity;
import com.studyagent.knowledge.mapper.KnowledgeBaseMapper;
import com.studyagent.knowledge.vo.KnowledgeBaseIdVO;
import com.studyagent.knowledge.vo.KnowledgeBaseVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Set;

@Service
public class KnowledgeBaseService {
    private static final Set<String> STATUSES = Set.of("PROCESSING", "READY", "FAILED");
    private final KnowledgeBaseMapper mapper;
    private final DocumentMapper documentMapper;
    private final FileStorageService fileStorageService;
    private final AiClient aiClient;

    public KnowledgeBaseService(KnowledgeBaseMapper mapper) {
        this(mapper, null, null, null);
    }
    @Autowired
    public KnowledgeBaseService(KnowledgeBaseMapper mapper, DocumentMapper documentMapper,
                                FileStorageService fileStorageService, AiClient aiClient) {
        this.mapper = mapper; this.documentMapper = documentMapper; this.fileStorageService = fileStorageService; this.aiClient = aiClient;
    }

    @Transactional
    public KnowledgeBaseIdVO create(Long userId, CreateKnowledgeBaseRequest request) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity(); entity.setUserId(userId); entity.setName(request.name());
        entity.setDescription(request.description()); entity.setEmbeddingModel(request.embeddingModel()); mapper.insert(entity);
        return new KnowledgeBaseIdVO(entity.getId());
    }
    public PageResult<KnowledgeBaseVO> list(Long userId, KnowledgeBaseQuery query) {
        validateStatus(query.status()); long total = mapper.countByUserId(userId, query.name(), query.status());
        var rows = mapper.findPageByUserId(userId, query.name(), query.status(), (query.page()-1)*query.pageSize(), query.pageSize()).stream().map(this::toVO).toList();
        return new PageResult<>(total, rows);
    }
    public KnowledgeBaseVO get(Long userId, Long id) { return toVO(requireOwned(userId, id)); }
    @Transactional
    public void update(Long userId, Long id, UpdateKnowledgeBaseRequest request) {
        requireOwned(userId,id); KnowledgeBaseEntity entity=new KnowledgeBaseEntity(); entity.setId(id); entity.setUserId(userId);
        entity.setName(request.name()); entity.setDescription(request.description()); mapper.updateByIdAndUserId(entity);
    }
    @Transactional
    public void delete(Long userId, Long id) {
        requireOwned(userId,id);
        if (aiClient != null) {
            try { aiClient.deleteKnowledgeBase(userId,id); }
            catch (RuntimeException e) { throw new BusinessException(ErrorCode.VECTOR_DELETE_FAILED, "向量删除失败，请稍后重试"); }
        }
        if (documentMapper != null && fileStorageService != null) {
            for (String key : documentMapper.findStorageKeysByKnowledgeBaseAndUser(id, userId)) {
                try { fileStorageService.delete(key); }
                catch (IOException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件删除失败，请稍后重试"); }
            }
        }
        mapper.deleteByIdAndUserId(id,userId);
    }
    public KnowledgeBaseEntity requireOwned(Long userId, Long id) {
        KnowledgeBaseEntity entity=mapper.findByIdAndUserId(id,userId); if(entity==null) throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND); return entity;
    }
    private void validateStatus(String status) { if(status!=null&&!STATUSES.contains(status)) throw new BusinessException(ErrorCode.VALIDATION_FAILED,"status不合法"); }
    private KnowledgeBaseVO toVO(KnowledgeBaseEntity e) { return new KnowledgeBaseVO(e.getId(),e.getName(),e.getDescription(),e.getDocumentCount()==null?0:e.getDocumentCount(),e.getChunkCount()==null?0:e.getChunkCount(),e.getStatus()); }
}
