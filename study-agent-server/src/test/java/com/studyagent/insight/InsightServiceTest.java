package com.studyagent.insight;

import com.studyagent.assessment.service.AssessmentSupport;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.insight.mapper.InsightMapper;
import com.studyagent.insight.service.InsightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {
    @Mock InsightMapper mapper;
    @Mock AssessmentSupport support;
    InsightService service;
    @BeforeEach void setup() { service = new InsightService(mapper, support); }

    @Test void everyProjectionRequiresManagerBeforeReadingFacts() {
        doThrow(new BusinessException(ErrorCode.PROJECT_ROLE_INSUFFICIENT)).when(support).requireManager(7L, 10L);
        List<Runnable> requests = List.of(() -> service.overview(7L,10L), () -> service.assignments(7L,10L),
            () -> service.knowledgeGaps(7L,10L), () -> service.pendingReviews(7L,10L),
            () -> service.contentImpact(7L,10L,null,null,null));
        requests.forEach(request -> assertThatThrownBy(request::run).isInstanceOf(BusinessException.class));
        verifyNoInteractions(mapper);
    }

    @Test void knowledgeGapsAreRatiosOfFactsIncludingShortLowScores() {
        var shortGap = new HashMap<String,Object>(Map.of("knowledgeKey","orders.retry","shortLow",2,"shortTotal",2));
        var quizGap = new HashMap<String,Object>(Map.of("knowledgeKey","orders.cache","quizWrong",1,"quizTotal",3));
        when(mapper.knowledgeGaps(10L)).thenReturn(List.of(shortGap,quizGap));
        when(mapper.knowledgeGapDocuments(10L)).thenReturn(List.of(Map.of("knowledgeKey","orders.retry","documentId",31L)));
        var result = service.knowledgeGaps(7L,10L);
        assertThat(result.get("rows")).isEqualTo(List.of(shortGap,quizGap));
        assertThat(shortGap).containsEntry("severity","HIGH").containsEntry("relatedDocumentIds",List.of(31L))
            .containsEntry("quizWrongRate",0.0).containsEntry("drillMissRate",0.0);
        assertThat(quizGap).containsEntry("quizWrongRate",0.3333).containsEntry("severity","MEDIUM");
    }

    @Test void contentFiltersAreValidatedAndDefaultToNeedsReview() {
        assertThatThrownBy(() -> service.contentImpact(7L,10L,null,null,"INVALID")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.contentImpact(7L,10L,"SQL",null,null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper);
        service.contentImpact(7L,10L,"QUIZ",31L,null);
        verify(mapper).contentImpact(10L,"QUIZ",31L,"NEEDS_REVIEW");
    }

    @Test void overviewUsesActualKnowledgeGapCount() {
        when(mapper.overview(10L)).thenReturn(Map.of("activeAssignments",2));
        when(mapper.knowledgeGaps(10L)).thenReturn(List.of(Map.of("knowledgeKey","orders.retry")));
        assertThat(service.overview(7L,10L)).containsEntry("knowledgeGapCount",1).containsEntry("activeAssignments",2);
    }
}
