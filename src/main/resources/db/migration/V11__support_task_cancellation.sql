ALTER TABLE analysis_task DROP CHECK chk_analysis_task_status;
ALTER TABLE analysis_task DROP CHECK chk_analysis_task_result_state;
ALTER TABLE analysis_task
    ADD CONSTRAINT chk_analysis_task_status CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','CANCELLED')),
    ADD CONSTRAINT chk_analysis_task_result_state CHECK ((status = 'SUCCESS' AND analysis_result_id IS NOT NULL) OR (status <> 'SUCCESS' AND analysis_result_id IS NULL));
