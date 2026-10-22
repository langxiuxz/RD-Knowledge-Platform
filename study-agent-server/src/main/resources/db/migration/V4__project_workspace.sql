CREATE TABLE sa_project (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT UNSIGNED NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    archived_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_code (code),
    KEY idx_project_status_time (status, update_time),
    CONSTRAINT chk_project_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_project_creator FOREIGN KEY (created_by) REFERENCES sa_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_project_member (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'LEARNER',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    removed_at DATETIME NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_member (project_id, user_id),
    KEY idx_member_user_status (user_id, status),
    KEY idx_member_project_role_status (project_id, role, status),
    CONSTRAINT chk_member_role CHECK (role IN ('OWNER', 'MAINTAINER', 'LEARNER')),
    CONSTRAINT chk_member_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT fk_member_project FOREIGN KEY (project_id) REFERENCES sa_project(id),
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES sa_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_audit_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NULL,
    actor_user_id BIGINT UNSIGNED NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT UNSIGNED NULL,
    detail_json JSON NULL,
    request_id VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_project_time (project_id, create_time),
    KEY idx_audit_resource (resource_type, resource_id),
    KEY idx_audit_actor_time (actor_user_id, create_time),
    CONSTRAINT fk_audit_project FOREIGN KEY (project_id) REFERENCES sa_project(id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES sa_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
