# Delivery Plan

Status values: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `COMPLETE`. A milestone becomes `COMPLETE` only after its commands and acceptance checks actually pass.

## Active roadmap

The only active delivery route contains seven milestones, numbered 0-6. Milestone 0 is complete; milestone 1 is the next stage and has not started. Business closure takes priority over accumulating infrastructure components.

The former milestone 0-12 route is a historical plan, has been retired, and is not the current execution route. It must not be restored from old tasks, prompts, or repository history.

| # | Goal and scope | Acceptance focus | Status |
| --- | --- | --- | --- |
| 0 | Engineering foundation and development environment: Java 17, Spring Boot 3.5.15, Maven Wrapper, response/error/request ID conventions, Compose for MySQL/Redis/RabbitMQ/MinIO, baseline tests, engineering and security rules | Recorded formatting/build/application/Compose acceptance is complete | COMPLETE |
| 1 | Database and persistence foundation: Flyway, MyBatis-Plus, necessary MyBatis XML, core business tables, database conventions, indexes/constraints, Testcontainers MySQL, persistence integration tests | Empty database migrates; mapper and persistence integration tests pass; indexes and constraints match verified access paths | NOT_STARTED |
| 2 | User authentication and dataset management: registration/login, BCrypt, Spring Security, JWT, logout, user data isolation, dataset CRUD, pagination and search | Authentication/authorization and ownership boundaries are tested; dataset operations are usable | NOT_STARTED |
| 3 | CSV upload, MinIO and streaming parsing: validation, SHA-256 deduplication, raw object storage, Apache Commons CSV, streaming parsing, batch track-point persistence, error-row location | Valid files complete the upload-to-persistence path; invalid and partial-failure paths are tested without loading a complete upload into memory | NOT_STARTED |
| 4 | Complete track-analysis business closure: 3D position error, mean error, RMSE, extrema, standard deviation, abnormal points and continuous intervals, multi-source comparison, synchronous analysis and result query | The first complete, demonstrable and resume-usable business workflow works end to end, with truthful algorithm and integration evidence | NOT_STARTED |
| 5 | RabbitMQ asynchronous tasks and Redis cache: PENDING/RUNNING/SUCCESS/FAILED, manual ACK, bounded retry, DLQ, basic message idempotency, result cache and invalidation | Async state/failure/idempotency behavior and cache hit/miss/invalidation are tested; Transactional Outbox is not implemented | NOT_STARTED |
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

Milestone 1 has not started. There are no Flyway migrations, persistence entities/mappers, authentication, upload workflow, analysis workflow, messaging consumer, result cache, or report business functions yet.

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
