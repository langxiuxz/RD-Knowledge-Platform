ALTER TABLE sa_quiz
 ADD COLUMN project_id BIGINT UNSIGNED NULL,
 ADD COLUMN knowledge_snapshot_id BIGINT UNSIGNED NULL,
 ADD COLUMN revision_of_quiz_id BIGINT UNSIGNED NULL,
 ADD COLUMN version_no INT UNSIGNED NOT NULL DEFAULT 1,
 ADD COLUMN rubric_version INT UNSIGNED NOT NULL DEFAULT 1,
 ADD COLUMN rubric_json JSON NULL,
 ADD COLUMN pass_score DECIMAL(5,2) NOT NULL DEFAULT 60,
 ADD COLUMN current_task_id VARCHAR(64) NULL,
 ADD COLUMN created_by BIGINT UNSIGNED NULL,
 ADD COLUMN published_at DATETIME NULL,
 ADD KEY idx_quiz_project_status_time(project_id,status,update_time),
 ADD CONSTRAINT fk_quiz_project FOREIGN KEY(project_id) REFERENCES sa_project(id),
 ADD CONSTRAINT fk_quiz_snapshot FOREIGN KEY(knowledge_snapshot_id) REFERENCES sa_knowledge_snapshot(id);

ALTER TABLE sa_quiz DROP CHECK chk_quiz_status;
ALTER TABLE sa_quiz ADD CONSTRAINT chk_quiz_status CHECK(status IN ('PROCESSING','READY','FAILED','COMPLETED','GENERATING','DRAFT','PUBLISHED','ARCHIVED'));
ALTER TABLE sa_quiz_question ADD COLUMN knowledge_key VARCHAR(128) NULL, ADD COLUMN source_refs_json JSON NULL, ADD COLUMN max_score DECIMAL(5,2) NOT NULL DEFAULT 1;

