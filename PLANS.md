# Delivery Plan

Status values: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `COMPLETE`. A milestone becomes `COMPLETE` only after its commands and acceptance checks actually pass.

## Active roadmap

The only active delivery route contains seven milestones, numbered 0-6. Milestones 0-5 are complete; milestone 6 has not started. Business closure takes priority over accumulating infrastructure components.

The former milestone 0-12 route is a historical plan, has been retired, and is not the current execution route. It must not be restored from old tasks, prompts, or repository history.

| # | Goal and scope | Acceptance focus | Status |
| --- | --- | --- | --- |
| 0 | Engineering foundation and development environment: Java 17, Spring Boot 3.5.15, Maven Wrapper, response/error/request ID conventions, Compose for MySQL/Redis/RabbitMQ/MinIO, baseline tests, engineering and security rules | Recorded formatting/build/application/Compose acceptance is complete | COMPLETE |
| 1 | Database and persistence foundation: Flyway, MyBatis-Plus, necessary MyBatis XML, core business tables, database conventions, indexes/constraints, Testcontainers MySQL, persistence integration tests | Empty database migrates; mapper and persistence integration tests pass; indexes and constraints match verified access paths | COMPLETE |
| 2 | User authentication and dataset management: registration/login, BCrypt, Spring Security, JWT, logout, user data isolation, dataset CRUD, pagination and search | Authentication/authorization and ownership boundaries are tested; dataset operations are usable | COMPLETE |
| 3 | CSV upload, MinIO and streaming parsing: validation, SHA-256 deduplication, raw object storage, Apache Commons CSV, streaming parsing, batch track-point persistence, error-row location | Valid files complete the upload-to-persistence path; invalid and partial-failure paths are tested without loading a complete upload into memory | COMPLETE |
| 4 | Complete track-analysis business closure: 3D position error, mean error, RMSE, extrema, standard deviation, abnormal points and continuous intervals, multi-source comparison, synchronous analysis and result query | The first complete, demonstrable and resume-usable business workflow works end to end, with truthful algorithm and integration evidence | COMPLETE |
| 5 | RabbitMQ asynchronous tasks and Redis cache: PENDING/RUNNING/SUCCESS/FAILED, manual ACK, bounded retry, DLQ, basic message idempotency, result cache and invalidation | Async state/failure/idempotency behavior and cache hit/miss/invalidation are tested; Transactional Outbox is not implemented | COMPLETE |
| 6 | Reports, testing, delivery and interview material: template reports/history, OpenAPI, Dockerfile, sample CSV, necessary integration tests, basic performance measurement, README, architecture/ER diagrams, demo, resume bullets and interview Q&A | Reproducible delivery evidence matches implemented code; performance and resume claims are based only on measured or verified results | NOT_STARTED |

## First-release exclusions

Unless separately approved by the user, the first release does not include Transactional Outbox, microservice decomposition, Kubernetes, Nacos, Sentinel, Elasticsearch, Kafka, Prometheus/Grafana platform deployment, complex rate limiting, complex distributed locks, multi-tenancy, a complete frontend, or AI-generated reports. They may be mentioned briefly as future extensions, but are not milestone acceptance conditions or Definition of Done requirements.

Existing milestone 0 Actuator and Micrometer Prometheus endpoint support is baseline application instrumentation, not a commitment to deploy a Prometheus/Grafana monitoring platform.

## Unified milestone workflow

1. Prepare the stage plan.
2. Obtain user confirmation of scope.
3. Implement code.
4. Run relevant tests and `clean verify`.
5. Complete necessary Docker or integration acceptance.
6. Perform one independent review.
7. Fix substantiated High and Medium findings.
8. Create the Git commit.
9. Enter the next milestone only after explicit continuation.

Do not repeat open-ended infrastructure review cycles unless a severe issue such as credential exposure, data loss, or build failure requires it.

## Milestone 0 completion record

Milestone 0 was completed by commit `a636d09` (`chore: initialize project foundation`). The accepted scope includes Java 17, Spring Boot 3.5.15, Maven Wrapper, `Result<T>`, centralized exception handling, request ID and MDC behavior, Docker Compose definitions for MySQL/Redis/RabbitMQ/MinIO, baseline tests, and engineering/security conventions.

The recorded 2026-07-16 acceptance used JetBrains OpenJDK 17.0.14 and Maven 3.9.11. Spotless checks and `clean verify` passed with 19 tests and no failures, errors, or skips. The packaged application smoke test passed, and all four Compose services reached healthy and passed the recorded service checks. These are milestone 0 results only; no later business feature is thereby claimed.

