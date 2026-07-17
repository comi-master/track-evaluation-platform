# 0006: Shared synchronous analysis core with asynchronous orchestration

Milestone 4 stores each successful calculation as an immutable `analysis_result` and its contiguous abnormal intervals. The read/compute phase uses keyset batches ordered by `sequence_no`, so it does not retain the entire point set or hold a long database transaction. A short transaction writes the result and intervals together.

Errors are three-dimensional Euclidean distance. Welford's algorithm produces population standard deviation; a point is abnormal only when `error > threshold`. `track_point` remains raw input and does not store `position_error`.

Milestone 5 retains the synchronous API and reuses the same calculation/write core behind a persisted asynchronous task. RabbitMQ carries only a version and task ID. Database conditional transitions provide basic idempotency; manual ACK, bounded TTL retry and a dead queue provide explicit failure handling. Redis Cache-Aside accelerates only latest-result and comparison reads after ownership checks, with post-commit invalidation and database fallback.

Consequences: synchronous and asynchronous metrics cannot drift, and result/interval/task-success are atomic. The system remains at-least-once and has a database-to-publish dual-write window. Transactional Outbox, reports and milestone 6 remain excluded.
