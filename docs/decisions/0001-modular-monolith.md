# ADR 0001: Package-by-feature modular monolith

- Status: Accepted
- Date: 2026-07-15

## Context

The product is one upload-analysis-report workflow, is developed as a portfolio project, and needs truthful end-to-end reliability evidence. Splitting early would add network contracts, deployment units, tracing, and distributed consistency before independent scaling or team ownership is demonstrated.

## Decision

Use one Spring Boot deployable organized by feature. Keep infrastructure behind useful ports, maintain dependency direction, and allow future extraction only with measured operational justification. Do not introduce Elasticsearch: the first release uses ownership, time, error sorting, and status filters that MySQL indexes can serve and verify with `EXPLAIN`.

## Consequences

Local transactions and debugging are simpler; deployment is one unit. Module boundaries require review/tests rather than process isolation. A hot module initially scales with the whole application. This cost is preferable until evidence supports extraction.
