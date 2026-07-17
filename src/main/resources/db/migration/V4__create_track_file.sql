CREATE TABLE track_file (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Track file identifier',
    dataset_id BIGINT NOT NULL COMMENT 'Owning dataset identifier',
    original_name VARCHAR(255) NOT NULL COMMENT 'Sanitized original file name',
    object_name VARCHAR(512) NOT NULL COMMENT 'Private MinIO object key',
    sha256 CHAR(64) NOT NULL COMMENT 'Lowercase SHA-256 digest',
    file_size BIGINT UNSIGNED NOT NULL COMMENT 'File size in bytes',
    track_source VARCHAR(32) NOT NULL COMMENT 'Track source type',
    parse_status VARCHAR(16) NOT NULL COMMENT 'CSV parsing state',
    point_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Parsed point count',
    parse_error VARCHAR(500) NULL COMMENT 'Safe parsing error summary',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_track_file PRIMARY KEY (id),
    CONSTRAINT fk_track_file_dataset FOREIGN KEY (dataset_id) REFERENCES dataset (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uk_track_file_object UNIQUE (object_name),
    CONSTRAINT uk_track_file_dataset_sha UNIQUE (dataset_id, sha256),
    CONSTRAINT chk_track_file_size CHECK (file_size > 0),
    CONSTRAINT chk_track_file_source CHECK (track_source IN ('RADAR','INFRARED','FUSION','ALGORITHM','OTHER')),
    CONSTRAINT chk_track_file_status CHECK (parse_status IN ('UPLOADED','PARSING','PARSED','FAILED')),
    INDEX idx_track_file_dataset_page (dataset_id, created_at DESC, id DESC)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
