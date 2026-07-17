# Interview Evidence Notebook

This file grows only from implemented and verified project evidence. Resume descriptions and interview Q&A are consolidated into milestone 6. Milestone 0 records a small architecture baseline and does not claim later business experience. The former 0-12 route is a historical plan, has been retired, and is not the current execution route.

## Why a modular monolith instead of microservices?

**Base answer:** The first release has one coherent upload-analysis-report boundary and one developer/operator. A package-by-feature monolith keeps local transactions, debugging, and deployment simple while preserving module boundaries.

**First follow-up — how is it modular?** Feature packages own their use cases, domain rules, and adapters; cross-cutting HTTP behavior remains in `common`.

**Second follow-up — what prevents coupling?** Controllers do not call mappers directly, domain code avoids middleware SDKs, and feature interaction goes through deliberate application/domain contracts.

**Third follow-up — when would you split?** Only after measured independent scaling, release cadence, ownership, or fault-isolation needs outweigh distributed consistency and operations cost.

**Project evidence:** `AGENTS.md`, package boundaries under `src/main/java/com/example/trackanalysis`, and ADR 0001. These are structural evidence only; later dependency tests remain pending.

## Why accept or generate a request ID?

**Base answer:** A stable request ID correlates the HTTP response with server logs and future audit/task records.

**First follow-up — can the client send anything?** No. The filter accepts only a bounded safe character pattern; missing or unsafe input becomes a UUID.

**Second follow-up — how does cleanup work?** The filter writes MDC in a `try` block and removes it in `finally`, preventing thread-reuse leakage.

**Third follow-up — is request ID a security identity?** No. It is observability metadata and must never authorize access or supply idempotency by itself.

**Project evidence:** `RequestIdFilter` and its filter/MockMvc tests.

## Why not connect every middleware in milestone 0?

**Base answer:** Each component must solve a real, testable problem. Introducing all SDKs before a synchronous business path exists multiplies configuration and failure variables without proving value.

**First follow-up — what is defined now?** Compose describes MySQL, Redis, RabbitMQ, and MinIO for repeatable local dependencies, but application clients are absent.

**Second follow-up — when are clients added?** MySQL in milestone 1, security and dataset behavior in milestone 2, MinIO in milestone 3, and RabbitMQ plus Redis result caching in milestone 5.

**Third follow-up — how does this help testing?** Each new adapter arrives with focused Testcontainers behavior and a documented outage/degradation path.

**Project evidence:** `pom.xml` contains no later SDKs, while `PLANS.md` orders their introduction.

## Why is Transactional Outbox absent from the first release?

**Base answer:** The first release prioritizes a complete, demonstrable business workflow. Milestone 5 still requires bounded retries, a DLQ, manual ACK, and basic idempotency, while Outbox complexity is deferred until a separately approved reliability extension.

**First follow-up — does that make database and broker publication atomic?** No. The project must state that limitation honestly and must not claim atomic cross-system delivery.

**Second follow-up — when should Outbox be reconsidered?** When verified delivery-risk requirements justify its extra schema, publisher, recovery, monitoring, and duplicate-handling costs.

**Project evidence:** the active scope in `PLANS.md` and ADR 0003. This is a planning decision, not evidence that messaging has already been implemented.

## Why use Flyway and real MySQL integration tests?

**Base answer:** Versioned immutable migrations make schema changes reproducible, while MySQL 8.4 Testcontainers verifies behavior that an in-memory substitute could misrepresent.

**First follow-up — what was tested?** Empty and repeated migration, checksums, schema metadata, constraints, owner pagination, logical deletion, optimistic locks, UTC fills, and full-table update blocking.

**Second follow-up — why are normal tests separate?** The default `clean verify` remains fast and Docker-independent; `-Pit` explicitly opts into real database acceptance and fails when Docker is unavailable.

**Third follow-up — why only two tables?** `sys_user` and `dataset` are the immediate persistence prerequisites. File, track, analysis, report, role, audit, and Outbox tables wait until their business rules and access paths are known.

**Project evidence:** Flyway V1/V2, feature-local DO/Mapper classes, and the persistence `*IT` suites. Milestone 2 builds authentication and owner-scoped dataset HTTP use cases on this foundation.

## Milestone 2: Why use JWT plus `auth_version`?

