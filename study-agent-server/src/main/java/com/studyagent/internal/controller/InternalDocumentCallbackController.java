package com.studyagent.internal.controller;

import com.studyagent.common.result.Result;
import com.studyagent.document.dto.DocumentProgressRequest;
import com.studyagent.document.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class InternalDocumentCallbackController {
    private final DocumentService documentService;
    public InternalDocumentCallbackController(DocumentService documentService) { this.documentService = documentService; }

    @PostMapping("/internal/callbacks/documents/{documentId}/progress")
    public Result<Void> progress(@PathVariable Long documentId, @Valid @RequestBody DocumentProgressRequest request) {
        documentService.progress(documentId, request); return Result.success();
    }
}
