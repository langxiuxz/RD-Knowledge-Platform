package com.studyagent.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import org.springframework.core.io.Resource;

public interface FileStorageService {
    StoredFile store(MultipartFile file) throws IOException;

    Resource load(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;

    record StoredFile(String storageKey, String originalName, String contentType, long size) {
    }
}