Milestone 1 has Flyway V1/V2, `sys_user` and `dataset`, feature-local MyBatis-Plus mappers, UTC persistence configuration, and isolated MySQL 8.4 Testcontainers coverage. Java 17 ordinary verification ran 19 tests without Docker; `-Pit` ran those 19 plus 20 persistence ITs, all with zero failures, errors, or skips. Local application smoke testing applied V1/V2 once, observed no repeat migration, returned `UP` and `SUCCESS`, preserved request ID, and stopped cleanly. Independent review completed with no Critical or High findings, and its documentation/test-evidence findings were corrected and reverified.

Milestone 2 implements registration/login/current-user/logout with BCrypt, stateless Spring Security, a single JWT Access Token and database-backed `auth_version` invalidation. Dataset CRUD, owner-scoped pagination/literal name search, logical deletion and optimistic locking are implemented without Refresh Token, Redis login state, RBAC, upload or analysis behavior. Java 17 ordinary verification ran 49 tests without Docker; `-Pit` ran those 49 plus 31 MySQL 8.4 integration tests, all with zero failures, errors or skips. The Compose application smoke test passed the full authentication/dataset/logout flow, old-token 401, health, request ID and sensitive-log checks. Independent review reported 0 Critical, 0 High, 4 Medium and 2 Low; all six findings were fixed and the complete verification was rerun.

Milestone 3 adds V4/V5 `track_file`/`track_point`, private MinIO storage, streamed multipart hashing and upload, fixed seven-column Commons CSV parsing with physical line errors, guarded parse states, 500-row XML batch insertion, ownership-scoped file/point APIs, and minimal MinIO/MySQL compensation. Java 17 ordinary verification ran 63 tests; `clean verify -Pit` ran those 63 plus 36 MySQL/MinIO integration tests, all with zero failures, errors or skips. Compose smoke testing verified health, upload, parse, point pagination, duplicate 409, invalid-file FAILED with zero points, owner isolation, request ID consistency, sensitive-log scanning, clean MinIO cleanup, and application shutdown. One independent review found 0 Critical, 0 High, 5 Medium and 2 Low; all five Medium findings were fixed and both verification layers were rerun.

Milestone 4 adds V6/V7 immutable analysis results and abnormal intervals, synchronous owner-scoped analysis, configurable keyset point scanning, Welford population statistics, dynamic error series, latest/history/interval queries and per-file latest dataset comparison. Java 17 ordinary verification ran 74 tests; `clean verify -Pit` ran those 74 plus 42 isolated MySQL/MinIO integration tests, all with zero failures, errors or skips. Compose smoke testing passed the three-source upload/parse/analyse/query workflow, hand-calculated metrics, latest-only comparison, owner/state/request-ID/sensitive-log checks, exact temporary cleanup and clean shutdown. The single independent review found 0 Critical, 0 High, 2 Medium and 1 Low; all were fixed and reverified.

Milestone 5 introduces V8 `analysis_task`, owner-scoped asynchronous task APIs, durable direct RabbitMQ main/retry/dead-letter topology, publisher confirms and mandatory returns, manual consumer ACK, bounded retry, database-state idempotency, and Redis Cache-Aside for latest result and dataset comparison only. Java 17 ordinary verification ran 89 tests; `clean verify -Pit` ran those 89 plus 44 isolated integration tests, all with zero failures, errors or skips. Compose smoke passed three-source upload/parse/synchronous analysis, asynchronous creation/completion/history, latest-only comparison and cache invalidation, ownership/state/validation/request-ID/sensitive-log checks, exact cleanup and clean application shutdown. The single independent review found 0 Critical, 1 High, 2 Medium and 0 Low; the High and both Medium findings were fixed and reverified. Transactional Outbox, report generation, Kafka and milestone 6 behavior remain excluded.

## Project-level Definition of Done

The first release is complete only after milestones 0-6 are accepted and recorded honestly. Required evidence includes:

- reproducible Java 17 Maven/Spotless verification and necessary integration tests;
- migration-backed database initialization and tested persistence paths;
- registration/login, ownership isolation, dataset management, upload, streaming batch ingestion, synchronous analysis, and result queries;
- asynchronous RabbitMQ processing with bounded retry, DLQ and basic idempotency, plus Redis result-cache behavior;
- template reports, OpenAPI, Docker image, sample data, diagrams, demo instructions, and synchronized documentation;
- basic performance measurements with environment and method recorded, never invented values;
- resume and interview material traceable to completed code, tests, or measured evidence.

Transactional Outbox and the other first-release exclusions are not part of this Definition of Done.

## Historical roadmap note

The repository previously described milestones 0-12 (13 milestones), including separate Outbox, observability platform, performance, closeout, and interview stages. That is a historical plan, has been retired, and is not the current execution route. Its useful goals were either consolidated into milestones 0-6 or moved to future extensions; it has no active acceptance authority.
