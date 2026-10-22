package com.studyagent.document.service;
import com.studyagent.document.mapper.DocumentVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class DocumentIndexFailureService {
    private final DocumentVersionMapper versions;
    public DocumentIndexFailureService(DocumentVersionMapper versions) { this.versions=versions; }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void fail(Long id,String taskId) { versions.updateProgress(id,taskId,"FAILED",0,null,"AI服务未接受文档处理任务",null,false); }
}
