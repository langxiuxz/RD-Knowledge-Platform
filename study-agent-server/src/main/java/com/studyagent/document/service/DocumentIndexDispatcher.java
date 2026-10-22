package com.studyagent.document.service;
import com.studyagent.ai.client.AiClient;
import com.studyagent.config.AppProperties;
import com.studyagent.document.entity.DocumentVersionEntity;
import org.springframework.stereotype.Service;
@Service
public class DocumentIndexDispatcher {
    private final AiClient ai; private final AppProperties properties; private final DocumentIndexFailureService failures;
    public DocumentIndexDispatcher(AiClient ai,AppProperties properties,DocumentIndexFailureService failures) {
        this.ai=ai; this.properties=properties; this.failures=failures;
    }
    public void dispatch(DocumentVersionEntity v) {
        try {
            String base=properties.storage().publicBaseUrl();
            var response=ai.index(new AiClient.IndexRequest(v.getProjectId(),v.getKnowledgeBaseId(),v.getDocumentId(),v.getId(),
                    v.getCurrentTaskId(),base+(base.endsWith("/")?"":"/")+v.getStorageKey(),v.getFileType(),v.getFileName(),v.getContentHash()));
            if (response==null || !v.getCurrentTaskId().equals(response.taskId())) throw new IllegalStateException("AI task not accepted");
        } catch (RuntimeException e) { failures.fail(v.getId(),v.getCurrentTaskId()); }
    }
}
