package com.studyagent.assessment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.assessment.mapper.AssessmentMapper;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.knowledge.entity.KnowledgeSnapshotEntity;
import com.studyagent.knowledge.mapper.KnowledgeBaseMapper;
import com.studyagent.knowledge.mapper.KnowledgeSnapshotMapper;
import com.studyagent.onboarding.service.OnboardingSupport;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class AssessmentSupport {
    private final OnboardingSupport projects;
    private final KnowledgeSnapshotMapper snapshots;
    private final KnowledgeBaseMapper bases;
    private final AssessmentMapper mapper;
    private final ObjectMapper json;
    public AssessmentSupport(OnboardingSupport projects,KnowledgeSnapshotMapper snapshots,
                             KnowledgeBaseMapper bases,AssessmentMapper mapper,ObjectMapper json) {
        this.projects=projects; this.snapshots=snapshots; this.bases=bases; this.mapper=mapper; this.json=json;
    }
    public boolean manager(Long userId,Long projectId) {
        return !"LEARNER".equals(projects.member(userId,projectId).getMyRole());
    }
    public String memberRole(Long userId,Long projectId) {
        return projects.member(userId,projectId).getMyRole();
    }
    public void requireManager(Long userId,Long projectId) {
        if(!manager(userId,projectId)) throw new BusinessException(ErrorCode.PROJECT_ROLE_INSUFFICIENT);
    }
    public void requireLearner(Long userId,Long projectId) {
        if(manager(userId,projectId)) throw new BusinessException(ErrorCode.PROJECT_ROLE_INSUFFICIENT);
    }
    public void lock(Long userId,Long projectId,boolean manager) { projects.lockWrite(userId,projectId,manager); }
    public void audit(Long projectId,Long userId,String action,String type,Long id) { projects.audit(projectId,userId,action,type,id); }
    public KnowledgeSnapshotEntity snapshot(Long projectId,Long snapshotId,boolean lockBase) {
        var snapshot=snapshots.find(snapshotId);
        if(snapshot==null) throw new BusinessException(ErrorCode.KNOWLEDGE_SNAPSHOT_NOT_FOUND);
        if(!Objects.equals(projectId,snapshot.getProjectId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if(lockBase) {
            var base=bases.lockById(snapshot.getKnowledgeBaseId());
            if(base==null || !Objects.equals(projectId,base.getProjectId())) throw new BusinessException(ErrorCode.FORBIDDEN);
            if(!"ACTIVE".equals(base.getStatus())) throw invalid("知识库已归档");
        }
        return snapshot;
    }
    public List<Long> versions(Long projectId,Long snapshotId) {
        var snapshot=snapshot(projectId,snapshotId,false);
        return snapshots.findVersionIds(snapshotId,projectId,snapshot.getKnowledgeBaseId());
    }
    public void validateSources(Long projectId,Long snapshotId,JsonNode refs) {
        if(refs==null || !refs.isArray() || refs.isEmpty() || refs.size()>100) throw invalid("题目必须包含快照中的来源版本");
        Set<Long> allowed=new HashSet<>(versions(projectId,snapshotId));
        for(var ref:refs) {
            var version=ref.get("documentVersionId");
            if(version==null || !version.isIntegralNumber() || !allowed.contains(version.longValue()))
                throw invalid("题目来源不属于固定知识快照");
            var sid=ref.get("knowledgeSnapshotId");
            if(sid!=null && (!sid.isIntegralNumber() || sid.longValue()!=snapshotId)) throw invalid("来源快照不匹配");
        }
    }
    public void depend(Long projectId,String type,Long id,Collection<Long> versions) {
        for(Long version:new TreeSet<>(versions)) mapper.insertContentDependency(projectId,type,id,version);
    }
    public void requireCurrent(String type,Long id) {
        if(mapper.unresolvedDependencies(type,id)>0) throw conflict("内容依赖存在 NEEDS_REVIEW，请先确认知识变更");
    }
    public JsonNode read(String value) {
        if(value==null) return null;
        try { return json.readTree(value); } catch(JsonProcessingException ex) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
    }
    public String write(Object value) {
        try { return json.writeValueAsString(value); } catch(JsonProcessingException ex) { throw invalid("JSON 内容不合法"); }
    }
    public JsonNode tree(Object value) { return json.valueToTree(value); }
    public List<String> strings(String value) {
        JsonNode node=read(value); List<String> result=new ArrayList<>();
        if(node!=null && node.isArray()) node.forEach(v->result.add(v.asText())); return result;
    }
    public static BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_FAILED,message); }
    public static BusinessException conflict(String message) { return new BusinessException(ErrorCode.QUIZ_ALREADY_SUBMITTED,message); }
    public static BusinessException missing() { return new BusinessException(ErrorCode.QUIZ_NOT_FOUND,"Assessment 资源不存在"); }
}
