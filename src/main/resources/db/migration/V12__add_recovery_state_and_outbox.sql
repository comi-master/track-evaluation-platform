ALTER TABLE analysis_task
    ADD COLUMN lease_owner VARCHAR(100) NULL AFTER status,
    ADD COLUMN lease_token VARCHAR(36) NULL AFTER lease_owner,
    ADD COLUMN lease_expires_at DATETIME(6) NULL AFTER lease_token,
    ADD COLUMN heartbeat_at DATETIME(6) NULL AFTER lease_expires_at,
    ADD INDEX idx_analysis_task_expired_lease (status, lease_expires_at, id);

ALTER TABLE dataset
    ADD COLUMN delete_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER deleted,
    ADD COLUMN delete_requested_at DATETIME(6) NULL AFTER delete_status,
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER delete_requested_at,
    ADD COLUMN delete_error VARCHAR(500) NULL AFTER deleted_at,
    ADD COLUMN delete_attempt_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER delete_error,
    ADD CONSTRAINT chk_dataset_delete_status CHECK (delete_status IN ('ACTIVE','DELETE_PENDING','DELETE_FAILED','DELETED')),
    ADD INDEX idx_dataset_delete_work (delete_status, delete_requested_at, id);

CREATE TABLE reliable_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_key VARCHAR(160) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL,
    claimed_at DATETIME(6) NULL,
    claim_token VARCHAR(36) NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at DATETIME(6) NULL,
    CONSTRAINT pk_reliable_outbox PRIMARY KEY (id),
    CONSTRAINT uk_reliable_outbox_event_key UNIQUE (event_key),
    CONSTRAINT chk_reliable_outbox_status CHECK (status IN ('PENDING','PROCESSING','PROCESSED')),
    INDEX idx_reliable_outbox_work (status, available_at, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
