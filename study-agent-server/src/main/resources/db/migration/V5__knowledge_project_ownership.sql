-- Preserve private legacy ownership in dedicated projects; never merge into an existing team.
CREATE TEMPORARY TABLE legacy_knowledge_owner (user_id BIGINT UNSIGNED PRIMARY KEY, project_code VARCHAR(64) NOT NULL);
INSERT INTO legacy_knowledge_owner
SELECT user_id, CONCAT('legacy-',user_id,'-',LEFT(REPLACE(UUID(),'-',''),16)) FROM (
 SELECT user_id FROM sa_knowledge_base UNION SELECT user_id FROM sa_conversation
) owners;
INSERT INTO sa_project(name,code,description,created_by)
SELECT CONCAT('Legacy Knowledge ',user_id),project_code,'Migrated private knowledge and conversations',user_id FROM legacy_knowledge_owner;
INSERT INTO sa_project_member(project_id,user_id,role)
SELECT p.id,o.user_id,'OWNER' FROM legacy_knowledge_owner o JOIN sa_project p ON p.code=o.project_code;
ALTER TABLE sa_knowledge_base
 ADD COLUMN project_id BIGINT UNSIGNED NULL,
 ADD COLUMN created_by BIGINT UNSIGNED NULL,
 ADD COLUMN current_snapshot_id BIGINT UNSIGNED NULL,
 DROP CHECK chk_kb_status;
UPDATE sa_knowledge_base k JOIN legacy_knowledge_owner o ON o.user_id=k.user_id
 JOIN sa_project p ON p.code=o.project_code
 SET k.project_id=p.id,k.created_by=k.user_id,k.status='ACTIVE';
ALTER TABLE sa_knowledge_base
 MODIFY project_id BIGINT UNSIGNED NOT NULL,
 MODIFY created_by BIGINT UNSIGNED NOT NULL,
 MODIFY user_id BIGINT UNSIGNED NULL,
 MODIFY name VARCHAR(128) NOT NULL,
 MODIFY description VARCHAR(1000) NULL,
 MODIFY status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 ADD CONSTRAINT chk_kb_v3_status CHECK(status IN ('ACTIVE','ARCHIVED')),
 ADD CONSTRAINT fk_kb_project FOREIGN KEY(project_id) REFERENCES sa_project(id),
 ADD INDEX idx_kb_project_status_time(project_id,status,update_time);
ALTER TABLE sa_conversation ADD COLUMN project_id BIGINT UNSIGNED NULL, ADD COLUMN knowledge_snapshot_id BIGINT UNSIGNED NULL;
UPDATE sa_conversation c JOIN sa_knowledge_base k ON k.id=c.knowledge_base_id SET c.project_id=k.project_id;
UPDATE sa_conversation c JOIN legacy_knowledge_owner o ON o.user_id=c.user_id
 JOIN sa_project p ON p.code=o.project_code SET c.project_id=p.id WHERE c.project_id IS NULL;
ALTER TABLE sa_conversation MODIFY project_id BIGINT UNSIGNED NOT NULL,
 ADD CONSTRAINT fk_conv_project FOREIGN KEY(project_id) REFERENCES sa_project(id),
 ADD INDEX idx_conv_project_user_time(project_id,user_id,update_time);
INSERT INTO sa_audit_log(project_id,actor_user_id,action,resource_type,resource_id)
SELECT p.id,o.user_id,'LEGACY_KNOWLEDGE_MIGRATED','PROJECT',p.id FROM legacy_knowledge_owner o JOIN sa_project p ON p.code=o.project_code;
DROP TEMPORARY TABLE legacy_knowledge_owner;
