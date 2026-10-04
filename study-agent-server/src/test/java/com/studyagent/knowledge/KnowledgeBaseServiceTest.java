package com.studyagent.knowledge;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.knowledge.entity.KnowledgeBaseEntity;
import com.studyagent.knowledge.mapper.KnowledgeBaseMapper;
import com.studyagent.knowledge.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {
    @Mock KnowledgeBaseMapper mapper;

    @Test
    void get_whenOwnedByAnotherUser_shouldReject() {
        KnowledgeBaseService service = new KnowledgeBaseService(mapper);
        when(mapper.findByIdAndUserId(12L, 2L)).thenReturn(null);

        assertThatThrownBy(() -> service.get(2L, 12L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
    }

    @Test
    void delete_shouldUseCurrentUserAndResourceIdTogether() {
        KnowledgeBaseService service = new KnowledgeBaseService(mapper);
        KnowledgeBaseEntity owned = new KnowledgeBaseEntity();
        owned.setId(12L);
        owned.setUserId(1L);
        when(mapper.findByIdAndUserId(12L, 1L)).thenReturn(owned);

        service.delete(1L, 12L);

        verify(mapper).deleteByIdAndUserId(12L, 1L);
    }
}