**Base answer:** A self-contained JWT alone cannot provide immediate logout before expiry. Each protected request therefore verifies the token cryptographically and compares its `authVersion` claim with `sys_user.auth_version`; logout increments the database value so every older token becomes invalid.

**First follow-up — why not Redis?** The current modular monolith prioritizes a clear revocation contract over avoiding one indexed user lookup. Redis login state, blacklists and Refresh Tokens add lifecycle and failure modes that milestone 2 does not need.

**Second follow-up — what is the logout tradeoff?** Logout is global for that user, not device-specific. It invalidates all previously issued tokens. This behavior is explicit in the API and ADR rather than being presented as per-device session management.

**Third follow-up — how is IDOR prevented?** Dataset Mapper statements include the resource ID, authenticated owner ID and `deleted = 0` in the same SQL condition. The application never loads by ID and checks ownership later, and inaccessible resources consistently return 404.

**Fourth follow-up — how are concurrent edits handled?** Updates require the client-visible `version` and condition on it in SQL. A zero-row update is distinguished from invisibility: an existing owned resource produces 409 for a stale version, while an absent or foreign resource produces 404.

**Project evidence:** `JwtAuthenticationFilter`, `JwtService`, `AuthApplicationService`, owner-scoped `DatasetMapper` SQL, V3 migration, ordinary security/service tests, and MySQL 8.4 API integration tests.

## Milestone 3: Why split upload from parsing?

**Base answer:** Upload establishes durable raw input and immutable metadata; parsing is a separate retryable state transition. A successful upload therefore remains useful even when CSV validation later fails.

**How is memory bounded?** The multipart input is copied with an 8 KiB buffer to a temporary file while hashing, MinIO is streamed in both directions, Commons CSV iterates records, and MyBatis writes configurable 500-row batches. The complete upload is never retained in a JVM byte array.

**How are cross-system failures handled?** MinIO and MySQL cannot share the application's local transaction. If metadata insertion fails after upload, the application best-effort removes only the newly generated object. Parsing downloads outside the database transaction, rolls back all point batches on failure, then marks the file `FAILED` in a short transaction. This is explicit compensation, not a distributed-consistency claim or an Outbox.

**How are IDOR and parser leakage prevented?** File and point SQL joins the file to its dataset and authenticated `user_id`; foreign and absent IDs both produce 404. Parser errors expose a bounded line number and reason without logging or storing the row content.

**Project evidence:** Flyway V4/V5, `TrackFileApplicationService`, `CsvTrackParser`, private MinIO adapter, `TrackPointMapper.xml`, ordinary tests and dual-container `TrackFileApiIT`.

## Milestone 4: Why synchronous analysis and keyset scanning?

**Base answer:** The first analysis closure is synchronous so failure and transaction semantics remain explicit before messaging is introduced. Points use `sequence_no > cursor ORDER BY sequence_no LIMIT batchSize`; no full list or large OFFSET is used.

**How are statistics and intervals calculated?** Three-dimensional Euclidean error feeds Welford population variance and squared-error RMSE. Only `error > threshold` is abnormal. Normal points and sequence gaps close intervals; equal maxima keep the earliest time.

**How is persistence atomic?** Scanning occurs outside a long transaction. A short transaction inserts one immutable result and its intervals; any failure rolls both back. Raw points and CSV objects are unchanged.

**What remains absent after milestone 5?** There is still no persisted `position_error`, report generation, Transactional Outbox, Kafka, distributed lock or milestone 6 delivery work.

## Milestone 5: How are asynchronous delivery and cache consistency handled?

**Base answer:** The database owns task truth. A conditional `PENDING -> RUNNING` update supplies basic idempotency, result/interval/task-success commit atomically, and the broker delivery is ACKed only afterward. Temporary failures use a bounded TTL retry queue; exhausted and permanent failures are recorded and dead-lettered.

**Why not claim exactly once?** RabbitMQ is at-least-once and task creation publishes after its database commit. Publisher confirms detect many failures but do not remove every crash window. Duplicate work is suppressed by state; a Transactional Outbox would close the creation dual-write gap but is intentionally outside this release.

**How is Redis safe?** Ownership is checked first, keys include `userId`, values are explicit JSON DTOs, and only latest/comparison summaries are cached. Successful writes invalidate both affected views after commit. Redis failures degrade to database reads and do not reverse committed business data.
