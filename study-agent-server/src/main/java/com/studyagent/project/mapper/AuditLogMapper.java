package com.studyagent.project.mapper;

import com.studyagent.project.dto.ProjectQueries;
import com.studyagent.project.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AuditLogMapper {
    int insert(AuditLogEntity log);
    long count(@Param("projectId") Long projectId, @Param("query") ProjectQueries.Audit query);
    List<AuditLogEntity> findPage(@Param("projectId") Long projectId, @Param("query") ProjectQueries.Audit query,
                                  @Param("offset") long offset);
}
