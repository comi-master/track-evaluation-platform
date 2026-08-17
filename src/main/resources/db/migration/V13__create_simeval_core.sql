CREATE TABLE benchmark (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_benchmark PRIMARY KEY (id),
    CONSTRAINT fk_benchmark_creator FOREIGN KEY (created_by) REFERENCES sys_user (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_benchmark_visibility CHECK (visibility IN ('PRIVATE','PUBLIC')),
    CONSTRAINT chk_benchmark_status CHECK (status IN ('DRAFT','PUBLISHED','RETIRED')),
    INDEX idx_benchmark_creator_status (created_by, status, created_at DESC, id DESC),
    INDEX idx_benchmark_public (visibility, status, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE benchmark_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    benchmark_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    reference_track_file_id BIGINT NOT NULL,
    format_version VARCHAR(32) NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6) NULL,
    CONSTRAINT pk_benchmark_version PRIMARY KEY (id),
    CONSTRAINT fk_benchmark_version_benchmark FOREIGN KEY (benchmark_id) REFERENCES benchmark (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_benchmark_version_file FOREIGN KEY (reference_track_file_id) REFERENCES track_file (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_benchmark_version_creator FOREIGN KEY (created_by) REFERENCES sys_user (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uk_benchmark_version_no UNIQUE (benchmark_id, version_no),
    CONSTRAINT chk_benchmark_version_status CHECK (status IN ('DRAFT','PUBLISHED','RETIRED')),
    INDEX idx_benchmark_version_lookup (benchmark_id, status, version_no DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE evaluation_protocol (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    description VARCHAR(500) NULL,
    rules_json JSON NOT NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6) NULL,
    CONSTRAINT pk_evaluation_protocol PRIMARY KEY (id),
    CONSTRAINT fk_evaluation_protocol_creator FOREIGN KEY (created_by) REFERENCES sys_user (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uk_evaluation_protocol_version UNIQUE (name, version_no),
    CONSTRAINT chk_evaluation_protocol_visibility CHECK (visibility IN ('PRIVATE','PUBLIC')),
    CONSTRAINT chk_evaluation_protocol_status CHECK (status IN ('DRAFT','PUBLISHED','RETIRED')),
    INDEX idx_evaluation_protocol_public (visibility, status, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE algorithm_project (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    repository_url VARCHAR(500) NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_algorithm_project PRIMARY KEY (id),
    CONSTRAINT fk_algorithm_project_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_algorithm_project_visibility CHECK (visibility IN ('PRIVATE','PUBLIC')),
    CONSTRAINT chk_algorithm_project_status CHECK (status IN ('ACTIVE','ARCHIVED')),
    INDEX idx_algorithm_project_owner (owner_user_id, status, created_at DESC, id DESC),
    INDEX idx_algorithm_project_public (visibility, status, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE algorithm_submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    benchmark_version_id BIGINT NOT NULL,
    protocol_id BIGINT NOT NULL,
    output_track_file_id BIGINT NOT NULL,
    algorithm_version VARCHAR(128) NOT NULL,
    git_commit VARCHAR(128) NULL,
    submission_key CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    description VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_algorithm_submission PRIMARY KEY (id),
    CONSTRAINT fk_algorithm_submission_project FOREIGN KEY (project_id) REFERENCES algorithm_project (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_algorithm_submission_benchmark FOREIGN KEY (benchmark_version_id) REFERENCES benchmark_version (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_algorithm_submission_protocol FOREIGN KEY (protocol_id) REFERENCES evaluation_protocol (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_algorithm_submission_file FOREIGN KEY (output_track_file_id) REFERENCES track_file (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uk_algorithm_submission_key UNIQUE (project_id, submission_key),
    CONSTRAINT chk_algorithm_submission_status CHECK (status IN ('SUBMITTED','VALIDATING','VALID','INVALID','EVALUATING','EVALUATED')),
    INDEX idx_algorithm_submission_project (project_id, created_at DESC, id DESC),
    INDEX idx_algorithm_submission_evaluation (status, benchmark_version_id, protocol_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
