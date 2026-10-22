package com.studyagent.insight.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface InsightMapper {
    Map<String,Object> overview(@Param("projectId") Long projectId);
    List<Map<String,Object>> assignments(@Param("projectId") Long projectId);
    List<Map<String,Object>> knowledgeGaps(@Param("projectId") Long projectId);
    List<Map<String,Object>> knowledgeGapDocuments(@Param("projectId") Long projectId);
    List<Map<String,Object>> pendingReviews(@Param("projectId") Long projectId);
    List<Map<String,Object>> contentImpact(@Param("projectId") Long projectId,
        @Param("targetType") String targetType,@Param("documentId") Long documentId,@Param("status") String status);
}
