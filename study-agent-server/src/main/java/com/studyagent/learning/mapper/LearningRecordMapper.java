package com.studyagent.learning.mapper;

import com.studyagent.learning.entity.LearningRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LearningRecordMapper {
    int insert(LearningRecordEntity entity);
    LearningRecordEntity findByClientRecordId(@Param("userId") Long userId,
                                               @Param("clientRecordId") String clientRecordId);
    long countByUserId(@Param("userId") Long userId, @Param("begin") LocalDateTime begin,
                       @Param("end") LocalDateTime end, @Param("type") String type, @Param("topic") String topic);
    List<LearningRecordEntity> findPageByUserId(@Param("userId") Long userId, @Param("begin") LocalDateTime begin,
                                                @Param("end") LocalDateTime end, @Param("type") String type,
                                                @Param("topic") String topic, @Param("offset") int offset,
                                                @Param("pageSize") int pageSize);
}
