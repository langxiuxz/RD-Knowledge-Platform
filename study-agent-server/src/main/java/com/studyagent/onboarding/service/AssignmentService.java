package com.studyagent.onboarding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.common.exception.*;
import com.studyagent.common.result.PageResult;
import com.studyagent.onboarding.dto.*;
import com.studyagent.onboarding.entity.*;
import com.studyagent.onboarding.mapper.OnboardingMapper;
import com.studyagent.onboarding.vo.OnboardingVOs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AssignmentService {
    private final OnboardingMapper mapper;
    private final OnboardingPathService paths;
    private final OnboardingSupport support;
    private final ObjectMapper json;
    public AssignmentService(OnboardingMapper mapper,OnboardingPathService paths,OnboardingSupport support,ObjectMapper json) {
        this.mapper=mapper;this.paths=paths;this.support=support;this.json=json;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OnboardingVOs.AssignmentIds assign(Long userId,Long pathId,OnboardingRequests.Assign request) {
        var original=paths.find(pathId); support.lockWrite(userId,original.getProjectId(),true);
        var path=mapper.lockPath(pathId);
        if(!"PUBLISHED".equals(path.getStatus())) throw TaskGraph.invalid("只能分配已发布的路径");
        if(request.learnerUserIds().isEmpty() || new HashSet<>(request.learnerUserIds()).size()!=request.learnerUserIds().size())
            throw TaskGraph.invalid("分配用户不能重复或为空");
        // Validate the whole batch before the first insert; all snapshots commit atomically.
        for(Long learner:request.learnerUserIds().stream().sorted().toList()) support.requireLearner(path.getProjectId(),learner);
        paths.validatePublishedResources(path);
        var templates=mapper.tasks(pathId);
        var graph=TaskGraph.prerequisites(templates,mapper.dependencies(pathId));
        List<Long> result=new ArrayList<>();
        for(Long learner:request.learnerUserIds()) {
            var assignment=new AssignmentEntity(); assignment.setProjectId(path.getProjectId());assignment.setPathId(pathId);
            assignment.setPathVersion(path.getVersionNo());assignment.setKnowledgeSnapshotId(path.getKnowledgeSnapshotId());
            assignment.setAssigneeUserId(learner);assignment.setAssignedBy(userId);assignment.setDueAt(request.dueAt());
            mapper.insertAssignment(assignment);
            Map<Long,AssignmentTaskEntity> copied=new LinkedHashMap<>();
            for(var source:templates) {
                var task=new AssignmentTaskEntity();task.setAssignmentId(assignment.getId());task.setSourcePathTaskId(source.getId());
                task.setType(source.getType());task.setTitle(source.getTitle());task.setResourceType(source.getResourceType());
                task.setResourceId(source.getResourceId());task.setCompletionRule(source.getCompletionRule());
                task.setRequired(source.getRequired());task.setSortOrder(source.getSortOrder());
                task.setPrerequisiteTaskIdsJson("[]");task.setStatus("PENDING");mapper.insertAssignmentTask(task);copied.put(source.getId(),task);
            }
            // Dependency IDs belong to this assignment, never to the live path template.
            for(var source:templates) {
                var task=copied.get(source.getId()); var ids=graph.get(source.getId()).stream().map(id->copied.get(id).getId()).toList();
                task.setPrerequisiteTaskIdsJson(encode(ids));task.setStatus(ids.isEmpty()?"PENDING":"LOCKED");
                mapper.setTaskDependencies(task);
            }
            support.audit(path.getProjectId(),userId,"ONBOARDING_ASSIGNMENT_CREATE","ASSIGNMENT",assignment.getId());
            result.add(assignment.getId());
        }
        return new OnboardingVOs.AssignmentIds(List.copyOf(result));
    }
    public PageResult<OnboardingVOs.Assignment> list(Long userId,Long projectId,OnboardingQueries.Assignments query) {
        var project=support.member(userId,projectId);
        Long assignee="LEARNER".equals(project.getMyRole())?userId:query.assigneeUserId();
        return new PageResult<>(mapper.countAssignments(projectId,assignee,query),mapper.listAssignments(projectId,assignee,query,
            (query.page().longValue()-1)*query.pageSize()).stream().map(OnboardingVOs.Assignment::from).toList());
    }
    public OnboardingVOs.AssignmentDetail get(Long userId,Long id) {
        var assignment=find(id); var project=support.member(userId,assignment.getProjectId());
        if("LEARNER".equals(project.getMyRole()) && !userId.equals(assignment.getAssigneeUserId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        return new OnboardingVOs.AssignmentDetail(OnboardingVOs.Assignment.from(assignment),mapper.assignmentTasks(id).stream()
            .map(t->OnboardingVOs.AssignmentTask.from(t,decode(t.getPrerequisiteTaskIdsJson()))).toList());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void start(Long userId,Long id) {
        var assignment=lockOwn(userId,id);
        if("IN_PROGRESS".equals(assignment.getStatus())) return;
        if(!"ASSIGNED".equals(assignment.getStatus())) throw TaskGraph.invalid("当前 Assignment 状态不能开始");
        begin(assignment);mapper.updateAssignment(assignment);
        support.audit(assignment.getProjectId(),userId,"ONBOARDING_ASSIGNMENT_START","ASSIGNMENT",id);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void complete(Long userId,Long taskId,OnboardingRequests.Complete request) {
        var initial=mapper.findAssignmentTask(taskId);
        if(initial==null) throw new BusinessException(ErrorCode.ONBOARDING_ASSIGNMENT_NOT_FOUND,"Assignment任务不存在");
        var assignment=lockOwn(userId,initial.getAssignmentId());
        var tasks=mapper.assignmentTasks(assignment.getId());
        var task=tasks.stream().filter(t->taskId.equals(t.getId())).findFirst()
            .orElseThrow(()->new BusinessException(ErrorCode.ONBOARDING_ASSIGNMENT_NOT_FOUND));
        if(!Set.of("READ","CHECKPOINT").contains(task.getType()) || !"MANUAL".equals(task.getCompletionRule()))
            throw TaskGraph.invalid("QUIZ/DRILL 只能由 Assessment 结果完成");
        if("COMPLETED".equals(task.getStatus())) return; // Retries preserve original note and timestamp, even after submit.
        if(!Set.of("ASSIGNED","IN_PROGRESS").contains(assignment.getStatus())) throw TaskGraph.invalid("Assignment 已进入不可修改状态");
        Set<Long> done=new HashSet<>();tasks.stream().filter(t->"COMPLETED".equals(t.getStatus())).forEach(t->done.add(t.getId()));
        if(!done.containsAll(decode(task.getPrerequisiteTaskIdsJson())))
            throw new BusinessException(ErrorCode.ONBOARDING_PREREQUISITE_NOT_MET);
        if(!Set.of("PENDING","IN_PROGRESS").contains(task.getStatus())) throw TaskGraph.invalid("任务状态不允许完成");
        begin(assignment);
        task.setStatus("COMPLETED");task.setCompletionNote(request.note());
        if(task.getOpenedAt()==null) task.setOpenedAt(LocalDateTime.now());task.setCompletedAt(LocalDateTime.now());
        mapper.updateAssignmentTask(task);done.add(task.getId());
        for(var next:tasks) {
            if("LOCKED".equals(next.getStatus()) && done.containsAll(decode(next.getPrerequisiteTaskIdsJson()))) {
                next.setStatus("PENDING");mapper.updateAssignmentTask(next);
            }
        }
        assignment.setProgress(BigDecimal.valueOf(done.size()).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(tasks.size()),2,RoundingMode.HALF_UP));
        mapper.updateAssignment(assignment); // 100% never means PASSED; submit and human review are separate.
        support.audit(assignment.getProjectId(),userId,"ONBOARDING_TASK_COMPLETE","ASSIGNMENT_TASK",taskId);
    }

    /** Locks and validates the pinned Assignment before an Assessment attempt begins. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireAssessmentTask(Long userId, Long taskId, String assessmentType, Long resourceId,
                                      Long projectId, Long snapshotId) {
        var initial=mapper.findAssignmentTask(taskId);
        if(initial==null) throw new BusinessException(ErrorCode.ONBOARDING_ASSIGNMENT_NOT_FOUND,"Assignment任务不存在");
        var assignment=lockOwn(userId,initial.getAssignmentId());
        if(!Objects.equals(projectId,assignment.getProjectId()) || !Objects.equals(snapshotId,assignment.getKnowledgeSnapshotId()))
            throw new BusinessException(ErrorCode.FORBIDDEN,"Assignment 的项目或知识快照与 Assessment 不匹配");
        var task=mapper.assignmentTasks(assignment.getId()).stream().filter(t->taskId.equals(t.getId())).findFirst()
            .orElseThrow(()->new BusinessException(ErrorCode.ONBOARDING_ASSIGNMENT_NOT_FOUND));
        if(!assessmentType.equals(task.getType()) || !assessmentType.equals(task.getResourceType()) ||
            !Objects.equals(resourceId,task.getResourceId()))
            throw new BusinessException(ErrorCode.ONBOARDING_ASSIGNMENT_NOT_FOUND,"Assessment与Assignment任务不匹配");
        if(!Set.of("ASSIGNED","IN_PROGRESS").contains(assignment.getStatus()) ||
            !Set.of("PENDING","IN_PROGRESS").contains(task.getStatus())) throw TaskGraph.invalid("Assignment任务尚未解锁或已锁定");
        Set<Long> done=new HashSet<>(); mapper.assignmentTasks(assignment.getId()).stream()
            .filter(t->"COMPLETED".equals(t.getStatus())).forEach(t->done.add(t.getId()));
        if(!done.containsAll(decode(task.getPrerequisiteTaskIdsJson()))) throw new BusinessException(ErrorCode.ONBOARDING_PREREQUISITE_NOT_MET);
    }

    /** Called only from the transaction that commits a completed Assessment result. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void completeFromAssessment(Long userId, Long taskId, String assessmentType,
                                       Long assessmentResourceId, Long assessmentResultId, Long projectId,
                                       Long snapshotId, boolean passed) {
        if (assessmentResultId == null || assessmentResultId <= 0) throw TaskGraph.invalid("Assessment结果无效");
        var initial=mapper.findAssignmentTask(taskId);
        if(initial==null) throw new BusinessException(ErrorCode.ONBOARDING_ASSIGNMENT_NOT_FOUND,"Assignment任务不存在");
        var assignment=lockOwn(userId,initial.getAssignmentId());
        if(!Objects.equals(projectId,assignment.getProjectId()) || !Objects.equals(snapshotId,assignment.getKnowledgeSnapshotId()))
            throw new BusinessException(ErrorCode.FORBIDDEN,"Assignment 的项目或知识快照与 Assessment 不匹配");
        var task=mapper.assignmentTasks(assignment.getId()).stream().filter(t->taskId.equals(t.getId())).findFirst()
            .orElseThrow(()->new BusinessException(ErrorCode.ONBOARDING_ASSIGNMENT_NOT_FOUND));
        boolean quiz="QUIZ".equals(assessmentType) && "QUIZ".equals(task.getType())
            && "QUIZ".equals(task.getResourceType());
        boolean drill="DRILL".equals(assessmentType) && "DRILL".equals(task.getType())
            && "DRILL".equals(task.getResourceType());
        if ((!quiz && !drill) || !Objects.equals(task.getResourceId(),assessmentResourceId))
            throw new BusinessException(ErrorCode.ONBOARDING_ASSIGNMENT_NOT_FOUND,"Assessment与Assignment任务不匹配");
        Long previous=quiz?task.getQuizAttemptId():task.getDrillRunId();
        if("COMPLETED".equals(task.getStatus())) {
            if(Objects.equals(previous,assessmentResultId)) return;
            throw TaskGraph.invalid("Assignment任务已由其他Assessment结果完成");
        }
        if(!Set.of("ASSIGNED","IN_PROGRESS").contains(assignment.getStatus()))
            throw TaskGraph.invalid("Assignment 已进入不可修改状态");
        if(!Set.of("PENDING","IN_PROGRESS").contains(task.getStatus()))
            throw TaskGraph.invalid("Assessment任务尚未解锁");
        var tasks=mapper.assignmentTasks(assignment.getId());
        Set<Long> done=new HashSet<>();
        tasks.stream().filter(t->"COMPLETED".equals(t.getStatus())).forEach(t->done.add(t.getId()));
        if(!done.containsAll(decode(task.getPrerequisiteTaskIdsJson())))
            throw new BusinessException(ErrorCode.ONBOARDING_PREREQUISITE_NOT_MET);
        task.setStatus(passed?"COMPLETED":"IN_PROGRESS");
        task.setCompletedAt(passed?LocalDateTime.now():null);
        if(quiz) task.setQuizAttemptId(assessmentResultId); else task.setDrillRunId(assessmentResultId);
        if(mapper.updateAssessmentTask(task)!=1) throw TaskGraph.invalid("Assignment任务状态已变化，请刷新后重试");
        begin(assignment);
        if(!passed) {
            mapper.updateAssignment(assignment);
            return;
        }
        done.add(taskId);
        for(var next:tasks) {
            if("LOCKED".equals(next.getStatus()) && done.containsAll(decode(next.getPrerequisiteTaskIdsJson()))) {
                next.setStatus("PENDING"); mapper.updateAssignmentTask(next);
            }
        }
        assignment.setProgress(BigDecimal.valueOf(done.size()).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(tasks.size()),2,RoundingMode.HALF_UP));
        mapper.updateAssignment(assignment);
        support.audit(assignment.getProjectId(),userId,"ASSESSMENT_TASK_COMPLETE","ASSIGNMENT_TASK",taskId);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void submit(Long userId,Long id) {
        var assignment=lockOwn(userId,id);
        if("REVIEW_PENDING".equals(assignment.getStatus())) return;
        if(!"IN_PROGRESS".equals(assignment.getStatus())) throw TaskGraph.invalid("当前状态不能提交复核");
        var tasks=mapper.assignmentTasks(id);
        if(tasks.isEmpty() || tasks.stream().anyMatch(t->Boolean.TRUE.equals(t.getRequired()) && !"COMPLETED".equals(t.getStatus())))
            throw new BusinessException(ErrorCode.ONBOARDING_ASSIGNMENT_INCOMPLETE);
        assignment.setStatus("REVIEW_PENDING");assignment.setSubmittedAt(LocalDateTime.now());mapper.updateAssignment(assignment);
        support.audit(assignment.getProjectId(),userId,"ONBOARDING_ASSIGNMENT_SUBMIT","ASSIGNMENT",id);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssignmentEntity review(Long reviewerId, Long id, String decision) {
        var initial=find(id); support.lockWrite(reviewerId,initial.getProjectId(),true);
        var assignment=mapper.lockAssignment(id);
        if(!"REVIEW_PENDING".equals(assignment.getStatus())) throw TaskGraph.invalid("Assignment 尚未提交复核或已完成复核");
        assignment.setStatus(decision.equals("PASS")?"PASSED":"NEEDS_WORK");
        assignment.setCompletedAt(LocalDateTime.now()); mapper.updateAssignment(assignment);
        support.audit(assignment.getProjectId(),reviewerId,"ASSIGNMENT_REVIEW_"+decision,"ASSIGNMENT",id);
        return assignment;
    }
    private AssignmentEntity lockOwn(Long userId,Long id) {
        var original=find(id);support.lockWrite(userId,original.getProjectId(),false);
        var assignment=mapper.lockAssignment(id);
        if(!userId.equals(assignment.getAssigneeUserId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        return assignment;
    }
    private static void begin(AssignmentEntity assignment) {
        if("ASSIGNED".equals(assignment.getStatus())) {assignment.setStatus("IN_PROGRESS");assignment.setStartedAt(LocalDateTime.now());}
    }
    private AssignmentEntity find(Long id) {
        var a=mapper.findAssignment(id);if(a==null) throw new BusinessException(ErrorCode.ONBOARDING_ASSIGNMENT_NOT_FOUND);return a;
    }
    private String encode(List<Long> ids) {
        try {return json.writeValueAsString(ids);} catch(JsonProcessingException e) {throw new BusinessException(ErrorCode.INTERNAL_ERROR);}
    }
    private List<Long> decode(String value) {
        if(value==null) return List.of();
        try {return json.readValue(value,new TypeReference<List<Long>>(){});} catch(JsonProcessingException e) {throw new BusinessException(ErrorCode.INTERNAL_ERROR,"任务依赖快照读取失败");}
    }
}
