-- V3 quizzes are project-owned. Keep legacy user_id rows intact, but allow new
-- project quizzes to omit the V2 owner column.
ALTER TABLE sa_quiz
    MODIFY COLUMN user_id BIGINT UNSIGNED NULL,
    MODIFY COLUMN knowledge_base_id BIGINT UNSIGNED NULL;

-- V3 answers are keyed by immutable attempts. Legacy answers remain queryable
-- through quiz_id/user_id and are not rewritten into synthetic attempts.
ALTER TABLE sa_quiz_answer
    MODIFY COLUMN quiz_id BIGINT UNSIGNED NULL,
    MODIFY COLUMN user_id BIGINT UNSIGNED NULL,
    MODIFY COLUMN is_correct TINYINT(1) NULL DEFAULT NULL;

CREATE INDEX idx_quiz_attempt_task ON sa_quiz_attempt(assignment_task_id, status);
CREATE INDEX idx_drill_run_task ON sa_drill_run(assignment_task_id, status);
