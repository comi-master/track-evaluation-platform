CREATE TABLE dataset (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Dataset identifier',
    user_id BIGINT NOT NULL COMMENT 'Owning user identifier',
    name VARCHAR(128) NOT NULL COMMENT 'Dataset display name',
    description VARCHAR(500) NULL COMMENT 'Optional dataset description',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Logical deletion flag: 0 active, 1 deleted',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC creation time',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC last update time',
    CONSTRAINT pk_dataset PRIMARY KEY (id),
    CONSTRAINT fk_dataset_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_dataset_deleted CHECK (deleted IN (0, 1)),
    INDEX idx_dataset_owner_page (user_id, deleted, created_at DESC, id DESC)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'User-owned track datasets';
