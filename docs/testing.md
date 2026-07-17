# Testing Strategy

## Test pyramid

1. Pure unit tests for algorithms, validation, state machines, tokens, and report formatting.
2. Application-service tests with Mockito for orchestration, ownership, and failure branches.
3. MockMvc slice tests for validation, envelopes, HTTP status, authentication, and authorization.
4. Testcontainers integration tests for real MySQL, Redis, RabbitMQ, and MinIO behavior introduced by each milestone.
5. A small end-to-end upload-to-report test after the synchronous and asynchronous flows exist.

## Milestone 0 suite

- Spring application context load without external services.
- Ping response, request ID propagation, missing/unsafe request ID replacement, and exact response-header/body equality.
- Request ID filter MDC cleanup after both successful and exceptional downstream chains.
- Business, binding, unreadable-request, and unexpected exception mapping, including global validation errors and safe fallback messages.
- Real MockMvc request validation for field errors, object-level errors, unreadable JSON, and stable multiple-error ordering.
- Compose credential-handling structure and Redis password-policy behavior, including safe rejection without value disclosure.
- PowerShell and POSIX environment-check regression harnesses for normal and five failure modes.

## Commands and profile boundary

```powershell
.\mvnw.cmd spotless:check
.\mvnw.cmd clean verify
.\mvnw.cmd clean verify -Pit
.\scripts\test-check-environment.ps1
bash ./scripts/test-redis-password-policy.sh
```

Run `./scripts/test-check-environment.sh` from a POSIX shell for the equivalent script regression suite. Run `./scripts/test-redis-password-policy.sh` to verify the Redis entrypoint policy without starting a container.

The default suite runs ordinary tests and does not activate Failsafe or Testcontainers. The `it` profile binds Failsafe to `*IT`, requires Docker, and runs isolated MySQL 8.4 containers. Docker unavailability fails `-Pit` explicitly; it is never converted to a skipped test.

## Milestone 1 persistence suite

- `FlywayMigrationIT`: empty schema, V1/V2 history, success/checksum, repeat migrate, UTC session.
- `DatabaseConstraintIT`: table/column/default metadata, PK/UK/FK/CHECK/NOT NULL, permanent case-insensitive username uniqueness, owner-page index order.
- `SysUserMapperIT`: insert/query/generated ID, UTC fills, logical delete, optimistic lock, BlockAttack.
- `DatasetMapperIT`: owner relation, pagination and stable ordering, logical delete, owner isolation, optimistic lock, executable `EXPLAIN`.

Every IT uses a JUnit-managed Testcontainer with independent credentials and database. Class-level Spring transactions roll back fixtures, contexts and containers close after each class, and no test reads the local Compose datasource.

## Milestone 2 authentication and dataset suite

- Pure and Mockito tests cover username normalization/validation, request validation, BCrypt orchestration, JWT signature/issuer/expiry, JSON 401/403 handlers, filter branches, ownership and optimistic-conflict translation.
- `V2ToV3MigrationIT` verifies a real v2 schema upgrades to v3 and that `auth_version` is NOT NULL with default zero.
- `AuthenticationApiIT` verifies registration normalization, permanent uniqueness, BCrypt storage, uniform login failures, disabled users, protected API access, invalid/expired JWTs, logout invalidation, and OpenAPI exposure.
- `DatasetApiIT` verifies authenticated CRUD, fixed pagination order, bounded keyword search, logical deletion, optimistic conflict, and 404 owner isolation for read/update/delete.

## Failure scenarios by roadmap

- Milestone 1: migration, mapper SQL, rollback, uniqueness, optimistic/conditional updates.
- Milestone 2: duplicate user, bad password, expired token, ownership denial, pagination and search boundaries.
- Milestone 3: invalid/empty file, BOM/header/column/numeric/NaN/infinity/duplicate-time failures, MinIO/database partial failure, and batch rollback.
- Milestone 4: RMSE distinction, extrema/standard deviation, abnormal-point and interval boundaries, multi-source comparison, and synchronous workflow integration.
- Milestone 5: duplicate MQ delivery, consumer failure, retry exhaustion, RabbitMQ/Redis outage, legal task-state transitions, and cache invalidation.
- Milestone 6: report history, OpenAPI/image/demo acceptance, necessary end-to-end integration, and reproducible basic performance measurement.

No later failure scenario is marked tested until its test has actually run.

The active test route follows milestones 0-6. Milestone 2 verification is complete; milestone 3 has not started. Transactional Outbox recovery and Prometheus/Grafana platform tests remain outside the first-release suite.

## Recorded milestone 0 result

On 2026-07-16, `.\mvnw.cmd clean verify` ran 19 tests with 0 failures, 0 errors, and 0 skipped on JetBrains OpenJDK 17.0.14 while compiling with release 17. MockMvc covered `/api/v1/ping` and actual validation dispatch; focused tests covered safe binding responses, deterministic error ordering, and MDC cleanup. Both environment-check harnesses passed all six cases, and the Redis policy harness passed all five cases. The packaged application returned `UP` from `/actuator/health` and `SUCCESS` from `/api/v1/ping` on the same JDK. Docker-backed Compose validation confirmed all four milestone 0 services healthy and practically reachable after the credential-handling changes. An earlier JDK 25/release 21 run is historical only.

## Recorded milestone 1 result

On 2026-07-16, Java 17 ordinary verification ran 19 tests without Docker, and `clean verify -Pit` ran those 19 plus 20 MySQL persistence integration tests. Both runs had 0 failures, 0 errors, and 0 skipped tests. MySQL 8.4 containers were created and removed by Testcontainers; the local Compose database was not used by ITs.

## Recorded milestone 2 result

On 2026-07-17, Java 17 ordinary verification ran 49 tests without Docker. After independent-review fixes, `clean verify -Pit` ran those 49 ordinary tests plus 31 MySQL 8.4 integration tests; both layers had 0 failures, 0 errors, and 0 skipped tests. Testcontainers were removed after execution and did not use the local Compose database. The post-review Compose smoke test passed registration, login, current user, dataset create/read/update/page, logout invalidation, health, request ID equality, sensitive-log scanning, and process shutdown.
