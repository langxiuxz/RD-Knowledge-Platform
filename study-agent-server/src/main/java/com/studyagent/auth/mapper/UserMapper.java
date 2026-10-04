package com.studyagent.auth.mapper;

import com.studyagent.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    @Select("SELECT id, username, password_hash, nickname, email, avatar, status, create_time, update_time "
            + "FROM sa_user WHERE username = #{username}")
    UserEntity findByUsername(@Param("username") String username);

    @Select("SELECT id, username, password_hash, nickname, email, avatar, status, create_time, update_time "
            + "FROM sa_user WHERE id = #{id}")
    UserEntity findById(@Param("id") Long id);

    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(UserEntity user);

    int updateProfile(UserEntity user);

    int updatePassword(@org.apache.ibatis.annotations.Param("id") Long id,
                       @org.apache.ibatis.annotations.Param("passwordHash") String passwordHash);
}
