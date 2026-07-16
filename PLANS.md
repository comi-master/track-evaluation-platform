# Delivery Plan

Status values: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `COMPLETE`. A milestone becomes `COMPLETE` only after its commands and acceptance checks actually pass.

## Milestone roadmap

| # | Goal and modules changed | Acceptance conditions | Test/verification commands | Status |
| --- | --- | --- | --- | --- |
| 0 | Planning and runnable skeleton: repository governance, Maven, `common`, Actuator, ping, tests, Compose | Clean verify and formatting pass; app starts; ping and health respond; four dependency containers are healthy | `./mvnw spotless:check`, `./mvnw clean verify` (use `mvnw.cmd` on Windows), `docker compose --env-file .env config --quiet`, app smoke test | COMPLETE |
| 1 | Database foundation: `user`, `dataset`, `track`, `task`, `report`, `audit`, `outbox`, Flyway, MyBatis-Plus/XML | Empty MySQL migrates; mapper integration tests pass; core SQL and real `EXPLAIN` are documented | `./mvnw clean verify -P integration` (use `mvnw.cmd` on Windows), migration smoke test, `EXPLAIN` script | NOT_STARTED |
| 2 | Authentication/authorization: `auth`, `user`, Redis-backed refresh tokens, Spring Security | Register/login/refresh/logout/me work; 401/403 differ; roles and ownership are enforced; security tests pass | targeted auth tests plus `./mvnw clean verify` (use `mvnw.cmd` on Windows) | NOT_STARTED |
| 3 | Dataset and object storage: `dataset`, `storage`, MinIO upload, SHA-256, ownership | Dataset CRUD and logical deletion work; streamed CSV upload reaches MinIO; duplicate and compensation paths are tested | dataset/service/MockMvc tests and MinIO Testcontainers tests | NOT_STARTED |
| 4 | CSV ingestion: `track`, streaming Commons CSV parser, validation, MyBatis batch insert | STRICT parsing handles required edge cases; 10/1k/100k generators exist; batch persistence is transactional and tested | parser unit tests, MySQL integration tests, `./mvnw clean verify` (use `mvnw.cmd` on Windows) | NOT_STARTED |
| 5 | Synchronous analysis closure: `analysis`, `task`, `report` | Welford statistics, RMSE, abnormal intervals, result persistence, and template report work end to end synchronously | algorithm boundary tests and synchronous workflow integration test | NOT_STARTED |
| 6 | RabbitMQ asynchronous tasks: `task`, `messaging` | Legal state machine, manual ACK, bounded retries, DLQ, idempotent duplicates, and concurrent claiming are tested | RabbitMQ Testcontainers failure/idempotency tests | NOT_STARTED |
| 7 | Transactional Outbox: `outbox`, `messaging`, `task` | Task/event commit atomically; broker outage retains events; recovery republishes; duplicates remain safe | transaction rollback, outage/recovery, and duplicate publish tests | NOT_STARTED |
| 8 | Cache and rate limiting: `analysis`, `auth`, shared Redis adapter | Cache-aside hit/miss/invalidation, TTL jitter, safe degradation, and Lua rate limiting are tested | Redis Testcontainers and unavailable-Redis tests | NOT_STARTED |
| 9 | Observability and audit: `audit`, logging, metrics, Prometheus, Grafana | Required metrics, MDC, operation log, provisioning, and a working dashboard are demonstrated | actuator/metrics tests and Compose dashboard smoke test | NOT_STARTED |
| 10 | Measured performance: generators, benchmark scripts, SQL/index tuning | Repeated same-machine measurements and `EXPLAIN` evidence exist; no invented values | `scripts/benchmark.ps1` / `.sh`, query plans, reproducibility checks | NOT_STARTED |
| 11 | Engineering closeout: OpenAPI, CI, Docker image, complete diagrams/docs/demo | CI passes, image builds, documented one-command demo is reproducible, docs match code, security review is clean | GitHub Actions, image and demo smoke tests, full `./mvnw clean verify` (use `mvnw.cmd` on Windows) | NOT_STARTED |
| 12 | Resume/interview evidence | Only verified implementation, incidents, measurements, and tests are used for introductions, 60 question chains, and resume bullets | manual evidence trace to code/test/report artifacts | NOT_STARTED |

## Current milestone 0

### Objective

Create a minimal, maintainable Spring Boot 3.5.15/Java 17 baseline without prematurely integrating later middleware SDKs.

### Modules and files

- Governance: `AGENTS.md`, this plan, README, base docs, ADRs, ignore/environment templates.
- Build: `pom.xml`, Maven Wrapper, Spotless, JaCoCo.
- Runtime: application entry point, `Result<T>`, stable error codes, business/global exceptions, request ID filter, ping, Actuator.
- Infrastructure definition only: MySQL, Redis, RabbitMQ, and MinIO in `compose.yaml`.
- Tests: context load, ping contract, request ID behavior, and exception envelope.

### Acceptance checklist

