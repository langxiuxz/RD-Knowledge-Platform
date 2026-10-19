package com.studyagent.plan.mapper;

import com.studyagent.plan.entity.StudyPlanEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StudyPlanMapper {
    int failStaleTasks(@Param("timeoutSeconds") long timeoutSeconds, @Param("batchSize") int batchSize,
                       @Param("errorMessage") String errorMessage);
    int insert(StudyPlanEntity entity);
    StudyPlanEntity findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
    long countByUserId(@Param("userId") Long userId, @Param("status") String status);
    List<StudyPlanEntity> findPageByUserId(@Param("userId") Long userId, @Param("status") String status,
                                           @Param("offset") int offset, @Param("pageSize") int pageSize);
    int markReady(@Param("id") Long id, @Param("userId") Long userId, @Param("title") String title);
    int markFailed(@Param("id") Long id, @Param("userId") Long userId,
                   @Param("errorMessage") String errorMessage);
    int updateStatus(@Param("id") Long id, @Param("userId") Long userId, @Param("status") String status);
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
