ALTER TABLE sys_user
    ADD COLUMN display_name VARCHAR(100) NULL AFTER username,
    ADD COLUMN email VARCHAR(254) NULL AFTER display_name,
    ADD COLUMN failed_login_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN last_login_at DATETIME(6) NULL AFTER failed_login_count;

CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT pk_sys_role PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_sys_user_role PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_role(code, name) VALUES ('ADMIN', 'Administrator'), ('RESEARCHER', 'Researcher');
INSERT INTO sys_user_role(user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.code='RESEARCHER'
WHERE u.deleted=0;

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    username_snapshot VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NULL,
    resource_id VARCHAR(100) NULL,
    request_id VARCHAR(100) NULL,
    ip_address VARCHAR(45) NULL,
    detail VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE SET NULL,
    INDEX idx_audit_log_created (created_at DESC, id DESC),
    INDEX idx_audit_log_user_created (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
