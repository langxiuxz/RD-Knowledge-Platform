package com.studyagent.knowledge;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.knowledge.entity.KnowledgeBaseEntity;
import com.studyagent.knowledge.mapper.KnowledgeBaseMapper;
import com.studyagent.knowledge.mapper.KnowledgeSnapshotMapper;
import com.studyagent.knowledge.service.KnowledgeBaseService;
import com.studyagent.project.service.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {
    @Mock KnowledgeBaseMapper mapper;
    @Mock KnowledgeSnapshotMapper snapshots;
    @Mock ProjectAccessService access;

    @Test
    void get_missingKnowledgeBase_shouldNotQueryProjectMembership() {
        var service = new KnowledgeBaseService(mapper, snapshots, access);
        when(mapper.findById(12L)).thenReturn(null);

        assertThatThrownBy(() -> service.get(2L, 12L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        verifyNoInteractions(access);
    }

    @Test
    void get_checksProjectMembershipResolvedFromKnowledgeBase() {
        var service = new KnowledgeBaseService(mapper, snapshots, access);
        var base = new KnowledgeBaseEntity();
        base.setId(12L);
        base.setProjectId(7L);
        base.setName("Team Knowledge");
        base.setStatus("ACTIVE");
        when(mapper.findById(12L)).thenReturn(base);
        when(access.requireMember(2L, 7L)).thenThrow(new BusinessException(ErrorCode.PROJECT_NOT_MEMBER));

        assertThatThrownBy(() -> service.get(2L, 12L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.PROJECT_NOT_MEMBER);
    }
}
