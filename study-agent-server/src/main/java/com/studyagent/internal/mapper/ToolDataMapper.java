package com.studyagent.internal.mapper;

import com.studyagent.internal.entity.ToolDataRow;
import com.studyagent.learning.entity.LearningRecordEntity;
import com.studyagent.plan.entity.StudyPlanEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ToolDataMapper {
    List<ToolDataRow> findLearningProgress(@Param("userId") Long userId, @Param("limit") int limit);
    List<ToolDataRow> findWrongQuestions(@Param("userId") Long userId, @Param("limit") int limit);
    List<ToolDataRow> findWeakTopics(@Param("userId") Long userId, @Param("limit") int limit);
    List<ToolDataRow> findRecentQuizResults(@Param("userId") Long userId, @Param("limit") int limit);
    List<LearningRecordEntity> findRecentLearningForProfile(@Param("userId") Long userId,
                                                            @Param("limit") int limit);
    List<StudyPlanEntity> findActivePlansForProfile(@Param("userId") Long userId,
                                                    @Param("limit") int limit);
}
