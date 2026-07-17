# Architecture

## Current milestone 3 acceptance

The application remains one Spring Boot process. Spring Security authenticates one signed JWT Access Token, reloads the current user for every protected request, and rejects deleted, disabled, or invalidated accounts. Application services own transactions; feature-local MyBatis-Plus mappers include ownership in SQL. MinIO-backed track-file upload and synchronous CSV parsing are implemented; analysis, messaging, cache and reports remain deferred.

```mermaid
flowchart TD
    Request["HTTP request"] --> RequestId["RequestIdFilter"]
    RequestId --> Security["JWT filter / SecurityContext"]
    Security --> Auth["auth application service"]
    Security --> Dataset["dataset application service"]
    Auth --> UserMapper["sys_user mapper"]
    Dataset --> DatasetMapper["owner-scoped dataset mapper"]
    Auth --> Result["Result<T>"]
    Dataset --> Result
    Error["Exception"] --> Handler["GlobalExceptionHandler"]
    Handler --> Result
    Actuator["Spring Boot Actuator"] --> Health["health / info / metrics / prometheus"]
    Flyway["Flyway V1/V2/V3"] --> MySQL["MySQL: sys_user / dataset"]
    UserMapper["user persistence mapper"] --> MySQL
    DatasetMapper["dataset persistence mapper"] --> MySQL
```

## Target modular monolith

Feature modules own their HTTP, application, domain, and infrastructure code. Cross-feature interaction should enter through application/domain contracts rather than reaching into another feature's mapper or SDK.

```mermaid
flowchart LR
    Common["common"]
    Auth["auth/user"]
    Dataset["dataset/storage"]
    Track["track"]
    Analysis["analysis/task/report"]
    Delivery["messaging"]
    Audit["audit"]
    Infra["MySQL / Redis / RabbitMQ / MinIO"]
    Common --> Auth
    Common --> Dataset
    Common --> Track
    Common --> Analysis
    Auth --> Dataset
    Dataset --> Track
    Track --> Analysis
    Analysis --> Delivery
    Auth --> Audit
    Dataset --> Audit
    Delivery --> Infra
    Dataset --> Infra
    Auth --> Infra
    Track --> Infra
```

The diagram expresses intended dependency flow, not completed code.

## Target upload and analysis sequence

```mermaid
sequenceDiagram
    actor User
    participant API
    participant MySQL
    participant MinIO
    participant RabbitMQ
    participant Worker
    User->>API: Upload CSV
    API->>MinIO: Stream object and SHA-256
    API->>MySQL: Save file metadata
    API->>MySQL: Save task state
    API->>RabbitMQ: Publish analysis task
    RabbitMQ->>Worker: Analysis task
    Worker->>MinIO: Stream CSV
    Worker->>MySQL: Batch points + result + intervals
    Worker-->>RabbitMQ: ACK after success
```

This sequence will be introduced incrementally: database persistence in milestone 1, authentication/datasets in milestone 2, storage and streaming ingestion in milestone 3, synchronous analysis closure in milestone 4, and messaging/cache in milestone 5. Transactional Outbox is outside the first release and requires separate approval.

## Target cache flow

Result queries use cache-aside: Redis hit returns a JSON value; a miss reads MySQL and fills a bounded-TTL entry. Task completion commits MySQL first and invalidates the old key. Redis outage should degrade result queries to MySQL. This is planned for milestone 5; complex rate limiting is outside the first release.

## Implemented authorization flow

Spring Security runs statelessly with CSRF, form login and HTTP Basic disabled. The JWT filter verifies signature, issuer and expiry, then loads `sys_user` and compares `authVersion` before building an immutable principal. Logout atomically increments `auth_version`, invalidating every previously issued token for that user. Dataset reads, writes and deletes require `(dataset.id, current user_id, deleted = 0)` in Mapper SQL; inaccessible resources return 404. There is one ordinary-user role only and no RBAC table.

## Delivery boundary

The active route contains seven milestones numbered 0-6. Milestones 0-3 are complete; milestone 4 has not started. The first release remains a modular monolith and prioritizes a demonstrable upload-analysis-report business closure over infrastructure breadth. The former 0-12 route is historical. Transactional Outbox, microservice decomposition, and a Prometheus/Grafana platform are future extensions only.

## Dependency rules

1. Controllers depend on application use cases and boundary DTO/VO types.
2. Application code owns orchestration and transactions; it does not concatenate SQL.
3. Domain rules do not import MinIO, Redis, RabbitMQ, servlet, or mapper SDKs.
4. Infrastructure adapters implement explicit ports only when isolation has value.
5. No external network call is held inside a long database transaction.

## Implemented milestone 3 ingestion flow

Upload and parse are deliberately separate synchronous APIs. Upload streams the multipart body to a permission-controlled temporary file while calculating SHA-256, writes the raw object under `{userId}/{datasetId}/{uuid}.csv`, then inserts immutable `track_file` metadata. If the database insert fails, only the newly written object is removed on a best-effort basis.

Parse first atomically claims `UPLOADED` or `FAILED` as `PARSING`. The MinIO object is copied to a bounded temporary file outside a database transaction. A single database transaction then parses that local file incrementally, writes 500-row MyBatis XML batches, and marks `PARSED`; any row or batch failure rolls back every point. A separate short transaction marks `FAILED` with a bounded safe summary. MySQL and MinIO do not share a local transaction, this is minimal compensation rather than distributed atomicity, and no Outbox is implemented.