- [x] Required environment commands were attempted and their actual output recorded below.
- [x] Git repository initialized.
- [x] Planning/governance documents created.
- [x] Maven/Java 17 project skeleton and package-by-feature placeholders created.
- [x] Base HTTP/error/request ID/Actuator implementation created.
- [x] Four-service Compose definition and `.env.example` created.
- [x] `.\mvnw.cmd spotless:apply` executed successfully.
- [x] `.\mvnw.cmd clean verify` executed successfully with 19 tests and no skipped tests.
- [x] Application startup, `/api/v1/ping`, and `/actuator/health` smoke-tested.
- [x] `docker compose --env-file .env config --quiet` succeeds without printing resolved secrets.
- [x] MySQL, Redis, RabbitMQ, and MinIO were pulled, started, observed healthy, and passed service-specific checks.
- [x] Verification completed with `java` and Maven Wrapper both using JDK 17.0.14 from `D:\java\JDK17`.
- [x] Final `git diff` reviewed; `git diff --check` passed, generated/tool caches are ignored, and no staged content or committed secret was found.

### Final verification record (2026-07-16, Asia/Shanghai)

- Java and build: `java` and Maven Wrapper both used JetBrains OpenJDK 17.0.14 from `D:\java\JDK17`. Maven Wrapper 3.3.4 ran Apache Maven 3.9.11. Compilation used release 17, and the application class has bytecode major version 61.
- Formatting and tests: `.\mvnw.cmd spotless:apply`, `.\mvnw.cmd spotless:check`, and `.\mvnw.cmd clean verify` passed. Surefire ran 19 tests with 0 failures, 0 errors, and 0 skipped; JaCoCo analyzed 8 application classes. Real MockMvc validation now covers field errors, object-level errors, unreadable JSON, deterministic multiple-error ordering, and safe messages; request ID tests assert header/body equality and MDC cleanup on downstream failure. Static Compose regression tests cover the credential-safe health checks and validated Redis entrypoint.
- Dependencies: `.\mvnw.cmd dependency:tree -Dverbose` passed with no Maven warnings and no `omitted for conflict` entries. Direct dependencies remain limited to Web, Validation, Actuator, Prometheus registry, and the test starter.
- Docker environment: Docker Desktop 4.82.0, Docker Client/Engine 29.6.1 on Linux/amd64, Compose v5.3.0, and WSL 2.7.10.0 with Ubuntu on WSL 2 were available.
- Compose: secret-safe configuration validation passed. Two initial pulls failed with transient Docker Hub `EOF` errors; the third finite retry succeeded without changing image tags. The final 2026-07-16 acceptance used `docker compose --env-file .env up -d --force-recreate` for all four containers while preserving every named volume.
- Runtime services: MySQL 8.4.10, Redis 8.2.7 Alpine, RabbitMQ 4.2.8 management, and MinIO release `2025-09-07T16-13-09Z` all reached `healthy`. All six published ports default to `127.0.0.1`. MySQL and Redis health checks pass credentials by process environment rather than arguments. Redis enforces a 16-128-character safe alphabet, regenerates its `600 redis:redis` temporary configuration after restart, and keeps the real value out of `Config.Cmd`, PID 1 arguments, health-check metadata, and logs.
- Service checks: the MySQL application account connected to `track_analysis` and executed a simple query; authenticated Redis `PING` returned `PONG`; RabbitMQ node ping, application-user presence, authenticated management API, AMQP port, and management port passed; MinIO API/Console ports and live-health endpoint passed.
- Application smoke test: the packaged JAR started on JDK 17.0.14; `/actuator/health` returned `UP`, `/api/v1/ping` returned `SUCCESS`, and the supplied request ID was preserved. The smoke-test process stopped and no matching JAR process remained.
- Environment scripts: the real PowerShell and Git Bash POSIX environment checks both returned 0. Isolated PowerShell and POSIX harnesses each passed six cases: normal, missing Docker client, missing Git, wrong Java, missing wrapper, and unreachable Docker server. The Redis password-policy harness passed one valid GUID-style value and rejected spaces, double quotes, backslashes, and a too-short value without echoing them.
- Security and Git: `.env` is ignored and untracked, contains all required variables without placeholder values, and was normalized to UTF-8 without BOM. No `.env` value matched the application smoke logs. `git diff --check` passed; the repository remains uncommitted as requested.

### Historical verification record

Before Java 17 became the project baseline, an earlier build used Oracle JDK 25.0.3 with compiler release 21 and also ran seven tests successfully. That result is retained only as history and is not the basis for milestone 0 acceptance.

### Remaining scope boundary

Milestone 0 is complete. No database schema, authentication, object-storage integration, analysis workflow, messaging consumer, cache behavior, or report business function has been implemented. Stop here and wait for an explicit instruction before entering milestone 1.

## Project-level Definition of Done register

The final project must verify all original conditions: full Maven/Spotless verification; empty-database Flyway initialization; healthy Compose dependencies; registration/login and ownership; dataset/upload/streaming batch ingestion; asynchronous RabbitMQ task processing with retry/DLQ/idempotency; Outbox outage recovery; correct statistics and intervals; progress/result cache/report queries with Redis degradation; MockMvc and Testcontainers coverage; reproducible README and CI; measured performance only; secret scanning; documentation/code consistency; and resume material limited to completed evidence. Until milestones 0-12 are complete, this register remains open.
