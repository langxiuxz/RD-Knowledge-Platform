package com.studyagent.onboarding.mapper;

import com.studyagent.onboarding.entity.*;
import com.studyagent.onboarding.dto.OnboardingQueries;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface OnboardingMapper {
    OnboardingPathEntity findPath(Long id);
    OnboardingPathEntity lockPath(Long id);
    int insertPath(OnboardingPathEntity path);
    int updatePath(OnboardingPathEntity path);
    int publishPath(Long id);
    long countPaths(@Param("projectId") Long projectId,@Param("query") OnboardingQueries.Paths query);
    List<OnboardingPathEntity> listPaths(@Param("projectId") Long projectId,@Param("query") OnboardingQueries.Paths query,@Param("offset") long offset);
    PathTaskEntity findTask(Long id);
    PathTaskEntity lockTask(Long id);
    List<PathTaskEntity> tasks(Long pathId);
    List<TaskDependencyEntity> dependencies(Long pathId);
    int insertTask(PathTaskEntity task);
    int updateTask(PathTaskEntity task);
    int deleteTask(Long id);
    int deleteDependencies(Long taskId);
    int deleteOutgoingDependencies(Long taskId);
    int insertDependency(TaskDependencyEntity dependency);
    int updateOrder(@Param("id") Long id,@Param("sortOrder") int sortOrder);
    int deleteContentDependency(Long taskId);
    int insertContentDependency(@Param("projectId") Long projectId,@Param("taskId") Long taskId,@Param("versionId") Long versionId);
    List<Long> unresolvedDependencies(Long pathId);
    AssignmentEntity findAssignment(Long id);
    AssignmentEntity lockAssignment(Long id);
    int insertAssignment(AssignmentEntity assignment);
    int updateAssignment(AssignmentEntity assignment);
    AssignmentTaskEntity findAssignmentTask(Long id);
    List<AssignmentTaskEntity> assignmentTasks(Long assignmentId);
    int insertAssignmentTask(AssignmentTaskEntity task);
    int setTaskDependencies(AssignmentTaskEntity task);
    int updateAssignmentTask(AssignmentTaskEntity task);
    int updateAssessmentTask(@Param("task") AssignmentTaskEntity task);
    long countAssignments(@Param("projectId") Long projectId,@Param("assigneeId") Long assigneeId,@Param("query") OnboardingQueries.Assignments query);
    List<AssignmentEntity> listAssignments(@Param("projectId") Long projectId,@Param("assigneeId") Long assigneeId,@Param("query") OnboardingQueries.Assignments query,@Param("offset") long offset);
}
