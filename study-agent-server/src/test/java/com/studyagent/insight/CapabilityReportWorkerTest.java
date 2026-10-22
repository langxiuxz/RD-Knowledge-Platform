package com.studyagent.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.ai.client.AiClient;
import com.studyagent.insight.entity.CapabilityReportEntity;
import com.studyagent.insight.mapper.CapabilityReportMapper;
import com.studyagent.insight.service.CapabilityReportWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CapabilityReportWorkerTest {
    @Mock CapabilityReportMapper mapper;
    @Mock AiClient ai;
    CapabilityReportWorker worker;
    CapabilityReportEntity report;
    @BeforeEach void setup() {
        worker = new CapabilityReportWorker(mapper,ai,new ObjectMapper());
        report = new CapabilityReportEntity(); report.setId(9L); report.setAssignmentId(42L);
        report.setPathVersion(3); report.setKnowledgeSnapshotId(8L); report.setCurrentTaskId("current");
        report.setStatus("PROCESSING"); report.setOverallDecision("NEEDS_WORK");
        report.setFactsJson("{\"managerDecision\":\"NEEDS_WORK\",\"allowedSources\":[{\"documentVersionId\":901,\"title\":\"Runbook\"}]}");
        when(mapper.find(9L)).thenReturn(report);
    }
    @Test void staleTaskCannotCallAiOrWrite() {
        worker.generate(9L,"old");
        verifyNoInteractions(ai); verify(mapper,never()).fail(anyLong(),anyString());
    }
    @Test void terminalReportCannotBeReopened() {
        report.setStatus("FAILED"); worker.generate(9L,"current");
        verifyNoInteractions(ai); verify(mapper,never()).fail(anyLong(),anyString());
    }
    @Test void summaryUsesFrozenFactsAndNeverChangesDecision() {
        when(ai.summarizeCapability(any())).thenAnswer(call -> {
            AiClient.CapabilitySummaryRequest request = call.getArgument(0);
            assertThat(request.facts()).containsEntry("managerDecision","NEEDS_WORK");
            assertThat(request.knowledgeSnapshotId()).isEqualTo(8L);
            return new AiClient.CapabilitySummary("Summary",List.of("Evidence"),List.of("Risk"),request.allowedSources(),"model");
        });
        when(mapper.complete(eq(9L),eq("current"),anyString(),anyString(),anyString(),anyString(),eq("model"))).thenReturn(1);
        worker.generate(9L,"current");
        verify(mapper).complete(9L,"current","Summary","[\"Evidence\"]","[\"Risk\"]","[{\"documentVersionId\":901,\"title\":\"Runbook\"}]","model");
        assertThat(report.getOverallDecision()).isEqualTo("NEEDS_WORK");
        verify(mapper,never()).fail(anyLong(),anyString());
    }
    @Test void outsideSnapshotRecommendationFailsWithoutCompleting() {
        when(ai.summarizeCapability(any())).thenReturn(new AiClient.CapabilitySummary("Summary",List.of(),List.of(),
            List.of(new AiClient.RecommendedSource(999L,"Unpublished")),"model"));
        worker.generate(9L,"current");
        verify(mapper).fail(9L,"current");
        verify(mapper,never()).complete(anyLong(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void aiFailureUsesTaskGuardedFailureUpdate() {
        when(ai.summarizeCapability(any())).thenThrow(new IllegalStateException("timeout"));
        worker.generate(9L,"current"); verify(mapper).fail(9L,"current");
    }
    @Test void lateAiResultCannotReviveTimedOutReport() {
        when(ai.summarizeCapability(any())).thenReturn(new AiClient.CapabilitySummary("Summary",List.of(),List.of(),List.of(),"model"));
        when(mapper.complete(eq(9L),eq("current"),anyString(),anyString(),anyString(),anyString(),eq("model"))).thenReturn(0);
        worker.generate(9L,"current");
        verify(mapper,never()).fail(anyLong(),anyString());
        verify(mapper,never()).insert(any());
    }
}
