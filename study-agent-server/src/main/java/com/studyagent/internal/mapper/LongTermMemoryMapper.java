package com.studyagent.internal.mapper;

import com.studyagent.internal.entity.LongTermMemoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LongTermMemoryMapper {
    List<LongTermMemoryEntity> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);
    int upsert(LongTermMemoryEntity memory);
}
