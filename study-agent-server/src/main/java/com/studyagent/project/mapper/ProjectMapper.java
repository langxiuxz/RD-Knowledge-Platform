package com.studyagent.project.mapper;

import com.studyagent.project.dto.ProjectQueries;
import com.studyagent.project.entity.ProjectEntity;
import com.studyagent.project.entity.ProjectMemberEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProjectMapper {
    int insert(ProjectEntity project);
    boolean codeExists(@Param("code") String code, @Param("excludeProjectId") Long excludeProjectId);
    ProjectEntity findById(@Param("projectId") Long projectId);
    ProjectEntity lockById(@Param("projectId") Long projectId);
    ProjectEntity findAccessible(@Param("projectId") Long projectId, @Param("userId") Long userId);
    int update(ProjectEntity project);
    int archive(@Param("projectId") Long projectId);
    long countProjects(@Param("userId") Long userId, @Param("query") ProjectQueries.Projects query);
    List<ProjectEntity> findProjects(@Param("userId") Long userId, @Param("query") ProjectQueries.Projects query,
                                     @Param("offset") long offset);
    long countMembers(@Param("projectId") Long projectId, @Param("query") ProjectQueries.Members query);
    List<ProjectMemberEntity> findMembers(@Param("projectId") Long projectId, @Param("query") ProjectQueries.Members query,
                                          @Param("offset") long offset);
    ProjectMemberEntity lockMember(@Param("projectId") Long projectId, @Param("userId") Long userId);
    long countActiveOwners(@Param("projectId") Long projectId);
    int insertMember(@Param("projectId") Long projectId, @Param("userId") Long userId, @Param("role") String role);
    int reactivateMember(@Param("projectId") Long projectId, @Param("userId") Long userId, @Param("role") String role);
    int updateMemberRole(@Param("projectId") Long projectId, @Param("userId") Long userId, @Param("role") String role);
    int removeMember(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
