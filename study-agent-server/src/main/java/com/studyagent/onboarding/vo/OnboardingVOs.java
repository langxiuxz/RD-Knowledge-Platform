package com.studyagent.onboarding.vo;

import com.studyagent.onboarding.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OnboardingVOs {
    private OnboardingVOs() {}
    public record Id(Long id) {}
    public record AssignmentIds(List<Long> assignmentIds) {}
    public record Path(Long id, Long projectId, String name, String targetRole, String description,
                       String status, Integer versionNo, Long knowledgeSnapshotId, Long createdBy,
                       LocalDateTime publishedAt, LocalDateTime createTime, LocalDateTime updateTime) {
        public static Path from(OnboardingPathEntity e) {
            return new Path(e.getId(),e.getProjectId(),e.getName(),e.getTargetRole(),e.getDescription(),
                e.getStatus(),e.getVersionNo(),e.getKnowledgeSnapshotId(),e.getCreatedBy(),e.getPublishedAt(),e.getCreateTime(),e.getUpdateTime());
        }
    }
    public record Task(Long id, String type, String title, String description, String resourceType,
                       Long resourceId, String completionRule, Integer estimatedMinutes, Boolean required,
                       Integer sortOrder, List<Long> prerequisiteTaskIds) {
        public static Task from(PathTaskEntity e,List<Long> ids) {
            return new Task(e.getId(),e.getType(),e.getTitle(),e.getDescription(),e.getResourceType(),
                e.getResourceId(),e.getCompletionRule(),e.getEstimatedMinutes(),e.getRequired(),e.getSortOrder(),ids);
        }
    }
    public record PathDetail(Path path, List<Task> tasks) {}
    public record Assignment(Long id, Long projectId, Long pathId, Integer pathVersion, Long knowledgeSnapshotId,
                             Long assigneeUserId, Long assignedBy, String status, BigDecimal progress,
                             LocalDateTime dueAt, LocalDateTime startedAt, LocalDateTime submittedAt,
                             LocalDateTime completedAt, LocalDateTime createTime) {
        public static Assignment from(AssignmentEntity e) {
            return new Assignment(e.getId(),e.getProjectId(),e.getPathId(),e.getPathVersion(),e.getKnowledgeSnapshotId(),
                e.getAssigneeUserId(),e.getAssignedBy(),e.getStatus(),e.getProgress(),e.getDueAt(),e.getStartedAt(),
                e.getSubmittedAt(),e.getCompletedAt(),e.getCreateTime());
        }
    }
    public record AssignmentTask(Long id, Long sourcePathTaskId, String type, String title, String resourceType,
                                 Long resourceId, String completionRule, Boolean required, Integer sortOrder,
                                 List<Long> prerequisiteTaskIds, String status, Long quizAttemptId, Long drillRunId,
                                 String completionNote, LocalDateTime openedAt, LocalDateTime completedAt) {
        public static AssignmentTask from(AssignmentTaskEntity e,List<Long> ids) {
            return new AssignmentTask(e.getId(),e.getSourcePathTaskId(),e.getType(),e.getTitle(),e.getResourceType(),
                e.getResourceId(),e.getCompletionRule(),e.getRequired(),e.getSortOrder(),ids,e.getStatus(),
                e.getQuizAttemptId(),e.getDrillRunId(),e.getCompletionNote(),e.getOpenedAt(),e.getCompletedAt());
        }
    }
    public record AssignmentDetail(Assignment assignment, List<AssignmentTask> tasks) {}
}
