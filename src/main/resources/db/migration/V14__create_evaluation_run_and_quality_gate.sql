CREATE TABLE evaluation_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    analysis_task_id BIGINT NULL,
    analysis_result_id BIGINT NULL,
    baseline_run_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    gate_status VARCHAR(16) NULL,
    metrics_json JSON NULL,
    failure_message VARCHAR(500) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_evaluation_run PRIMARY KEY (id),
    CONSTRAINT fk_evaluation_run_submission FOREIGN KEY (submission_id) REFERENCES algorithm_submission (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_evaluation_run_task FOREIGN KEY (analysis_task_id) REFERENCES analysis_task (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_evaluation_run_result FOREIGN KEY (analysis_result_id) REFERENCES analysis_result (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_evaluation_run_baseline FOREIGN KEY (baseline_run_id) REFERENCES evaluation_run (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uk_evaluation_run_submission UNIQUE (submission_id),
    CONSTRAINT chk_evaluation_run_status CHECK (status IN ('QUEUED','RUNNING','SUCCESS','FAILED','CANCELLED')),
    CONSTRAINT chk_evaluation_run_gate CHECK (gate_status IS NULL OR gate_status IN ('PASS','FAIL','WARNING')),
    INDEX idx_evaluation_run_status (status, created_at, id),
    INDEX idx_evaluation_run_submission (submission_id, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quality_gate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    evaluation_run_id BIGINT NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    actual_value DOUBLE NULL,
    threshold_value DOUBLE NULL,
    comparison VARCHAR(8) NOT NULL,
    passed TINYINT UNSIGNED NOT NULL,
    detail VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_quality_gate PRIMARY KEY (id),
    CONSTRAINT fk_quality_gate_run FOREIGN KEY (evaluation_run_id) REFERENCES evaluation_run (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uk_quality_gate_metric UNIQUE (evaluation_run_id, metric_code),
    CONSTRAINT chk_quality_gate_comparison CHECK (comparison IN ('LTE','GTE','LT','GT','EQ')),
    CONSTRAINT chk_quality_gate_passed CHECK (passed IN (0,1)),
    INDEX idx_quality_gate_run (evaluation_run_id, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
