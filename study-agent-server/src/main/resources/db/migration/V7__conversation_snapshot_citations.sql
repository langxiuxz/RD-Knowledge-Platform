ALTER TABLE sa_conversation
 ADD CONSTRAINT fk_conversation_snapshot FOREIGN KEY(knowledge_snapshot_id) REFERENCES sa_knowledge_snapshot(id);
ALTER TABLE sa_message_citation
 ADD COLUMN document_version_id BIGINT UNSIGNED NULL,
 ADD COLUMN knowledge_snapshot_id BIGINT UNSIGNED NULL,
 ADD CONSTRAINT fk_citation_version FOREIGN KEY(document_version_id) REFERENCES sa_document_version(id),
 ADD CONSTRAINT fk_citation_snapshot FOREIGN KEY(knowledge_snapshot_id) REFERENCES sa_knowledge_snapshot(id);
-- Existing citations have unknown version provenance and intentionally remain NULL.
