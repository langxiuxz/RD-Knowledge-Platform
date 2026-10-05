package com.studyagent.file.mapper;

import com.studyagent.file.entity.FileAssetEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileAssetMapper {
    int insert(FileAssetEntity entity);
    FileAssetEntity findByStorageKeyAndUser(@Param("storageKey") String storageKey, @Param("userId") Long userId);
}
