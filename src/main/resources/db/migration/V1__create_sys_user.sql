CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'User identifier',
    username VARCHAR(64) NOT NULL COMMENT 'Case-insensitive login name',
    password_hash VARCHAR(255) NOT NULL COMMENT 'Encoded password hash',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Account status',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Logical deletion flag: 0 active, 1 deleted',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC creation time',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC last update time',
    CONSTRAINT pk_sys_user PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_username UNIQUE (username),
    CONSTRAINT chk_sys_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_sys_user_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Application users';
