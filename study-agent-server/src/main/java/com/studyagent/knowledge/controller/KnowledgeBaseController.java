package com.studyagent.knowledge.controller;
import com.studyagent.common.result.PageResult;import com.studyagent.common.result.Result;
import com.studyagent.knowledge.dto.*;import com.studyagent.knowledge.service.KnowledgeBaseService;import com.studyagent.knowledge.service.KnowledgeSnapshotService;
import com.studyagent.knowledge.vo.*;import com.studyagent.security.CurrentUser;import jakarta.validation.Valid;import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController public class KnowledgeBaseController {
 private final KnowledgeBaseService service;private final KnowledgeSnapshotService snapshots;public KnowledgeBaseController(KnowledgeBaseService s,KnowledgeSnapshotService snapshots){this.service=s;this.snapshots=snapshots;}
 @GetMapping("/api/projects/{projectId}/knowledge-bases") public Result<PageResult<KnowledgeBaseVO>> list(@PathVariable @Positive Long projectId,@RequestParam(required=false)String name,@RequestParam(required=false)String status,@RequestParam(defaultValue="1")@Positive Integer page,@RequestParam(defaultValue="10")@Positive Integer pageSize){return Result.success(service.list(CurrentUser.requireId(),projectId,new KnowledgeBaseQuery(name,status,page,pageSize)));}
 @PostMapping("/api/projects/{projectId}/knowledge-bases") public ResponseEntity<Result<KnowledgeBaseIdVO>> create(@PathVariable @Positive Long projectId,@Valid @RequestBody CreateKnowledgeBaseRequest req){return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(service.create(CurrentUser.requireId(),projectId,req)));}
 @GetMapping("/api/knowledge-bases/{id}") public Result<KnowledgeBaseVO> get(@PathVariable @Positive Long id){return Result.success(service.get(CurrentUser.requireId(),id));}
 @PutMapping("/api/knowledge-bases/{id}") public Result<Void> update(@PathVariable @Positive Long id,@Valid @RequestBody UpdateKnowledgeBaseRequest req){service.update(CurrentUser.requireId(),id,req);return Result.success();}
 @PostMapping("/api/knowledge-bases/{id}/archive") public Result<Void> archive(@PathVariable @Positive Long id){service.archive(CurrentUser.requireId(),id);return Result.success();}
 @GetMapping("/api/knowledge-bases/{id}/snapshots") public Result<PageResult<KnowledgeSnapshotVOs.Summary>> snapshots(@PathVariable @Positive Long id,@RequestParam(defaultValue="1")@Positive Integer page,@RequestParam(defaultValue="10")@jakarta.validation.constraints.Min(1)@jakarta.validation.constraints.Max(100) Integer pageSize){return Result.success(snapshots.list(CurrentUser.requireId(),id,page,pageSize));}
 @GetMapping("/api/knowledge-snapshots/{id}") public Result<KnowledgeSnapshotVOs.Detail> snapshot(@PathVariable @Positive Long id){return Result.success(snapshots.get(CurrentUser.requireId(),id));}
}
