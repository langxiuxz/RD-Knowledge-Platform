package com.studyagent.wrong.mapper;

import com.studyagent.wrong.entity.WrongQuestionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WrongQuestionMapper {
    long countByUserId(@Param("userId") Long userId, @Param("topic") String topic,
                       @Param("mastered") Boolean mastered);
    List<WrongQuestionEntity> findPageByUserId(@Param("userId") Long userId, @Param("topic") String topic,
                                               @Param("mastered") Boolean mastered, @Param("offset") int offset,
                                               @Param("pageSize") int pageSize);
    int upsertWrong(@Param("userId") Long userId, @Param("questionId") Long questionId,
                    @Param("topic") String topic);
    int updateMastered(@Param("id") Long id, @Param("userId") Long userId,
                       @Param("mastered") boolean mastered);
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