CREATE TABLE sa_quiz_attempt (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, project_id BIGINT UNSIGNED NOT NULL,
 quiz_id BIGINT UNSIGNED NOT NULL, user_id BIGINT UNSIGNED NOT NULL, assignment_task_id BIGINT UNSIGNED NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS', rubric_version INT UNSIGNED NOT NULL,
 auto_score DECIMAL(5,2) NULL, final_score DECIMAL(5,2) NULL, correct_count INT UNSIGNED NULL,
 review_required TINYINT(1) NOT NULL DEFAULT 0, error_message VARCHAR(1000) NULL,
 started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, submitted_at DATETIME NULL, completed_at DATETIME NULL,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 KEY idx_quiz_attempt_project_user(project_id,user_id,status,started_at),
 FOREIGN KEY(project_id) REFERENCES sa_project(id), FOREIGN KEY(quiz_id) REFERENCES sa_quiz(id),
 FOREIGN KEY(user_id) REFERENCES sa_user(id),
 CHECK(status IN ('IN_PROGRESS','SCORING','REVIEW_PENDING','COMPLETED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE sa_quiz_answer
 ADD COLUMN attempt_id BIGINT UNSIGNED NULL,
 ADD COLUMN rule_score DECIMAL(5,2) NULL,
 ADD COLUMN ai_suggested_score DECIMAL(5,2) NULL,
 ADD COLUMN final_score DECIMAL(5,2) NULL,
 ADD COLUMN scoring_source VARCHAR(16) NULL,
 ADD COLUMN ai_feedback TEXT NULL,
 ADD COLUMN source_refs_json JSON NULL,
 DROP INDEX uk_answer_quiz_question_user,
 ADD UNIQUE KEY uk_quiz_answer_attempt_question(attempt_id,question_id),
 ADD CONSTRAINT fk_answer_attempt FOREIGN KEY(attempt_id) REFERENCES sa_quiz_attempt(id);

CREATE TABLE sa_drill_scenario (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, project_id BIGINT UNSIGNED NOT NULL,
 knowledge_snapshot_id BIGINT UNSIGNED NOT NULL, revision_of_scenario_id BIGINT UNSIGNED NULL,
 version_no INT UNSIGNED NOT NULL DEFAULT 1, name VARCHAR(128) NOT NULL, description VARCHAR(1000) NULL,
 severity VARCHAR(16) NULL, initial_symptom TEXT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 rubric_version INT UNSIGNED NOT NULL DEFAULT 1, rubric_json JSON NOT NULL, pass_score DECIMAL(5,2) NOT NULL,
 created_by BIGINT UNSIGNED NOT NULL, published_at DATETIME NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 KEY idx_drill_project_status(project_id,status,update_time), FOREIGN KEY(project_id) REFERENCES sa_project(id),
 FOREIGN KEY(knowledge_snapshot_id) REFERENCES sa_knowledge_snapshot(id), FOREIGN KEY(created_by) REFERENCES sa_user(id),
 CHECK(status IN ('DRAFT','PUBLISHED','ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_drill_rule (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, scenario_id BIGINT UNSIGNED NOT NULL,
 code VARCHAR(64) NOT NULL, knowledge_key VARCHAR(128) NULL, title VARCHAR(128) NOT NULL,
 rule_type VARCHAR(16) NOT NULL, aliases_json JSON NULL, sequence_group VARCHAR(64) NULL, min_order INT UNSIGNED NULL,
 points DECIMAL(6,2) NOT NULL, sort_order INT UNSIGNED NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_drill_rule(scenario_id,code),
 FOREIGN KEY(scenario_id) REFERENCES sa_drill_scenario(id) ON DELETE CASCADE,
 CHECK(rule_type IN ('REQUIRED','OPTIONAL','RISKY','VALIDATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_drill_evidence (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, scenario_id BIGINT UNSIGNED NOT NULL,
 code VARCHAR(64) NOT NULL, evidence_type VARCHAR(16) NOT NULL, title VARCHAR(128) NOT NULL, content TEXT NOT NULL,
 unlock_rule_code VARCHAR(64) NULL, sort_order INT UNSIGNED NOT NULL DEFAULT 0,
 UNIQUE KEY uk_drill_evidence(scenario_id,code), FOREIGN KEY(scenario_id) REFERENCES sa_drill_scenario(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_drill_run (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, project_id BIGINT UNSIGNED NOT NULL,
 scenario_id BIGINT UNSIGNED NOT NULL, user_id BIGINT UNSIGNED NOT NULL, assignment_task_id BIGINT UNSIGNED NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS', rubric_version INT UNSIGNED NOT NULL,
 program_score DECIMAL(5,2) NULL, final_score DECIMAL(5,2) NULL, pass_candidate TINYINT(1) NULL,
 conclusion_json JSON NULL, ai_explanation TEXT NULL, started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 submitted_at DATETIME NULL, completed_at DATETIME NULL,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 KEY idx_drill_run_project_user(project_id,user_id,status,started_at),
 FOREIGN KEY(project_id) REFERENCES sa_project(id), FOREIGN KEY(scenario_id) REFERENCES sa_drill_scenario(id),
 FOREIGN KEY(user_id) REFERENCES sa_user(id),
 CHECK(status IN ('IN_PROGRESS','SCORING','REVIEW_PENDING','COMPLETED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_drill_run_action (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, run_id BIGINT UNSIGNED NOT NULL, seq_no INT UNSIGNED NOT NULL,
 client_request_id VARCHAR(36) NOT NULL, action_text VARCHAR(2000) NOT NULL, normalized_action VARCHAR(64) NULL,
 matched_rule_id BIGINT UNSIGNED NULL, match_source VARCHAR(16) NOT NULL, match_confidence DECIMAL(5,4) NULL,
 evidence_unlocked_json JSON NULL, risk_flag TINYINT(1) NOT NULL DEFAULT 0, points_awarded DECIMAL(6,2) NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_run_request(run_id,client_request_id),
 UNIQUE KEY uk_run_seq(run_id,seq_no), FOREIGN KEY(run_id) REFERENCES sa_drill_run(id),
 FOREIGN KEY(matched_rule_id) REFERENCES sa_drill_rule(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_assessment_review (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, project_id BIGINT UNSIGNED NOT NULL,
 target_type VARCHAR(24) NOT NULL, target_id BIGINT UNSIGNED NOT NULL, reviewer_id BIGINT UNSIGNED NOT NULL,
 decision VARCHAR(16) NOT NULL, score_override DECIMAL(5,2) NULL, comment VARCHAR(2000) NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_assessment_review_target(project_id,target_type,target_id,create_time),
 FOREIGN KEY(project_id) REFERENCES sa_project(id), FOREIGN KEY(reviewer_id) REFERENCES sa_user(id),
 CHECK(target_type IN ('QUIZ_ATTEMPT','DRILL_RUN','ASSIGNMENT')), CHECK(decision IN ('PASS','NEEDS_WORK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
