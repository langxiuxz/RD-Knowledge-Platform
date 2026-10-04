package com.studyagent.document.vo;

import com.studyagent.document.entity.DocumentEntity;

public record DocumentStatusVO(String status, Integer progress, String errorMessage) {
    public static DocumentStatusVO from(DocumentEntity e) {
        return new DocumentStatusVO(e.getStatus(), e.getProgress(), e.getErrorMessage());
    }
}
