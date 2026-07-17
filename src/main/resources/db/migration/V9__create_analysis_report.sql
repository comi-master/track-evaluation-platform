CREATE TABLE analysis_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    report_type VARCHAR(32) NOT NULL,
    source_file_count INT UNSIGNED NOT NULL,
    content_html MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_analysis_report PRIMARY KEY (id),
    CONSTRAINT fk_analysis_report_dataset FOREIGN KEY (dataset_id) REFERENCES dataset (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_analysis_report_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT chk_analysis_report_source_count CHECK (source_file_count > 0),
    CONSTRAINT chk_analysis_report_type CHECK (report_type = 'DATASET_COMPARISON'),
    CONSTRAINT chk_analysis_report_content CHECK (CHAR_LENGTH(content_html) > 0),
    INDEX idx_analysis_report_dataset_created (dataset_id, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
