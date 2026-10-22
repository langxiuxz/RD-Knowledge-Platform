package com.studyagent.onboarding.service;

import com.studyagent.common.exception.*;
import com.studyagent.common.result.PageResult;
import com.studyagent.knowledge.mapper.KnowledgeSnapshotMapper;
import com.studyagent.knowledge.mapper.KnowledgeBaseMapper;
import com.studyagent.assessment.mapper.AssessmentMapper;
import com.studyagent.onboarding.dto.*;
import com.studyagent.onboarding.entity.*;
import com.studyagent.onboarding.mapper.OnboardingMapper;
import com.studyagent.onboarding.vo.OnboardingVOs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.*;

@Service
public class OnboardingPathService {
    private final OnboardingMapper mapper;
    private final KnowledgeSnapshotMapper snapshots;
    private final OnboardingSupport support;
    private final KnowledgeBaseMapper bases;
    private final AssessmentMapper assessments;
    public OnboardingPathService(OnboardingMapper mapper,KnowledgeSnapshotMapper snapshots,OnboardingSupport support,
                                 KnowledgeBaseMapper bases,AssessmentMapper assessments) {
        this.mapper=mapper; this.snapshots=snapshots; this.support=support; this.bases=bases; this.assessments=assessments;
    }
    public PageResult<OnboardingVOs.Path> list(Long userId,Long projectId,OnboardingQueries.Paths query) {
        support.member(userId,projectId);
        return new PageResult<>(mapper.countPaths(projectId,query),mapper.listPaths(projectId,query,
            (query.page().longValue()-1)*query.pageSize()).stream().map(OnboardingVOs.Path::from).toList());
    }
    public OnboardingVOs.PathDetail get(Long userId,Long id) {
        var path=find(id); support.member(userId,path.getProjectId());
        var tasks=mapper.tasks(id); var graph=TaskGraph.prerequisites(tasks,mapper.dependencies(id));
        return new OnboardingVOs.PathDetail(OnboardingVOs.Path.from(path),tasks.stream()
            .map(t->OnboardingVOs.Task.from(t,List.copyOf(graph.get(t.getId())))).toList());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OnboardingVOs.Id create(Long userId,Long projectId,OnboardingRequests.Path request) {
        support.lockWrite(userId,projectId,true); requireSnapshot(projectId,request.knowledgeSnapshotId());
        var path=new OnboardingPathEntity(); path.setProjectId(projectId); path.setCreatedBy(userId);
        metadata(path,request); mapper.insertPath(path);
        support.audit(projectId,userId,"ONBOARDING_PATH_CREATE","ONBOARDING_PATH",path.getId());
        return new OnboardingVOs.Id(path.getId());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void update(Long userId,Long id,OnboardingRequests.Path request) {
        var path=lockDraft(userId,id); requireSnapshot(path.getProjectId(),request.knowledgeSnapshotId());
        for(var task:mapper.tasks(id)) validateResource(path.getProjectId(),request.knowledgeSnapshotId(),task,false);
        metadata(path,request); mapper.updatePath(path);
        support.audit(path.getProjectId(),userId,"ONBOARDING_PATH_UPDATE","ONBOARDING_PATH",id);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OnboardingVOs.Id addTask(Long userId,Long pathId,OnboardingRequests.Task request) {
        var path=lockDraft(userId,pathId);
        var tasks=mapper.tasks(pathId);
        if(tasks.size()>=500) throw TaskGraph.invalid("每条路径最多500个任务");
        var task=new PathTaskEntity(); task.setPathId(pathId); copy(task,request);
        validateResource(path.getProjectId(),path.getKnowledgeSnapshotId(),task,false);
        // Validate all edges before inserting. Generated ID is supplied after task creation.
        validatePrerequisites(tasks,null,request.prerequisiteTaskIds());
        mapper.insertTask(task); replaceDependencies(path,task,request.prerequisiteTaskIds(),true);
        support.audit(path.getProjectId(),userId,"ONBOARDING_TASK_CREATE","PATH_TASK",task.getId());
        return new OnboardingVOs.Id(task.getId());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateTask(Long userId,Long id,OnboardingRequests.Task request) {
        var initial=task(id); var path=lockDraft(userId,initial.getPathId());
        var task=mapper.lockTask(id);
        if(task==null) throw new BusinessException(ErrorCode.ONBOARDING_PATH_NOT_FOUND,"路径任务不存在");
        Long previousResourceId=task.getResourceId();
        String previousResourceType=task.getResourceType();
        copy(task,request);
        validateResource(path.getProjectId(),path.getKnowledgeSnapshotId(),task,false);
        validatePrerequisites(mapper.tasks(path.getId()),id,request.prerequisiteTaskIds());
        var edges=new ArrayList<>(mapper.dependencies(path.getId())); edges.removeIf(e->id.equals(e.getTaskId()));
        edges.addAll(edges(path.getId(),id,request.prerequisiteTaskIds()));
        TaskGraph.prerequisites(mapper.tasks(path.getId()),edges);
        boolean resourceChanged=!Objects.equals(previousResourceId,task.getResourceId()) || !Objects.equals(previousResourceType,task.getResourceType());
        mapper.updateTask(task); replaceDependencies(path,task,request.prerequisiteTaskIds(),resourceChanged);
        support.audit(path.getProjectId(),userId,"ONBOARDING_TASK_UPDATE","PATH_TASK",id);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteTask(Long userId,Long id) {
        var task=task(id); var path=lockDraft(userId,task.getPathId());
        mapper.deleteDependencies(id); mapper.deleteContentDependency(id); mapper.deleteTask(id);
        support.audit(path.getProjectId(),userId,"ONBOARDING_TASK_DELETE","PATH_TASK",id);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void order(Long userId,Long id,OnboardingRequests.Order request) {
        var path=lockDraft(userId,id); var tasks=mapper.tasks(id);
        var expected=new HashSet<>(tasks.stream().map(PathTaskEntity::getId).toList());
        if(request.taskIds().size()!=expected.size() || !expected.equals(new HashSet<>(request.taskIds())))
            throw TaskGraph.invalid("必须一次提交全部任务，且不能包含重复或外部任务");
        TaskGraph.prerequisites(tasks,mapper.dependencies(id));
        int order=1; for(Long taskId:request.taskIds()) mapper.updateOrder(taskId,order++);
        support.audit(path.getProjectId(),userId,"ONBOARDING_TASK_ORDER","ONBOARDING_PATH",id);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void publish(Long userId,Long id) {
        var original=find(id); support.lockWrite(userId,original.getProjectId(),true); var path=mapper.lockPath(id);
        if("PUBLISHED".equals(path.getStatus())) return; // Retry must not increment version or repeat audit.
        draft(path); validatePublishedResources(path);
        if(mapper.publishPath(id)!=1) throw TaskGraph.invalid("路径状态已改变");
        support.audit(path.getProjectId(),userId,"ONBOARDING_PATH_PUBLISH","ONBOARDING_PATH",id);
    }
    void validatePublishedResources(OnboardingPathEntity path) {
        requireSnapshot(path.getProjectId(),path.getKnowledgeSnapshotId());
        var tasks=mapper.tasks(path.getId());
        if(tasks.isEmpty()) throw TaskGraph.invalid("路径至少需要一个任务");
        TaskGraph.prerequisites(tasks,mapper.dependencies(path.getId()));
        for(var task:tasks) validateResource(path.getProjectId(),path.getKnowledgeSnapshotId(),task,true);
        if(!mapper.unresolvedDependencies(path.getId()).isEmpty()) throw TaskGraph.invalid("存在未确认的 NEEDS_REVIEW 内容依赖");
    }
    private void requireSnapshot(Long projectId,Long snapshotId) {
        var snapshot=snapshots.find(snapshotId);
        if(snapshot==null) throw new BusinessException(ErrorCode.KNOWLEDGE_SNAPSHOT_NOT_FOUND);
        if(!projectId.equals(snapshot.getProjectId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        // Same lock as knowledge publication: dependency registration/checks cannot race invalidation.
        var base=bases.lockById(snapshot.getKnowledgeBaseId());
        if(base==null || !projectId.equals(base.getProjectId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if(!"ACTIVE".equals(base.getStatus())) throw TaskGraph.invalid("知识库已归档，不能用于新的路径发布或分配");
    }
    private void validateResource(Long projectId,Long snapshotId,PathTaskEntity task,boolean publishing) {
        String expected=switch(task.getType()) {
            case "READ" -> "DOCUMENT_VERSION"; case "QUIZ" -> "QUIZ"; case "DRILL" -> "DRILL";
            case "CHECKPOINT" -> "NONE"; default -> throw TaskGraph.invalid("未知任务类型");
        };
        String rule=("READ".equals(task.getType()) || "CHECKPOINT".equals(task.getType()))?"MANUAL":"AUTO";
        if(!expected.equals(task.getResourceType()) || !rule.equals(task.getCompletionRule()) ||
            ("NONE".equals(expected) ? task.getResourceId()!=null : task.getResourceId()==null))
            throw TaskGraph.invalid("任务类型、资源和完成规则不匹配");
        if("READ".equals(task.getType()) && snapshots.findItems(snapshotId).stream()
            .noneMatch(item->task.getResourceId().equals(item.documentVersionId())))
            throw TaskGraph.invalid("阅读文档版本必须属于路径绑定的正式知识快照");
        if("QUIZ".equals(task.getType())) {
            var quiz=assessments.quiz(task.getResourceId());
            if(quiz==null || !projectId.equals(quiz.getProjectId()) || !snapshotId.equals(quiz.getKnowledgeSnapshotId())
                || !(publishing?"PUBLISHED".equals(quiz.getStatus()):Set.of("READY","PUBLISHED").contains(quiz.getStatus())))
                throw TaskGraph.invalid("QUIZ resource must match project and snapshot and be ready or published");
        }
        if("DRILL".equals(task.getType())) {
            var scenario=assessments.scenario(task.getResourceId());
            if(scenario==null || !projectId.equals(scenario.getProjectId()) || !snapshotId.equals(scenario.getKnowledgeSnapshotId())
                || !(publishing?"PUBLISHED".equals(scenario.getStatus()):Set.of("DRAFT","PUBLISHED").contains(scenario.getStatus())))
                throw TaskGraph.invalid("DRILL resource must match project and snapshot and be published before path publication");
        }
    }
    private void replaceDependencies(OnboardingPathEntity path,PathTaskEntity task,List<Long> ids,boolean resourceChanged) {
        mapper.deleteOutgoingDependencies(task.getId());
        for(var edge:edges(path.getId(),task.getId(),ids)) mapper.insertDependency(edge);
        if(resourceChanged) {
            mapper.deleteContentDependency(task.getId());
            if("READ".equals(task.getType())) mapper.insertContentDependency(path.getProjectId(),task.getId(),task.getResourceId());
        }
    }
    private static List<TaskDependencyEntity> edges(Long pathId,Long taskId,List<Long> ids) {
        return ids.stream().map(id->{var e=new TaskDependencyEntity(); e.setPathId(pathId);e.setTaskId(taskId);e.setPrerequisiteTaskId(id);return e;}).toList();
    }
    private static void validatePrerequisites(List<PathTaskEntity> tasks,Long id,List<Long> ids) {
        var allowed=new HashSet<>(tasks.stream().map(PathTaskEntity::getId).toList());
        if(new HashSet<>(ids).size()!=ids.size() || (id!=null && ids.contains(id)) || !allowed.containsAll(ids))
            throw TaskGraph.invalid("前置任务必须属于当前路径，不能重复或引用自身");
    }
    private OnboardingPathEntity lockDraft(Long userId,Long id) {
        var path=find(id); support.lockWrite(userId,path.getProjectId(),true); path=mapper.lockPath(id); draft(path); requireSnapshot(path.getProjectId(),path.getKnowledgeSnapshotId()); return path;
    }
    private static void draft(OnboardingPathEntity path) {
        if(path==null || !"DRAFT".equals(path.getStatus())) throw TaskGraph.invalid("只有 DRAFT 路径可以编辑");
    }
    OnboardingPathEntity find(Long id) {
        var path=mapper.findPath(id); if(path==null) throw new BusinessException(ErrorCode.ONBOARDING_PATH_NOT_FOUND); return path;
    }
    private PathTaskEntity task(Long id) {
        var task=mapper.findTask(id); if(task==null) throw new BusinessException(ErrorCode.ONBOARDING_PATH_NOT_FOUND,"路径任务不存在"); return task;
    }
    private static void metadata(OnboardingPathEntity path,OnboardingRequests.Path r) {
        path.setName(r.name().strip()); path.setTargetRole(r.targetRole()); path.setDescription(r.description()); path.setKnowledgeSnapshotId(r.knowledgeSnapshotId());
    }
    private static void copy(PathTaskEntity task,OnboardingRequests.Task r) {
        task.setType(r.type());task.setTitle(r.title().strip());task.setDescription(r.description());task.setResourceType(r.resourceType());
        task.setResourceId(r.resourceId());task.setCompletionRule(r.completionRule());task.setEstimatedMinutes(r.estimatedMinutes());
        task.setRequired(r.required());task.setSortOrder(r.sortOrder());
    }
}
