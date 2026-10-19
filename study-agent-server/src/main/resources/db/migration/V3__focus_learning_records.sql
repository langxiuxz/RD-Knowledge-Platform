-- Existing rows remain valid. NULL client IDs preserve legacy inserts.
ALTER TABLE sa_learning_record
    DROP CHECK chk_learning_type,
    ADD CONSTRAINT chk_learning_type CHECK (type IN ('READ','QUIZ','REVIEW','CHAT','FOCUS')),
    ADD COLUMN client_record_id VARCHAR(36) NULL,
    ADD UNIQUE KEY uk_learning_user_client (user_id, client_record_id);
