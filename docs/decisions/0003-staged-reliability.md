# ADR 0003: Synchronous-first analysis and staged reliability components

- Status: Accepted as first-release scope
- Date: 2026-07-15

## Context

The target flow spans MySQL, MinIO, RabbitMQ, and Redis. Implementing every distributed failure mode before the parser and analysis rules are proven would obscure business correctness.

## Decision

Build and test CSV ingestion plus analysis synchronously before adding RabbitMQ. Then use durable RabbitMQ queues, bounded retry/DLQ, manual ACK, and basic idempotent handling for long-running tasks. The first release will not implement Transactional Outbox. Database state and broker publication must not be described as atomic; the limitation and applicable failure behavior must remain explicit.

MinIO and MySQL likewise cannot share a local transaction, so uploads need compensation/reconciliation. Redis supports the authentication design and analysis-result cache where introduced; it is not a second database. Complex rate limiting is outside the first release. Testcontainers will verify real middleware semantics and relevant outage paths as each adapter arrives.

## Consequences

The project gains diagnosable increments and reaches the business closure before asynchronous infrastructure is added. Messaging still adds state, retry, failure, and duplicate-handling complexity. Without Outbox, cross-system publication has an acknowledged reliability gap; bounded retry and basic idempotency reduce some failure impact but do not create atomicity. Result reads can degrade to MySQL when Redis is unavailable.

Transactional Outbox may be reconsidered as a future extension only with separate user approval and a documented problem, cost, test strategy, and degradation path. The former 0-12 roadmap that required it was a historical plan, has been retired, and is not the current execution route.
