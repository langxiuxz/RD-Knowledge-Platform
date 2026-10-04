package com.studyagent.document.vo;

import com.studyagent.document.entity.DocumentEntity;

public record DocumentVO(Long id, String fileName, String fileType, Long fileSize, String status, Integer chunkCount) {
    public static DocumentVO from(DocumentEntity e) {
        return new DocumentVO(e.getId(), e.getFileName(), e.getFileType(), e.getFileSize(), e.getStatus(), e.getChunkCount());
    }
}
