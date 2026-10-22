package com.studyagent.insight.mapper;

import com.studyagent.insight.entity.CapabilityReportEntity;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface CapabilityReportMapper {
    int insert(CapabilityReportEntity report);
    int nextVersion(@Param("assignmentId") Long assignmentId);
    CapabilityReportEntity latestForAssignment(@Param("assignmentId") Long assignmentId);
    Map<String,Object> assignmentFacts(@Param("assignmentId") Long assignmentId);
    Map<String,Object> assignmentFactsForUpdate(@Param("assignmentId") Long assignmentId);
    CapabilityReportEntity find(@Param("id") Long id);
    CapabilityReportEntity findForUpdate(@Param("id") Long id);
    int complete(@Param("id") Long id,@Param("taskId") String taskId,@Param("summary") String summary,
        @Param("strengths") String strengths,@Param("risks") String risks,@Param("sources") String sources,
        @Param("modelName") String modelName);
    int fail(@Param("id") Long id,@Param("taskId") String taskId);
    int failStale(@Param("timeoutSeconds") long timeoutSeconds,@Param("batchSize") int batchSize,
                  @Param("errorMessage") String errorMessage);
    int failStaleTask(@Param("id") Long id,@Param("errorMessage") String errorMessage);
    List<Map<String,Object>> taskFacts(@Param("assignmentId") Long assignmentId);
    List<Map<String,Object>> quizFacts(@Param("assignmentId") Long assignmentId);
    List<Map<String,Object>> drillFacts(@Param("assignmentId") Long assignmentId);
    List<Map<String,Object>> reviewFacts(@Param("projectId") Long projectId,@Param("assignmentId") Long assignmentId);
    List<Map<String,Object>> snapshotSources(@Param("snapshotId") Long snapshotId);
}
