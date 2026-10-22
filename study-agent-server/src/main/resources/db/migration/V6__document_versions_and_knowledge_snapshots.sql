-- Add versioned storage before changing the logical-document status. Legacy columns remain for compatibility only.
ALTER TABLE sa_document
 ADD COLUMN project_id BIGINT UNSIGNED NULL,
 ADD COLUMN title VARCHAR(255) NULL,
 ADD COLUMN created_by BIGINT UNSIGNED NULL,
 ADD COLUMN current_published_version_id BIGINT UNSIGNED NULL,
 ADD COLUMN archived_at DATETIME NULL;
UPDATE sa_document d JOIN sa_knowledge_base k ON k.id=d.knowledge_base_id
 SET d.project_id=k.project_id,d.title=d.file_name,d.created_by=d.user_id;
CREATE TABLE sa_document_version (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 document_id BIGINT UNSIGNED NOT NULL,
 project_id BIGINT UNSIGNED NOT NULL,
 knowledge_base_id BIGINT UNSIGNED NOT NULL,
 version_no INT UNSIGNED NOT NULL,
 file_name VARCHAR(255) NOT NULL,
 file_type VARCHAR(16) NOT NULL,
 mime_type VARCHAR(128) NULL,
 file_size BIGINT UNSIGNED NOT NULL,
 content_hash CHAR(64) NULL COMMENT 'NULL only for migrated FAILED versions; calculated from original file on reprocess',
 storage_key VARCHAR(512) NOT NULL,
 process_status VARCHAR(16) NOT NULL DEFAULT 'UPLOADED',
 publish_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 progress TINYINT UNSIGNED NOT NULL DEFAULT 0,
 chunk_count INT UNSIGNED NOT NULL DEFAULT 0,
 current_task_id VARCHAR(64) NULL,
 change_note VARCHAR(1000) NULL,
 error_message VARCHAR(1000) NULL,
 reviewer_id BIGINT UNSIGNED NULL,
 review_comment VARCHAR(1000) NULL,
 reviewed_at DATETIME NULL,
 published_at DATETIME NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_version_number(document_id,version_no),
 KEY idx_version_project_process_time(project_id,process_status,update_time),
 KEY idx_version_task(current_task_id),
 KEY idx_version_storage(storage_key),
 CONSTRAINT fk_version_document FOREIGN KEY(document_id) REFERENCES sa_document(id),
 CONSTRAINT fk_version_project FOREIGN KEY(project_id) REFERENCES sa_project(id),
 CONSTRAINT fk_version_kb FOREIGN KEY(knowledge_base_id) REFERENCES sa_knowledge_base(id),
 CONSTRAINT chk_version_process CHECK(process_status IN ('UPLOADED','PARSING','CHUNKING','EMBEDDING','READY','FAILED')),
 CONSTRAINT chk_version_publish CHECK(publish_status IN ('DRAFT','IN_REVIEW','APPROVED','REJECTED','PUBLISHED','SUPERSEDED')),
 CONSTRAINT chk_version_ready CHECK(process_status <> 'READY' OR (progress=100 AND content_hash IS NOT NULL)),
 CONSTRAINT chk_version_failure CHECK(process_status <> 'FAILED' OR (error_message IS NOT NULL AND LENGTH(TRIM(error_message))>0)),
 CONSTRAINT chk_version_published_ready CHECK(publish_status NOT IN ('PUBLISHED','SUPERSEDED') OR process_status='READY'),
 CONSTRAINT chk_version_hash CHECK(content_hash IS NOT NULL OR process_status='FAILED'),
 CONSTRAINT chk_version_progress CHECK(progress<=100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO sa_document_version(document_id,project_id,knowledge_base_id,version_no,file_name,file_type,mime_type,file_size,storage_key,
 process_status,publish_status,progress,chunk_count,current_task_id,change_note,error_message,create_time)
SELECT id,project_id,knowledge_base_id,1,file_name,file_type,mime_type,file_size,storage_key,
 'FAILED','DRAFT',0,0,NULL,'Legacy import: reindex in V3 collection before review',
 'Legacy document requires V3 reindex; original file retained',create_time FROM sa_document;
ALTER TABLE sa_document DROP CHECK chk_doc_status;
UPDATE sa_document SET status='ACTIVE';
ALTER TABLE sa_document
 MODIFY project_id BIGINT UNSIGNED NOT NULL,
 MODIFY title VARCHAR(255) NOT NULL,
 MODIFY created_by BIGINT UNSIGNED NOT NULL,
 MODIFY status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 MODIFY user_id BIGINT UNSIGNED NULL,
 MODIFY file_name VARCHAR(255) NULL,
 MODIFY file_type VARCHAR(16) NULL,
 MODIFY storage_key VARCHAR(512) NULL,
 MODIFY file_url VARCHAR(1024) NULL,
 ADD CONSTRAINT chk_doc_v3_status CHECK(status IN ('ACTIVE','ARCHIVED')),
 ADD CONSTRAINT fk_doc_project FOREIGN KEY(project_id) REFERENCES sa_project(id),
 ADD INDEX idx_doc_project_kb_status(project_id,knowledge_base_id,status);
CREATE TABLE sa_knowledge_snapshot (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 project_id BIGINT UNSIGNED NOT NULL,
 knowledge_base_id BIGINT UNSIGNED NOT NULL,
 snapshot_no INT UNSIGNED NOT NULL,
 reason VARCHAR(32) NOT NULL,
 created_by BIGINT UNSIGNED NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_snapshot_number(knowledge_base_id,snapshot_no),
 CONSTRAINT fk_snapshot_project FOREIGN KEY(project_id) REFERENCES sa_project(id),
 CONSTRAINT fk_snapshot_kb FOREIGN KEY(knowledge_base_id) REFERENCES sa_knowledge_base(id),
 CONSTRAINT chk_snapshot_reason CHECK(reason IN ('DOCUMENT_PUBLISH','MANUAL_IMPORT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE sa_knowledge_snapshot_item (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 snapshot_id BIGINT UNSIGNED NOT NULL,
 document_id BIGINT UNSIGNED NOT NULL,
 document_version_id BIGINT UNSIGNED NOT NULL,
 sort_order INT UNSIGNED NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_snapshot_document(snapshot_id,document_id),
 UNIQUE KEY uk_snapshot_version(snapshot_id,document_version_id),
 CONSTRAINT fk_item_snapshot FOREIGN KEY(snapshot_id) REFERENCES sa_knowledge_snapshot(id),
 CONSTRAINT fk_item_document FOREIGN KEY(document_id) REFERENCES sa_document(id),
 CONSTRAINT fk_item_version FOREIGN KEY(document_version_id) REFERENCES sa_document_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE sa_content_dependency (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 project_id BIGINT UNSIGNED NOT NULL,
 source_document_id BIGINT UNSIGNED NOT NULL,
 source_version_id BIGINT UNSIGNED NOT NULL,
 target_type VARCHAR(16) NOT NULL,
 target_id BIGINT UNSIGNED NOT NULL,
 review_status VARCHAR(16) NOT NULL DEFAULT 'CURRENT',
 detected_at DATETIME NULL,
 confirmed_by BIGINT UNSIGNED NULL,
 confirmed_at DATETIME NULL,
 note VARCHAR(1000) NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_dependency_source_target(source_version_id,target_type,target_id),
 KEY idx_dependency_project_review(project_id,review_status,target_type),
 CONSTRAINT fk_dependency_project FOREIGN KEY(project_id) REFERENCES sa_project(id),
 CONSTRAINT fk_dependency_document FOREIGN KEY(source_document_id) REFERENCES sa_document(id),
 CONSTRAINT fk_dependency_version FOREIGN KEY(source_version_id) REFERENCES sa_document_version(id),
 CONSTRAINT chk_dependency_target CHECK(target_type IN ('QUIZ','DRILL','PATH_TASK')),
 CONSTRAINT chk_dependency_review CHECK(review_status IN ('CURRENT','NEEDS_REVIEW','CONFIRMED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE sa_document ADD CONSTRAINT fk_doc_current_version FOREIGN KEY(current_published_version_id) REFERENCES sa_document_version(id);
ALTER TABLE sa_knowledge_base ADD CONSTRAINT fk_kb_current_snapshot FOREIGN KEY(current_snapshot_id) REFERENCES sa_knowledge_snapshot(id);
