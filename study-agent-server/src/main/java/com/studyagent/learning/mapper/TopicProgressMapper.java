package com.studyagent.learning.mapper;

import com.studyagent.learning.entity.TopicProgressEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TopicProgressMapper {
    int addStudyMinutes(@Param("userId") Long userId, @Param("topic") String topic, @Param("minutes") int minutes);
    int recordQuizAnswer(@Param("userId") Long userId, @Param("topic") String topic,
                         @Param("correct") int correct, @Param("wrong") int wrong);
    List<TopicProgressEntity> findByUserId(@Param("userId") Long userId);
    List<TopicProgressEntity> findWeakByUserId(@Param("userId") Long userId, @Param("limit") int limit);
}
