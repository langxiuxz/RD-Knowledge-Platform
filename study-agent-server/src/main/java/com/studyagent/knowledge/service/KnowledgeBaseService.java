package com.studyagent.knowledge.service;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.common.result.PageResult;
import com.studyagent.knowledge.dto.CreateKnowledgeBaseRequest;
import com.studyagent.knowledge.dto.KnowledgeBaseQuery;
import com.studyagent.knowledge.dto.UpdateKnowledgeBaseRequest;
import com.studyagent.knowledge.entity.KnowledgeBaseEntity;
import com.studyagent.knowledge.mapper.KnowledgeBaseMapper;
import com.studyagent.knowledge.mapper.KnowledgeSnapshotMapper;
import com.studyagent.knowledge.vo.KnowledgeBaseIdVO;
import com.studyagent.knowledge.vo.KnowledgeBaseVO;
import com.studyagent.project.entity.ProjectEntity;
import com.studyagent.project.service.ProjectAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
@Service
public class KnowledgeBaseService {
 private static final Set<String> STATUSES=Set.of("ACTIVE","ARCHIVED");
 private final KnowledgeBaseMapper mapper; private final KnowledgeSnapshotMapper snapshots; private final ProjectAccessService access;
 public KnowledgeBaseService(KnowledgeBaseMapper mapper,KnowledgeSnapshotMapper snapshots,ProjectAccessService access){this.mapper=mapper;this.snapshots=snapshots;this.access=access;}
 @Transactional public KnowledgeBaseIdVO create(Long userId,Long projectId,CreateKnowledgeBaseRequest req){ProjectEntity p=access.requireRole(userId,projectId,"OWNER","MAINTAINER");if(!"ACTIVE".equals(p.getStatus()))throw new BusinessException(ErrorCode.PROJECT_ARCHIVED); KnowledgeBaseEntity e=new KnowledgeBaseEntity();e.setProjectId(projectId);e.setCreatedBy(userId);e.setName(req.name());e.setDescription(req.description());mapper.insert(e);return new KnowledgeBaseIdVO(e.getId());}
 public PageResult<KnowledgeBaseVO> list(Long userId,Long projectId,KnowledgeBaseQuery q){access.requireMember(userId,projectId);if(q.status()!=null&&!STATUSES.contains(q.status()))throw new BusinessException(ErrorCode.VALIDATION_FAILED,"status不合法");long total=mapper.count(projectId,q.name(),q.status());List<KnowledgeBaseVO> rows=mapper.findPage(projectId,q.name(),q.status(),(long)(q.page()-1)*q.pageSize(),q.pageSize()).stream().map(KnowledgeBaseVO::from).toList();return new PageResult<>(total,rows);}
 public KnowledgeBaseVO get(Long userId,Long id){return KnowledgeBaseVO.from(requireMember(userId,id));}
 @Transactional public void update(Long userId,Long id,UpdateKnowledgeBaseRequest req){KnowledgeBaseEntity old=requireEditor(userId,id);KnowledgeBaseEntity e=new KnowledgeBaseEntity();e.setId(id);e.setProjectId(old.getProjectId());e.setName(req.name());e.setDescription(req.description());mapper.update(e);}
 @Transactional public void archive(Long userId,Long id){KnowledgeBaseEntity kb=requireEditor(userId,id);mapper.archive(id);}
 public KnowledgeBaseEntity requireMember(Long userId,Long id){KnowledgeBaseEntity kb=mapper.findById(id);if(kb==null)throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);access.requireMember(userId,kb.getProjectId());return kb;}
 public KnowledgeBaseEntity requireOwned(Long userId,Long id){return requireMember(userId,id);}
 public KnowledgeBaseEntity requireEditor(Long userId,Long id){KnowledgeBaseEntity kb=requireMember(userId,id);ProjectEntity project=access.requireRole(userId,kb.getProjectId(),"OWNER","MAINTAINER");if(!"ACTIVE".equals(project.getStatus()))throw new BusinessException(ErrorCode.PROJECT_ARCHIVED);if(!"ACTIVE".equals(kb.getStatus()))throw new BusinessException(ErrorCode.PROJECT_ARCHIVED,"知识库已归档");return kb;}
}
