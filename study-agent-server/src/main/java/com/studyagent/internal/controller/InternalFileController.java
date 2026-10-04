package com.studyagent.internal.controller;

import com.studyagent.document.service.DocumentService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalFileController {
    private final DocumentService documentService;
    public InternalFileController(DocumentService documentService) { this.documentService = documentService; }

    @GetMapping("/internal/files/{storageKey:.+}")
    public ResponseEntity<Resource> get(@PathVariable String storageKey) {
        Resource resource = documentService.loadRegistered(storageKey);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource.getFilename()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }
}
