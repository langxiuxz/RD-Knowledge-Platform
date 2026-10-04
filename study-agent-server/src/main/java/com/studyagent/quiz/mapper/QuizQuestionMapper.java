package com.studyagent.quiz.mapper;

import com.studyagent.quiz.entity.QuizQuestionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuizQuestionMapper {
    int insert(QuizQuestionEntity entity);
    List<QuizQuestionEntity> findByQuizIdAndUserId(@Param("quizId") Long quizId, @Param("userId") Long userId);
}
