package com.studyagent.quiz.mapper;

import com.studyagent.quiz.entity.QuizAnswerEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuizAnswerMapper {
    int insert(QuizAnswerEntity entity);
    List<QuizAnswerEntity> findByQuizIdAndUserId(@Param("quizId") Long quizId, @Param("userId") Long userId);
}
