package com.studyagent.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.conversation.vo.MessageCitationVO;
import com.studyagent.knowledge.vo.KnowledgeSnapshotVOs.Publication;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeCitationContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void citation_usesExternalSnapshotFieldWithoutInternalAlias() throws Exception {
        var json = mapper.readTree(mapper.writeValueAsString(
                new MessageCitationVO(101L, "chunk-1", "runbook.pdf", 3, 0.9, 1001L, 501L)));
        assertThat(json.path("documentVersionId").asLong()).isEqualTo(1001L);
        assertThat(json.path("snapshotId").asLong()).isEqualTo(501L);
        assertThat(json.has("knowledgeSnapshotId")).isFalse();
    }

    @Test
    void publication_usesExternalSnapshotFieldWithoutInternalAlias() throws Exception {
        var json = mapper.readTree(mapper.writeValueAsString(
                new Publication(101L, 1001L, "PUBLISHED", 501L, 4, null)));
        assertThat(json.path("snapshotId").asLong()).isEqualTo(501L);
        assertThat(json.has("knowledgeSnapshotId")).isFalse();
    }
}
