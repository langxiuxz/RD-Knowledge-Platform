package com.studyagent.auth.mapper;

import com.studyagent.auth.entity.RefreshTokenEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefreshTokenMapper {
    int insert(RefreshTokenEntity token);
    RefreshTokenEntity findByTokenHash(@Param("tokenHash") String tokenHash);
    int revokeByUserAndSession(@Param("userId") Long userId, @Param("sessionId") String sessionId);
    int revokeAllByUserId(@Param("userId") Long userId);
    int markUsed(@Param("id") Long id);
}
