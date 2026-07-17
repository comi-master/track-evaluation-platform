# ADR 0005: Track-file storage and parsing

## Status

Accepted for milestone 3.

## Decision

Upload and parse are separate authenticated APIs. Raw CSV files are private MinIO objects named `{userId}/{datasetId}/{uuid}.csv`; MySQL stores immutable metadata and SHA-256 deduplication. Parsing is synchronous, requires the exact seven-column header, reads incrementally with Apache Commons CSV, and persists points through configurable MyBatis XML batches. `track_point` contains source values only and no analysis result.

MinIO downloads complete into a bounded temporary file before the point-write transaction begins. Successful point batches and the final `PARSED` state share one MySQL transaction. Failures roll back all points and are followed by a short `FAILED` status transaction. Upload metadata failures trigger best-effort removal of only the new object.

## Consequences

The design bounds heap use, permits safe parsing retries, and keeps ownership predicates in SQL. It requires temporary disk proportional to the configured maximum file size. MySQL and MinIO are not atomically consistent; the chosen compensation is intentionally minimal. Transactional Outbox, error/RMSE calculation, abnormal intervals, messaging, Redis business caching and reports are not implemented in this milestone.
