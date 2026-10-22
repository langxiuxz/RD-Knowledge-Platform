package com.studyagent.document.controller;

import com.studyagent.common.result.*;
import com.studyagent.document.dto.*;
import com.studyagent.document.service.DocumentService;
import com.studyagent.document.vo.*;
import com.studyagent.knowledge.service.KnowledgeSnapshotService;
import com.studyagent.knowledge.vo.KnowledgeSnapshotVOs.Publication;
import com.studyagent.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
public class DocumentController {
    private final DocumentService service;
    private final KnowledgeSnapshotService snapshots;
    public DocumentController(DocumentService service, KnowledgeSnapshotService snapshots) {
        this.service = service; this.snapshots = snapshots;
    }
    @PostMapping("/api/knowledge-bases/{id}/documents")
    public ResponseEntity<Result<DocumentIdVO>> upload(@PathVariable @Positive Long id,
            @RequestParam @NotBlank @Size(max=255) String title,
            @RequestParam(required=false) @Size(max=1000) String changeNote, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(Result.success(service.upload(CurrentUser.requireId(), id, title, changeNote, file)));
    }
    @PostMapping("/api/documents/{id}/versions")
    public ResponseEntity<Result<DocumentIdVO>> uploadVersion(@PathVariable @Positive Long id,
            @RequestParam(required=false) @Size(max=1000) String changeNote, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(Result.success(service.uploadVersion(CurrentUser.requireId(), id, changeNote, file)));
    }
    @GetMapping("/api/knowledge-bases/{id}/documents")
    public Result<PageResult<DocumentVO>> list(@PathVariable @Positive Long id,
            @RequestParam(required=false) String keyword, @RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") @Positive Integer page,
            @RequestParam(defaultValue="10") @Min(1) @Max(100) Integer pageSize) {
        return Result.success(service.list(CurrentUser.requireId(), id, new DocumentQuery(keyword,status,page,pageSize)));
    }
    @GetMapping("/api/documents/{id}")
    public Result<DocumentDetailVO> get(@PathVariable @Positive Long id) { return Result.success(service.get(CurrentUser.requireId(),id)); }
    @PostMapping("/api/documents/{id}/archive")
    public Result<Void> archive(@PathVariable @Positive Long id) { service.archive(CurrentUser.requireId(),id);return Result.success(); }
    @GetMapping("/api/document-versions/{id}")
    public Result<DocumentVersionVO> version(@PathVariable @Positive Long id) { return Result.success(service.version(CurrentUser.requireId(),id)); }
    @GetMapping("/api/document-versions/{id}/status")
    public Result<DocumentStatusVO> status(@PathVariable @Positive Long id) { return Result.success(service.status(CurrentUser.requireId(),id)); }
    @PostMapping("/api/document-versions/{id}/reprocess")
    public Result<DocumentTaskVO> reprocess(@PathVariable @Positive Long id) { return Result.success(service.reprocess(CurrentUser.requireId(),id)); }
    @PostMapping("/api/document-versions/{id}/submit-review")
    public Result<Void> submit(@PathVariable @Positive Long id) { service.submitReview(CurrentUser.requireId(),id);return Result.success(); }
    @PostMapping("/api/document-versions/{id}/review")
    public Result<Void> review(@PathVariable @Positive Long id,@Valid @RequestBody ReviewVersionRequest request) {
        service.review(CurrentUser.requireId(),id,request);return Result.success();
    }
    @PostMapping("/api/document-versions/{id}/publish")
    public Result<Publication> publish(@PathVariable @Positive Long id) { return Result.success(snapshots.publish(CurrentUser.requireId(),id)); }
}
