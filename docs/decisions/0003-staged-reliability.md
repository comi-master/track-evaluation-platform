# ADR 0003: Synchronous-first analysis and staged reliability components

- Status: Proposed; implementation begins in later milestones
- Date: 2026-07-15

## Context

The target flow spans MySQL, MinIO, RabbitMQ, and Redis. Implementing every distributed failure mode before the parser and analysis rules are proven would obscure business correctness.

## Decision

Build and test CSV ingestion plus analysis synchronously before adding RabbitMQ. Then use durable RabbitMQ queues, bounded retry/DLQ, manual ACK, and idempotent consumers for long-running tasks. Add a Transactional Outbox because database commit and broker publish cannot be one local transaction; accept eventual consistency and possible duplicate delivery rather than introduce a distributed transaction coordinator.

MinIO and MySQL likewise cannot share a local transaction, so uploads need compensation/reconciliation. Redis is limited to refresh/token state, result cache, blacklist, and atomic rate limits; it is not a second database. Testcontainers will verify real middleware semantics and outage paths as each adapter arrives.

## Consequences

The project gains diagnosable increments and a working fallback path. Later messaging adds state, retry, monitoring, and duplicate-handling complexity. Outbox reduces lost-event risk but does not make cross-system work atomic. Redis-dependent security/rate operations need explicit fail-open/fail-closed decisions, while result reads can degrade to MySQL.
