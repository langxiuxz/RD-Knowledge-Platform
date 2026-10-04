package com.studyagent.plan.mapper;

import com.studyagent.plan.entity.StudyPlanTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StudyPlanTaskMapper {
    int insert(StudyPlanTaskEntity entity);
    List<StudyPlanTaskEntity> findByPlanIdAndUserId(@Param("planId") Long planId,
                                                    @Param("userId") Long userId);
    int updateCompleted(@Param("planId") Long planId, @Param("taskId") Long taskId,
                        @Param("userId") Long userId, @Param("completed") boolean completed);
    int countIncomplete(@Param("planId") Long planId, @Param("userId") Long userId);
}
