CREATE TABLE sa_long_term_memory (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    category VARCHAR(32) NOT NULL,
    memory_key VARCHAR(64) NOT NULL,
    memory_value VARCHAR(500) NOT NULL,
    source_message_id BIGINT UNSIGNED NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_user_category_key (user_id, category, memory_key),
    KEY idx_memory_user_updated (user_id, update_time),
    CONSTRAINT fk_memory_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_memory_source_message FOREIGN KEY (source_message_id) REFERENCES sa_message(id) ON DELETE SET NULL,
    CONSTRAINT chk_memory_category CHECK (category IN ('LEARNING_GOAL','EXPLANATION_PREFERENCE','LEARNING_STYLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
