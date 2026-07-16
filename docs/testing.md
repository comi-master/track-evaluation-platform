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

## Commands

```powershell
.\mvnw.cmd spotless:check
.\mvnw.cmd clean verify
.\scripts\test-check-environment.ps1
bash ./scripts/test-redis-password-policy.sh
```

Run `./scripts/test-check-environment.sh` from a POSIX shell for the equivalent script regression suite. Run `./scripts/test-redis-password-policy.sh` to verify the Redis entrypoint policy without starting a container.

The default suite must not need manually running infrastructure. Container-backed `*IT` tests will be bound to Maven verification when their dependencies are introduced.

## Failure scenarios by roadmap

- Milestone 1: migration, mapper SQL, rollback, uniqueness, optimistic/conditional updates.
- Milestone 2: duplicate user, bad password, expired token, ownership denial, pagination and search boundaries.
- Milestone 3: invalid/empty file, BOM/header/column/numeric/NaN/infinity/duplicate-time failures, MinIO/database partial failure, and batch rollback.
- Milestone 4: RMSE distinction, extrema/standard deviation, abnormal-point and interval boundaries, multi-source comparison, and synchronous workflow integration.
- Milestone 5: duplicate MQ delivery, consumer failure, retry exhaustion, RabbitMQ/Redis outage, legal task-state transitions, and cache invalidation.
- Milestone 6: report history, OpenAPI/image/demo acceptance, necessary end-to-end integration, and reproducible basic performance measurement.

No later failure scenario is marked tested until its test has actually run.

The active test route follows milestones 0-6; milestone 0 is complete and milestone 1 is next but has not started. Transactional Outbox recovery and Prometheus/Grafana platform tests are outside the first-release suite unless separately approved. The former 0-12 route is a historical plan, has been retired, and is not the current execution route.

## Recorded milestone 0 result

On 2026-07-16, `.\mvnw.cmd clean verify` ran 19 tests with 0 failures, 0 errors, and 0 skipped on JetBrains OpenJDK 17.0.14 while compiling with release 17. MockMvc covered `/api/v1/ping` and actual validation dispatch; focused tests covered safe binding responses, deterministic error ordering, and MDC cleanup. Both environment-check harnesses passed all six cases, and the Redis policy harness passed all five cases. The packaged application returned `UP` from `/actuator/health` and `SUCCESS` from `/api/v1/ping` on the same JDK. Docker-backed Compose validation confirmed all four milestone 0 services healthy and practically reachable after the credential-handling changes. An earlier JDK 25/release 21 run is historical only.
