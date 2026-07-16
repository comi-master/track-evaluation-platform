# Repository Working Agreement

This file is the operating contract for contributors and coding agents. Read it before changing the repository. The project is delivered milestone by milestone; do not implement a later milestone unless the user explicitly asks to continue.

## Project structure

- `src/main/java/com/example/trackanalysis/common`: cross-cutting API, exception, logging, validation, security, configuration, and small stateless utilities.
- `src/main/java/com/example/trackanalysis/<feature>`: package-by-feature modules (`auth`, `user`, `dataset`, `storage`, `track`, `analysis`, `task`, `report`, `audit`, and `messaging`). A legacy `outbox` placeholder may remain from the retired roadmap, but Transactional Outbox is outside the first release unless separately approved.
- A feature may contain `controller`, `application`, `domain`, `infrastructure`, `dto`, `vo`, `converter`, and `mapper` only when the layer has real code.
- `src/main/resources`: Spring configuration and, from milestone 1 onward, Flyway migrations and MyBatis XML.
- `src/test`: mirrors production packages. Pure unit tests end in `Test`; container-backed tests end in `IT`.
- `docs`: architecture, database, API, testing, performance, interview evidence, and ADRs.
- `docker`: small container-side startup helpers required by `compose.yaml`; they must validate configuration and must never print credentials.
- `scripts`: PowerShell and POSIX shell automation with equivalent behavior where practical.

## Commands

Use Java 17. The Maven Wrapper is the reproducible entry point.

| Purpose | PowerShell | POSIX shell |
| --- | --- | --- |
| Compile | `.\mvnw.cmd -DskipTests compile` | `./mvnw -DskipTests compile` |
| Start | `.\mvnw.cmd spring-boot:run` | `./mvnw spring-boot:run` |
| Unit and integration verification | `.\mvnw.cmd clean verify` | `./mvnw clean verify` |
| Apply formatting | `.\mvnw.cmd spotless:apply` | `./mvnw spotless:apply` |
| Check formatting | `.\mvnw.cmd spotless:check` | `./mvnw spotless:check` |
| Dependency inspection | `.\mvnw.cmd dependency:tree` | `./mvnw dependency:tree` |
| Environment check | `.\scripts\check-environment.ps1` | `./scripts/check-environment.sh` |
| Environment-check regression | `.\scripts\test-check-environment.ps1` | `./scripts/test-check-environment.sh` |
| Redis password-policy regression | `bash ./scripts/test-redis-password-policy.sh` | `./scripts/test-redis-password-policy.sh` |
| Start dependencies | `docker compose up -d` | `docker compose up -d` |
| Validate Compose | `docker compose --env-file .env config --quiet` | same |

Before development, copy `.env.example` to the ignored `.env` and replace every placeholder. Never commit `.env`.

For local acceptance, never print resolved Compose configuration or `.env` values. Validate with `docker compose --env-file .env config --quiet`, and do not start containers while any required value still contains a placeholder. Middleware ports must default to `127.0.0.1`; widening `BIND_ADDRESS` requires an explicit local decision. Health checks must pass credentials through container environment variables, never command arguments. `REDIS_PASSWORD` must contain 16-128 ASCII letters, digits, dots, underscores, or hyphens so its generated configuration is unambiguous; validation failures must not echo the value. The supported build runtime and compiler release are both Java 17; verification on JDK 21 or JDK 25 does not replace a real JDK 17 run.

## Java and package conventions

- Base package is `com.example.trackanalysis`; package names are lowercase singular nouns.
- Organize by business feature, not global controller/service/mapper buckets.
- Controllers translate HTTP only. Application services own use-case orchestration and transaction boundaries. Domain code owns rules. Infrastructure code implements external adapters.
- Use constructor injection. Field injection and service locators are forbidden.
- Separate request DTOs, response VOs, and persistence entities. Never expose an entity by default.
- Create an interface only for multiple implementations, infrastructure isolation, or a stable domain port.
- Prefer immutable records/value objects at boundaries. Avoid global mutable state and unexplained utility classes.
- Important states are enums. Magic numbers, silent catches, unexplained TODOs, and cyclic dependencies are forbidden.

## Database conventions

- MySQL 8.4, InnoDB, and `utf8mb4` are the baseline.
- Every schema change is a new versioned Flyway migration; never edit an already released migration.
- Tables and columns use `snake_case`; Java fields use `camelCase`.
- Use `BIGINT` identifiers, UTC timestamps, explicit `NOT NULL`, and deliberate logical/physical deletion.
- Design indexes from verified SQL access paths. Unique constraints are a final idempotency defense, not a substitute for domain rules.
- Do not use `SELECT *`, SQL string concatenation in services, per-row transactions, or large loops of single-row statements.
- Complex queries and batch inserts use MyBatis XML and require integration tests plus documented `EXPLAIN` evidence.

## API conventions

- Public application endpoints use `/api/v1`.
- Responses use `Result<T>` with stable business code, safe message, data, request ID, and timestamp.
- Use meaningful HTTP status codes; do not turn every error into HTTP 200.
- Validate input at the boundary and centralize exception translation.
- Enforce ownership in application/query boundaries, not only in controllers.
- List endpoints must cap page size and explicitly whitelist sort fields.
- Never expose stack traces, credentials, internal object keys, password hashes, or complete tokens.

## Testing conventions

- Test pure rules without Spring; use Mockito for application-service collaborators; use MockMvc for HTTP/security behavior.
- Persistence and middleware behavior needs Testcontainers integration tests when its milestone introduces the component.
- Tests must be deterministic, isolated, repeatable, and must not require manually prepared local services.
- Never disable real validation or mock every dependency just to make a test pass.
- JaCoCo identifies gaps; do not game coverage or claim unmeasured percentages.
- A defect fix includes a regression test when practical.

## Logging conventions

- Use SLF4J placeholders. Include request context through MDC (`requestId`, then later `userId`, `datasetId`, and `taskId`).
- Preserve exception causes and stack traces in internal error logs while returning safe public messages.
- Never log plaintext passwords, full JWTs, infrastructure credentials, or complete CSV content.
- Record a task's safe `errorStage` and error code when task processing exists.

## Forbidden actions

- The only active roadmap has seven milestones numbered 0-6. Milestone 0 is complete and milestone 1 is next but not started; the former 0-12 route is a historical plan, has been retired, and is not the current execution route.
- Do not jump ahead of the active milestone or claim unfinished features.
- Do not introduce a middleware SDK without documenting its problem, cost, test strategy, and degradation path.
- Do not commit secrets, generated build output, benchmark claims without measurements, or resume claims without code/test evidence.
- Do not load a complete upload into memory, run uncontrolled network calls inside long database transactions, or assume database and object storage/message broker share a transaction.
- Do not split this modular monolith into artificial microservices.

## Definition of Done

A milestone follows the unified workflow in `PLANS.md`: plan, user scope confirmation, implementation, tests and `clean verify`, necessary Docker/integration acceptance, one independent review, fixes for substantiated High and Medium findings, commit, then explicit continuation. A milestone is done only when its acceptance conditions are recorded honestly. The whole first release is done only after milestones 0-6 are verified; Transactional Outbox and complex monitoring platforms are not first-release completion conditions.
