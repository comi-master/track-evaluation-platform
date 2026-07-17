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
