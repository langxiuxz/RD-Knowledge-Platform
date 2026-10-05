package com.studyagent.file.entity;

import java.time.LocalDateTime;

public class FileAssetEntity {
    private Long id; private Long userId; private String type; private String originalName;
    private String storageKey; private String url; private String mimeType; private Long fileSize; private LocalDateTime createTime;
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getUserId() { return userId; } public void setUserId(Long v) { userId = v; }
    public String getType() { return type; } public void setType(String v) { type = v; }
    public String getOriginalName() { return originalName; } public void setOriginalName(String v) { originalName = v; }
    public String getStorageKey() { return storageKey; } public void setStorageKey(String v) { storageKey = v; }
    public String getUrl() { return url; } public void setUrl(String v) { url = v; }
    public String getMimeType() { return mimeType; } public void setMimeType(String v) { mimeType = v; }
    public Long getFileSize() { return fileSize; } public void setFileSize(Long v) { fileSize = v; }
    public LocalDateTime getCreateTime() { return createTime; } public void setCreateTime(LocalDateTime v) { createTime = v; }
}
