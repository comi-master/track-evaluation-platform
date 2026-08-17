# Delivery Plan

Status values: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `COMPLETE`. A milestone becomes `COMPLETE` only after its commands and acceptance checks actually pass.

## Active roadmap

The only active delivery route contains seven milestones, numbered 0-6. Milestones 0-6 and the first release are complete. Business closure takes priority over accumulating infrastructure components.

The former milestone 0-12 route is a historical plan, has been retired, and is not the current execution route. It must not be restored from old tasks, prompts, or repository history.

| # | Goal and scope | Acceptance focus | Status |
| --- | --- | --- | --- |
| 0 | Engineering foundation and development environment: Java 17, Spring Boot 3.5.15, Maven Wrapper, response/error/request ID conventions, Compose for MySQL/Redis/RabbitMQ/MinIO, baseline tests, engineering and security rules | Recorded formatting/build/application/Compose acceptance is complete | COMPLETE |
| 1 | Database and persistence foundation: Flyway, MyBatis-Plus, necessary MyBatis XML, core business tables, database conventions, indexes/constraints, Testcontainers MySQL, persistence integration tests | Empty database migrates; mapper and persistence integration tests pass; indexes and constraints match verified access paths | COMPLETE |
| 2 | User authentication and dataset management: registration/login, BCrypt, Spring Security, JWT, logout, user data isolation, dataset CRUD, pagination and search | Authentication/authorization and ownership boundaries are tested; dataset operations are usable | COMPLETE |
| 3 | CSV upload, MinIO and streaming parsing: validation, SHA-256 deduplication, raw object storage, Apache Commons CSV, streaming parsing, batch track-point persistence, error-row location | Valid files complete the upload-to-persistence path; invalid and partial-failure paths are tested without loading a complete upload into memory | COMPLETE |
| 4 | Complete track-analysis business closure: 3D position error, mean error, RMSE, extrema, standard deviation, abnormal points and continuous intervals, multi-source comparison, synchronous analysis and result query | The first complete, demonstrable and resume-usable business workflow works end to end, with truthful algorithm and integration evidence | COMPLETE |
| 5 | RabbitMQ asynchronous tasks and Redis cache: PENDING/RUNNING/SUCCESS/FAILED, manual ACK, bounded retry, DLQ, basic message idempotency, result cache and invalidation | Async state/failure/idempotency behavior and cache hit/miss/invalidation are tested; Transactional Outbox is not implemented | COMPLETE |
| 6 | Reports, testing, delivery and interview material: template reports/history, OpenAPI, Dockerfile, sample CSV, necessary integration tests, basic performance measurement, README, architecture/ER diagrams, demo, resume bullets and interview Q&A | Reproducible delivery evidence matches implemented code; performance and resume claims are based only on measured or verified results | COMPLETE |

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

Milestone 6 adds V9 immutable `analysis_report`, latest-per-file dataset comparison snapshots, owner-scoped metadata/history/content/download APIs, self-contained escaped HTML, explicit report OpenAPI contracts, synthetic samples, demo/performance scripts, a Java 17 non-root multi-stage Docker image and final synchronized documentation/interview material. Java 17 ordinary verification ran 91 tests and final `clean verify -Pit` ran those 91 plus 46 isolated integration tests, all with zero failures, errors or skips. The five-service Compose stack reached healthy, Flyway reached V9, Swagger/OpenAPI and the three-source sync/async/report demo passed, the image ran as UID 10001 without `.env`, and application logs contained no checked credential or complete JWT. A 10,000-point local engineering smoke (253,877 bytes) measured upload 59.6 ms, parse 332.3 ms, synchronous analysis 145.4 ms and async end-to-end 233.5 ms; it is explicitly not a production benchmark. The single independent review found 0 Critical, 0 High, 2 Medium and 1 Low; all findings were fixed and the relevant full test, performance, OpenAPI and container acceptance was rerun. The application container was stopped without deleting middleware containers or named volumes. The first release is COMPLETE.

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

## Post-release compatibility extension

Status: `IN_PROGRESS` (2026-07-21). This separately approved extension preserves the completed REST/JWT and three-source track-analysis release while adding Redis Session and Thymeleaf browser access. Implemented and exercised: separated security chains, JWT filter registration isolation, V10 roles/audit schema, default-disabled public registration, idempotent initial-admin bootstrap, login/dashboard/profile/dataset/task/result/user/audit pages, user detail and role editing, administrator and self-service password changes, indexed Redis Session storage, and transaction-after-commit invalidation after account status, password, or role changes. Java 17 ordinary verification passes 106 unit/MockMvc tests and the complete `-Pit` run passes 46 MySQL 8.4 Testcontainers integration tests, all with zero failures, errors, or skips. A real Compose HTTP smoke created the researcher through the web form and verified simultaneous Session/JWT authentication, authorization, BCrypt persistence, audit rows, principal indexes, disable/enable, password reset, self-service password change, and role change without exposing credentials. The rebuilt app and four middleware containers remained healthy. The extension is not COMPLETE: dataset web upload/download/delete/detail, a full browser-driven upload-to-RabbitMQ-result workflow, independent Redis Testcontainers coverage, manual visual browser acceptance, complete business-operation audit coverage, and an executed concurrent two-administrator race test remain open.

2026-07-22 continuation remains `IN_PROGRESS`. Dataset upload/detail/download/deletion, task cancel/poll/result binding, administrator global dataset/task query scope, business audit, Redis Testcontainers (4 tests), and a real two-administrator MySQL race test (1 test) are implemented. An independent review found four High issues; RUNNING-task crash recovery, parse-failure object cleanup, multi-object deletion refusal, and exact task-result binding received fixes. The final post-fix Java 17 `clean verify -Pit` passed 107 Surefire plus 51 Failsafe tests with zero failures/errors/skips; Spotless, JaCoCo report generation, and `git diff --check` passed. The rebuilt image is current and all five services are healthy. The dedicated full business smoke script and the remaining real-browser pages are still required. Browser security stopped the visual workflow after a successful real upload and explicitly prohibited circumvention, so visual acceptance and the extension as a whole must not be marked COMPLETE. Detailed evidence is in `docs/web-business-acceptance.md`.

2026-07-22 V12 closure work remains `IN_PROGRESS`. The implementation now contains token-owned renewable task leases, expiry-only recovery, token-guarded terminal writes, a recoverable dataset deletion state machine, a conditionally claimed reliable Outbox for object cleanup and task publication, transaction-coupled deletion/task audit, and `scripts/smoke-web-business.ps1`. Java 17 Surefire passes 109 tests. MySQL 8.4 successfully migrated an empty schema through V12; the first new lease integration run found annotation-SQL escaping and that defect was fixed. Execution-credit exhaustion blocked the required post-fix `clean verify -Pit`, image rebuild, Compose smoke scripts, and independent re-review. Browser policy separately blocks localhost and explicitly prohibits circumvention. No final completion claim is made.
