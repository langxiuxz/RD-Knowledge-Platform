package com.studyagent.quiz.mapper;

import com.studyagent.quiz.entity.QuizEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface QuizMapper {
    int failStaleTasks(@Param("timeoutSeconds") long timeoutSeconds, @Param("batchSize") int batchSize,
                       @Param("errorMessage") String errorMessage);
    int insert(QuizEntity entity);
    QuizEntity findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
    long countByUserId(@Param("userId") Long userId, @Param("keyword") String keyword, @Param("status") String status);
    List<QuizEntity> findPageByUserId(@Param("userId") Long userId, @Param("keyword") String keyword,
                                      @Param("status") String status, @Param("offset") int offset,
                                      @Param("pageSize") int pageSize);
    int markReady(@Param("id") Long id, @Param("userId") Long userId);
    int markFailed(@Param("id") Long id, @Param("userId") Long userId, @Param("errorMessage") String errorMessage);
    int markCompleted(@Param("id") Long id, @Param("userId") Long userId, @Param("score") BigDecimal score,
                      @Param("correctCount") int correctCount, @Param("wrongCount") int wrongCount,
                      @Param("analysis") String analysis);
}
