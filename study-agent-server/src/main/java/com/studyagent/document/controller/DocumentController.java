package com.studyagent.document.controller;

import com.studyagent.common.result.PageResult;
import com.studyagent.common.result.Result;
import com.studyagent.document.dto.DocumentQuery;
import com.studyagent.document.service.DocumentService;
import com.studyagent.document.vo.DocumentIdVO;
import com.studyagent.document.vo.DocumentStatusVO;
import com.studyagent.document.vo.DocumentTaskVO;
import com.studyagent.document.vo.DocumentVO;
import com.studyagent.security.CurrentUser;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
public class DocumentController {
    private final DocumentService service;
    public DocumentController(DocumentService service) { this.service = service; }

    @PostMapping("/api/knowledge-bases/{id}/documents")
    public ResponseEntity<Result<DocumentIdVO>> upload(@PathVariable @Positive Long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.success(service.upload(CurrentUser.requireId(), id, file)));
    }

    @GetMapping("/api/knowledge-bases/{id}/documents")
    public Result<PageResult<DocumentVO>> list(@PathVariable @Positive Long id,
                                                @RequestParam(required = false) String fileName,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") @Positive Integer page,
                                                @RequestParam(defaultValue = "10") @Positive Integer pageSize) {
        return Result.success(service.list(CurrentUser.requireId(), id, new DocumentQuery(fileName, status, page, pageSize)));
    }

    @GetMapping("/api/documents/{id}/status")
    public Result<DocumentStatusVO> status(@PathVariable @Positive Long id) {
        return Result.success(service.status(CurrentUser.requireId(), id));
    }

    @PostMapping("/api/documents/{id}/reprocess")
    public Result<DocumentTaskVO> reprocess(@PathVariable @Positive Long id) {
        return Result.success(service.reprocess(CurrentUser.requireId(), id));
    }

    @DeleteMapping("/api/documents/{id}")
    public Result<Void> delete(@PathVariable @Positive Long id) {
        service.delete(CurrentUser.requireId(), id); return Result.success();
    }
}
