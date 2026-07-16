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
