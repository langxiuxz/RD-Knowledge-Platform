CREATE TABLE sa_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    email VARCHAR(128) NULL,
    avatar VARCHAR(512) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_email (email),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_refresh_token (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    session_id CHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_session (session_id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_user_expire (user_id, expires_at),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_knowledge_base (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(500) NULL,
    embedding_model VARCHAR(128) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'READY',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kb_user_status_time (user_id, status, create_time),
    KEY idx_kb_user_name (user_id, name),
    CONSTRAINT chk_kb_status CHECK (status IN ('PROCESSING','READY','FAILED')),
    CONSTRAINT fk_kb_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_document (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    knowledge_base_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    mime_type VARCHAR(128) NULL,
    file_size BIGINT UNSIGNED NOT NULL DEFAULT 0,
    storage_key VARCHAR(512) NOT NULL,
    file_url VARCHAR(1024) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'UPLOADED',
    progress TINYINT UNSIGNED NOT NULL DEFAULT 0,
    chunk_count INT UNSIGNED NOT NULL DEFAULT 0,
    current_task_id VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    processed_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_doc_kb_status_time (knowledge_base_id, status, create_time),
    KEY idx_doc_user_status (user_id, status),
    KEY idx_doc_user_filename (user_id, file_name),
    KEY idx_doc_task (current_task_id),
    CONSTRAINT chk_doc_status CHECK (status IN ('UPLOADED','PARSING','CHUNKING','EMBEDDING','READY','FAILED')),
    CONSTRAINT chk_doc_progress CHECK (progress <= 100),
    CONSTRAINT fk_doc_kb FOREIGN KEY (knowledge_base_id) REFERENCES sa_knowledge_base(id) ON DELETE CASCADE,
    CONSTRAINT fk_doc_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_conversation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    knowledge_base_id BIGINT UNSIGNED NULL,
    title VARCHAR(128) NULL,
    mode VARCHAR(16) NOT NULL DEFAULT 'CHAT',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conv_user_mode_time (user_id, mode, update_time),
    KEY idx_conv_kb (knowledge_base_id),
    CONSTRAINT chk_conv_mode CHECK (mode IN ('CHAT','RAG','AGENT')),
    CONSTRAINT fk_conv_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_conv_kb FOREIGN KEY (knowledge_base_id) REFERENCES sa_knowledge_base(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    role VARCHAR(16) NOT NULL,
    content LONGTEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
    trace_id VARCHAR(64) NULL,
    finish_reason VARCHAR(32) NULL,
    error_message VARCHAR(1000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_msg_conv_id (conversation_id, id),
    KEY idx_msg_user_time (user_id, create_time),
    KEY idx_msg_trace (trace_id),
    CONSTRAINT chk_msg_role CHECK (role IN ('user','assistant','system','tool')),
    CONSTRAINT chk_msg_status CHECK (status IN ('GENERATING','COMPLETED','CANCELLED','FAILED')),
    CONSTRAINT fk_msg_conv FOREIGN KEY (conversation_id) REFERENCES sa_conversation(id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_message_citation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_id BIGINT UNSIGNED NOT NULL,
    document_id BIGINT UNSIGNED NULL,
    chunk_id VARCHAR(128) NULL,
    file_name VARCHAR(255) NOT NULL,
    page INT UNSIGNED NULL,
    score DECIMAL(8,6) NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_citation_message_order (message_id, sort_order),
    KEY idx_citation_document (document_id),
    CONSTRAINT fk_citation_message FOREIGN KEY (message_id) REFERENCES sa_message(id) ON DELETE CASCADE,
    CONSTRAINT fk_citation_document FOREIGN KEY (document_id) REFERENCES sa_document(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_message_feedback (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    rating TINYINT NOT NULL,
    reason VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feedback_user_message (user_id, message_id),
    KEY idx_feedback_message (message_id),
    CONSTRAINT chk_feedback_rating CHECK (rating IN (-1, 1)),
    CONSTRAINT fk_feedback_message FOREIGN KEY (message_id) REFERENCES sa_message(id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_quiz (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    knowledge_base_id BIGINT UNSIGNED NULL,
    title VARCHAR(128) NOT NULL,
    topic VARCHAR(255) NULL,
    difficulty VARCHAR(16) NOT NULL,
    question_count INT UNSIGNED NOT NULL,
    types_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    score DECIMAL(5,2) NULL,
    correct_count INT UNSIGNED NULL,
    wrong_count INT UNSIGNED NULL,
    analysis TEXT NULL,
    error_message VARCHAR(1000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    submitted_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_quiz_user_status_time (user_id, status, create_time),
    KEY idx_quiz_kb (knowledge_base_id),
    CONSTRAINT chk_quiz_difficulty CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    CONSTRAINT chk_quiz_status CHECK (status IN ('PROCESSING','READY','FAILED','COMPLETED')),
    CONSTRAINT chk_quiz_count CHECK (question_count BETWEEN 1 AND 50),
    CONSTRAINT fk_quiz_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_kb FOREIGN KEY (knowledge_base_id) REFERENCES sa_knowledge_base(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_quiz_question (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quiz_id BIGINT UNSIGNED NOT NULL,
    topic VARCHAR(128) NULL,
    type VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    options_json JSON NULL,
    correct_answer_json JSON NOT NULL,
    explanation TEXT NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_question_quiz_order (quiz_id, sort_order),
    KEY idx_question_topic (topic),
    CONSTRAINT chk_question_type CHECK (type IN ('SINGLE','MULTIPLE','JUDGE','SHORT')),
    CONSTRAINT fk_question_quiz FOREIGN KEY (quiz_id) REFERENCES sa_quiz(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_quiz_answer (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quiz_id BIGINT UNSIGNED NOT NULL,
    question_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    user_answer_json JSON NOT NULL,
    is_correct TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_answer_quiz_question_user (quiz_id, question_id, user_id),
    KEY idx_answer_user_time (user_id, create_time),
    KEY idx_answer_question (question_id),
    CONSTRAINT fk_answer_quiz FOREIGN KEY (quiz_id) REFERENCES sa_quiz(id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES sa_quiz_question(id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_wrong_question (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    question_id BIGINT UNSIGNED NOT NULL,
    topic VARCHAR(128) NOT NULL,
    wrong_count INT UNSIGNED NOT NULL DEFAULT 1,
    mastered TINYINT(1) NOT NULL DEFAULT 0,
    first_wrong_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_wrong_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wrong_user_question (user_id, question_id),
    KEY idx_wrong_user_mastered_topic (user_id, mastered, topic),
    KEY idx_wrong_user_count (user_id, wrong_count),
    CONSTRAINT fk_wrong_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_wrong_question FOREIGN KEY (question_id) REFERENCES sa_quiz_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_learning_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(16) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    duration_minutes INT UNSIGNED NOT NULL DEFAULT 0,
    source_id BIGINT UNSIGNED NULL,
    remark VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_learning_user_time (user_id, create_time),
    KEY idx_learning_user_type_time (user_id, type, create_time),
    KEY idx_learning_user_topic_time (user_id, topic, create_time),
    CONSTRAINT chk_learning_type CHECK (type IN ('READ','QUIZ','REVIEW','CHAT')),
    CONSTRAINT fk_learning_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_topic_progress (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    topic VARCHAR(128) NOT NULL,
    mastery DECIMAL(5,2) NOT NULL DEFAULT 0,
    quiz_accuracy DECIMAL(5,2) NOT NULL DEFAULT 0,
    study_minutes INT UNSIGNED NOT NULL DEFAULT 0,
    wrong_count INT UNSIGNED NOT NULL DEFAULT 0,
    quiz_question_count INT UNSIGNED NOT NULL DEFAULT 0,
    correct_question_count INT UNSIGNED NOT NULL DEFAULT 0,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_progress_user_topic (user_id, topic),
    KEY idx_progress_user_mastery (user_id, mastery),
    KEY idx_progress_user_wrong (user_id, wrong_count),
    CONSTRAINT chk_progress_mastery CHECK (mastery BETWEEN 0 AND 100),
    CONSTRAINT chk_progress_accuracy CHECK (quiz_accuracy BETWEEN 0 AND 100),
    CONSTRAINT fk_progress_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_study_plan (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(128) NOT NULL,
    goal VARCHAR(500) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    daily_minutes INT UNSIGNED NOT NULL,
    focus_topics_json JSON NULL,
    generation_status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    error_message VARCHAR(1000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_plan_user_status_time (user_id, status, create_time),
    KEY idx_plan_user_generation (user_id, generation_status),
    CONSTRAINT chk_plan_generation CHECK (generation_status IN ('PROCESSING','READY','FAILED')),
    CONSTRAINT chk_plan_status CHECK (status IN ('ACTIVE','COMPLETED','ARCHIVED')),
    CONSTRAINT chk_plan_dates CHECK (end_date >= start_date),
    CONSTRAINT fk_plan_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_study_plan_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    task_date DATE NOT NULL,
    topic VARCHAR(128) NOT NULL,
    type VARCHAR(16) NOT NULL,
    estimated_minutes INT UNSIGNED NOT NULL DEFAULT 0,
    completed TINYINT(1) NOT NULL DEFAULT 0,
    completed_at DATETIME NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task_plan_date_order (plan_id, task_date, sort_order),
    KEY idx_task_user_date (user_id, task_date),
    CONSTRAINT fk_task_plan FOREIGN KEY (plan_id) REFERENCES sa_study_plan(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_file_asset (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(16) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    mime_type VARCHAR(128) NULL,
    file_size BIGINT UNSIGNED NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_storage_key (storage_key),
    KEY idx_file_user_type_time (user_id, type, create_time),
    CONSTRAINT chk_file_type CHECK (type IN ('AVATAR','IMAGE')),
    CONSTRAINT fk_file_user FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
