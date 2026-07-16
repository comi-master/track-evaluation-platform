# Database Design

## Status

Milestone 1 has not started. There are no Flyway migrations, entities, mappers, executed SQL statements, or `EXPLAIN` results yet. This document records the proposed model only; it will be replaced with migration-backed evidence.

## Proposed logical ER model

```mermaid
erDiagram
    SYS_USER ||--o{ SYS_USER_ROLE : has
    SYS_ROLE ||--o{ SYS_USER_ROLE : grants
    SYS_USER ||--o{ DATASET : owns
    DATASET ||--o{ DATASET_FILE : contains
    DATASET_FILE ||--o{ TRACK_POINT : provides
    DATASET ||--o{ ANALYSIS_TASK : requests
    DATASET_FILE ||--o{ ANALYSIS_TASK : analyzes
    ANALYSIS_TASK ||--|| ANALYSIS_RESULT : produces
    ANALYSIS_TASK ||--o{ ABNORMAL_INTERVAL : detects
    ANALYSIS_TASK ||--o{ ANALYSIS_REPORT : versions
    SYS_USER ||--o{ OPERATION_LOG : performs
    ANALYSIS_TASK ||--o{ MESSAGE_OUTBOX : emits
```

Planned tables are `sys_user`, `sys_role`, `sys_user_role`, `dataset`, `dataset_file`, `track_point`, `analysis_task`, `analysis_result`, `abnormal_interval`, `analysis_report`, `operation_log`, and `message_outbox`.

## Planned constraints and indexes

- Unique usernames and role codes; unique `(user_id, role_id)` assignments.
- Ownership query index on `dataset.user_id` and logical deletion status.
- File metadata index on `dataset_file.dataset_id`; duplicate upload defense will use an ownership-aware business key after query design.
- Track lookup indexes `(dataset_id, time_value)` and `(dataset_id, error_value)`; initial uniqueness candidate is `(file_id, time_value)` for the single-source/single-target format.
- Unique task request ID and unique result task ID for idempotency defenses.
- Outbox unique event ID plus a publish-scan index based on status and next retry time.

## Planned transaction boundaries

- Schema changes only through new immutable Flyway versions.
- Batch track-point writes use explicit chunk/rollback semantics; no per-row transaction.
- Task creation and Outbox insert share one MySQL transaction.
- MinIO and MySQL cannot share a local transaction, so upload requires compensation and reconciliation rather than a false atomic claim.

## Core SQL and EXPLAIN

No core SQL exists yet, so no `EXPLAIN` result is claimed. Milestone 1 will document the exact select columns, predicates, fixtures, MySQL version, plan output, and why each index supports an observed access path.
