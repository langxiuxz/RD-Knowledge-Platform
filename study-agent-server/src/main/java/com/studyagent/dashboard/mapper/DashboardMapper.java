package com.studyagent.dashboard.mapper;

import com.studyagent.dashboard.entity.DailyScoreRow;
import com.studyagent.dashboard.entity.DailyStudyRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DashboardMapper {
    Integer sumStudyMinutes(@Param("userId") Long userId, @Param("begin") LocalDateTime begin);
    Integer countCompletedQuizzes(@Param("userId") Long userId, @Param("begin") LocalDateTime begin);
    BigDecimal averageQuizScore(@Param("userId") Long userId, @Param("begin") LocalDateTime begin);
    List<LocalDate> findStudyDates(@Param("userId") Long userId);
    List<DailyStudyRow> dailyStudy(@Param("userId") Long userId, @Param("begin") LocalDateTime begin,
                                   @Param("end") LocalDateTime end);
    List<DailyScoreRow> dailyQuizScores(@Param("userId") Long userId, @Param("begin") LocalDateTime begin,
                                        @Param("end") LocalDateTime end);
}
