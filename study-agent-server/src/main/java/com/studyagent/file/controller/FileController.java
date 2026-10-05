package com.studyagent.file.controller;

import com.studyagent.common.result.Result;
import com.studyagent.file.service.FileAssetService;
import com.studyagent.file.dto.FileUploadRequest;
import com.studyagent.file.vo.FileUploadVO;
import com.studyagent.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileAssetService service;

    public FileController(FileAssetService service) { this.service = service; }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FileUploadVO> upload(@RequestPart("file") MultipartFile file,
                                       @RequestParam @NotBlank @Pattern(regexp = "AVATAR|IMAGE") String type) {
        return Result.success(service.upload(CurrentUser.requireId(), type, file));
    }

    @GetMapping("/{storageKey:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> get(@PathVariable String storageKey) {
        var file = service.load(CurrentUser.requireId(), storageKey);
        return ResponseEntity.ok()
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .body(file.resource());
    }
}

