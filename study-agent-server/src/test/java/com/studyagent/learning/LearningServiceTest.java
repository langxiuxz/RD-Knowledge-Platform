package com.studyagent.learning;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.learning.dto.CreateLearningRecordRequest;
import com.studyagent.learning.entity.LearningRecordEntity;
import com.studyagent.learning.mapper.LearningRecordMapper;
import com.studyagent.learning.mapper.TopicProgressMapper;
import com.studyagent.learning.service.LearningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearningServiceTest {
    private static final String KEY = "d86efc94-2975-4b88-a756-0b4c35c850a6";
    private static final OffsetDateTime COMPLETED = OffsetDateTime.parse("2026-10-18T10:50:00+08:00");
    @Mock LearningRecordMapper records;
    @Mock TopicProgressMapper progress;
    LearningService service;

    @BeforeEach
    void setUp() { service = new LearningService(records, progress); }

    private CreateLearningRecordRequest focus() {
        return new CreateLearningRecordRequest("FOCUS", "Java", 50, null, "Read streams", KEY, COMPLETED);
    }

    @Test
    void create_focus_shouldSaveCompletionDateAndUpdateTopicMinutes() {
        when(records.insert(any())).thenAnswer(call -> {
            LearningRecordEntity record = call.getArgument(0);
            assertThat(record.getUserId()).isEqualTo(7L);
            assertThat(record.getClientRecordId()).isEqualTo(KEY);
            assertThat(record.getRemark()).isEqualTo("Read streams");
            assertThat(record.getCreateTime()).isEqualTo(COMPLETED.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
            record.setId(100L);
            return 1;
        });
        assertThat(service.create(7L, focus()).id()).isEqualTo(100L);
        verify(progress).addStudyMinutes(7L, "Java", 50);
    }

    @Test
    void create_retriedFocus_shouldReturnOriginalIdWithoutAddingMinutes() {
        var saved = saved();
        when(records.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(records.findByClientRecordId(7L, KEY)).thenReturn(saved);
        assertThat(service.create(7L, focus()).id()).isEqualTo(100L);
        verify(records).findByClientRecordId(7L, KEY);
        verifyNoInteractions(progress);
    }

    @Test
    void create_sameKeyWithDifferentPayload_shouldReject() {
        var saved = saved();
        saved.setDurationMinutes(25);
        when(records.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(records.findByClientRecordId(7L, KEY)).thenReturn(saved);
        assertThatThrownBy(() -> service.create(7L, focus())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(progress);
    }

    @Test
    void create_sameKeyForAnotherUser_shouldNotReadOrReturnOtherUsersRecord() {
        when(records.insert(any())).thenAnswer(call -> {
            LearningRecordEntity record = call.getArgument(0);
            assertThat(record.getUserId()).isEqualTo(8L);
            record.setId(101L);
            return 1;
        });
        assertThat(service.create(8L, focus()).id()).isEqualTo(101L);
        verify(records, never()).findByClientRecordId(anyLong(), anyString());
        verify(progress).addStudyMinutes(8L, "Java", 50);
    }

    @Test
    void create_focusWithoutRequiredFieldsOrWithFutureDate_shouldRejectBeforeWriting() {
        var requests = new CreateLearningRecordRequest[] {
            new CreateLearningRecordRequest("FOCUS", "Java", 0, null, null, KEY, COMPLETED),
            new CreateLearningRecordRequest("FOCUS", "Java", 50, null, null, null, COMPLETED),
            new CreateLearningRecordRequest("FOCUS", "Java", 50, null, null, KEY, null),
            new CreateLearningRecordRequest("FOCUS", "Java", 50, 3L, null, KEY, COMPLETED),
            new CreateLearningRecordRequest("FOCUS", "Java", 50, null, null, KEY, OffsetDateTime.now().plusDays(1)),
            new CreateLearningRecordRequest("REST", "Java", 10, null, null, null, null),
            new CreateLearningRecordRequest("READ", "Java", 10, null, null, KEY, COMPLETED)
        };
        for (var request : requests) {
            assertThatThrownBy(() -> service.create(7L, request)).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(records, progress);
    }

    @Test
    void create_legacyRecord_shouldRemainCompatible() {
        service.create(7L, new CreateLearningRecordRequest("READ", "Java", null, 3L, null, null, null));
        verify(records).insert(argThat(r -> r.getCreateTime() == null && r.getClientRecordId() == null));
        verify(progress).addStudyMinutes(7L, "Java", 0);
    }

    private LearningRecordEntity saved() {
        var record = new LearningRecordEntity();
        record.setId(100L);
        record.setUserId(7L);
        record.setType("FOCUS");
        record.setTopic("Java");
        record.setDurationMinutes(50);
        record.setRemark("Read streams");
        record.setClientRecordId(KEY);
        record.setCreateTime(COMPLETED.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
        return record;
    }
}
