package com.studyagent.internal;

import com.studyagent.internal.entity.ToolDataRow;
import com.studyagent.internal.mapper.ToolDataMapper;
import com.studyagent.internal.service.InternalToolService;
import com.studyagent.internal.vo.LearnerProfileToolVO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalToolServiceTest {
    private final ToolDataMapper mapper = mock(ToolDataMapper.class);
    private final InternalToolService service = new InternalToolService(mapper, JsonMapper.builder().build());

    @Test
    void learnerProfileWithNoBusinessDataIsEmptyAndDoesNotInventFacts() {
        when(mapper.findLearningProgress(7L, 50)).thenReturn(List.of());
        when(mapper.findRecentQuizResults(7L, 10)).thenReturn(List.of());
        when(mapper.findRecentLearningForProfile(7L, 10)).thenReturn(List.of());
        when(mapper.findActivePlansForProfile(7L, 5)).thenReturn(List.of());

        LearnerProfileToolVO profile = service.learnerProfile(7L);

        assertThat(profile.hasData()).isFalse();
        assertThat(profile.currentGoals()).isEmpty();
        assertThat(profile.topics()).isEmpty();
        assertThat(profile.weakPoints()).isEmpty();
        assertThat(profile.strengths()).isEmpty();
        assertThat(profile.quizTrend()).isNull();
    }

    @Test
    void learnerProfileRanksWeakTopicsFromRealProgressRows() {
        ToolDataRow spring = row("Spring", "45.00", 4);
        ToolDataRow java = row("Java", "82.00", 0);
        when(mapper.findLearningProgress(7L, 50)).thenReturn(List.of(java, spring));
        when(mapper.findRecentQuizResults(7L, 10)).thenReturn(List.of());
        when(mapper.findRecentLearningForProfile(7L, 10)).thenReturn(List.of());
        when(mapper.findActivePlansForProfile(7L, 5)).thenReturn(List.of());

        LearnerProfileToolVO profile = service.learnerProfile(7L);

        assertThat(profile.hasData()).isTrue();
        assertThat(profile.weakPoints()).extracting(LearnerProfileToolVO.Topic::topic).contains("Spring");
        assertThat(profile.topics()).extracting(LearnerProfileToolVO.Topic::mastery)
                .contains(BigDecimal.valueOf(45), BigDecimal.valueOf(82));
    }

    private ToolDataRow row(String topic, String mastery, int wrongCount) {
        ToolDataRow row = new ToolDataRow();
        row.setTopic(topic);
        row.setMastery(new BigDecimal(mastery));
        row.setQuizAccuracy(new BigDecimal("70.00"));
        row.setStudyMinutes(20);
        row.setWrongCount(wrongCount);
        row.setQuizQuestionCount(5);
        return row;
    }
}
