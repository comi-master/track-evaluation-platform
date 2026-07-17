CREATE TABLE track_point (
    id BIGINT NOT NULL AUTO_INCREMENT,
    track_file_id BIGINT NOT NULL,
    sequence_no BIGINT UNSIGNED NOT NULL,
    time_value DOUBLE NOT NULL,
    true_x DOUBLE NOT NULL,
    true_y DOUBLE NOT NULL,
    true_z DOUBLE NOT NULL,
    track_x DOUBLE NOT NULL,
    track_y DOUBLE NOT NULL,
    track_z DOUBLE NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_track_point PRIMARY KEY (id),
    CONSTRAINT fk_track_point_file FOREIGN KEY (track_file_id) REFERENCES track_file (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uk_track_point_sequence UNIQUE (track_file_id, sequence_no),
    INDEX idx_track_point_time (track_file_id, time_value)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
