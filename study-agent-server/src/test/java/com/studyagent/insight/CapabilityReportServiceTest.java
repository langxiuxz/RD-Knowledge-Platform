package com.studyagent.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.assessment.service.AssessmentSupport;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.insight.entity.CapabilityReportEntity;
import com.studyagent.insight.mapper.CapabilityReportMapper;
import com.studyagent.insight.service.CapabilityReportService;
import com.studyagent.insight.service.CapabilityReportWorker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CapabilityReportServiceTest {
    @Mock CapabilityReportMapper mapper;
    @Mock AssessmentSupport support;
    @Mock CapabilityReportWorker worker;
    final ObjectMapper json = new ObjectMapper();
    CapabilityReportService service;
    @BeforeEach void setup() { service = new CapabilityReportService(mapper,support,worker,json); }
    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    Map<String,Object> assignment(String status) {
        return Map.of("assignmentId",42L,"projectId",10L,"userId",8L,"knowledgeSnapshotId",55L,"pathVersion",3,"decisionStatus",status);
    }
    @Test void generationRequiresProjectManagerBeforeLockingFacts() {
        when(mapper.assignmentFacts(42L)).thenReturn(assignment("PASSED"));
        doThrow(new BusinessException(ErrorCode.PROJECT_ROLE_INSUFFICIENT)).when(support).lock(7L,10L,true);
        assertThatThrownBy(() -> service.generate(7L,42L)).isInstanceOf(BusinessException.class);
        verify(mapper,never()).assignmentFactsForUpdate(anyLong()); verifyNoInteractions(worker);
    }
    @Test void unsubmittedAssignmentCannotGenerateReport() {
        when(mapper.assignmentFacts(42L)).thenReturn(assignment("IN_PROGRESS"));
        when(mapper.assignmentFactsForUpdate(42L)).thenReturn(assignment("IN_PROGRESS"));
        assertThatThrownBy(() -> service.generate(7L,42L)).isInstanceOf(BusinessException.class);
        verify(mapper,never()).insert(any()); verifyNoInteractions(worker);
    }
    @Test void fixedFactsAreReusedAndAiDispatchesOnlyAfterCommit() throws Exception {
        var facts = assignment("PASSED");
        when(mapper.assignmentFacts(42L)).thenReturn(facts);
        when(mapper.assignmentFactsForUpdate(42L)).thenReturn(facts);
        when(mapper.taskFacts(42L)).thenReturn(List.of(Map.of("required",1,"status","COMPLETED","title","Read")));
        when(mapper.quizFacts(42L)).thenReturn(List.of(Map.of("attemptId",90L,"autoScore",70,"finalScore",80,"status","COMPLETED","wrongCount",1)));
        when(mapper.snapshotSources(55L)).thenReturn(List.of(Map.of("documentVersionId",901L,"title","Runbook")));
        when(mapper.nextVersion(42L)).thenReturn(1);
        var captured = new ArrayList<CapabilityReportEntity>();
        when(mapper.insert(any())).thenAnswer(call -> {
            CapabilityReportEntity row=call.getArgument(0); row.setId(9L); captured.add(row); return 1;
        });
        TransactionSynchronizationManager.initSynchronization();
        assertThat(service.generate(7L,42L)).containsEntry("reportId",9L).containsEntry("overallDecision","PASS");
        verifyNoInteractions(worker);
        var row=captured.get(0);
        assertThat(json.readTree(row.getFactsJson()).get("managerDecision").asText()).isEqualTo("PASS");
        assertThat(json.readTree(row.getDimensionsJson()).get("items").get(0).get("automaticScore").asInt()).isEqualTo(70);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(worker).generate(9L,row.getCurrentTaskId());
        when(mapper.latestForAssignment(42L)).thenReturn(row);
        service.generate(7L,42L);
        verify(mapper,times(1)).insert(any()); verify(mapper,times(1)).nextVersion(42L);
    }
    @Test void learnerCanOnlyReadOwnReportAndMembershipIsAlwaysRequired() {
        var row=new CapabilityReportEntity(); row.setId(9L); row.setProjectId(10L); row.setUserId(8L);
        when(mapper.find(9L)).thenReturn(row);
        when(support.memberRole(7L,10L)).thenReturn("LEARNER");
        assertThatThrownBy(() -> service.get(7L,9L)).isInstanceOf(BusinessException.class);
        when(support.memberRole(8L,10L)).thenReturn("LEARNER");
        assertThat(service.get(8L,9L)).containsEntry("id",9L);
        when(support.memberRole(7L,10L)).thenReturn("MAINTAINER");
        assertThat(service.get(7L,9L)).containsEntry("id",9L);
        when(support.memberRole(7L,10L)).thenThrow(new BusinessException(ErrorCode.PROJECT_MEMBER_REQUIRED));
        assertThatThrownBy(() -> service.get(7L,9L)).isInstanceOf(BusinessException.class);
    }
    @Test void missingReportUsesDedicatedContractCode() {
        assertThatThrownBy(() -> service.get(7L,404L)).isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException)error).errorCode()).isEqualTo(ErrorCode.CAPABILITY_REPORT_NOT_FOUND));
        verifyNoInteractions(support);
    }
}
