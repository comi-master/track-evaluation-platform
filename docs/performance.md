# Performance Evidence

## Milestone 6 measured engineering smoke

On 2026-07-17, commit worktree based on `477fbb0` was measured once after review fixes on Windows, Intel Core i7-14650HX, 34,075,090,944 bytes physical memory, Docker Desktop Linux containers and Java 17. The deterministic input had 10,000 rows and was 253,877 bytes. Results: upload 59.6 ms, parse plus 500-row batch persistence 332.3 ms, synchronous analysis 145.4 ms, and asynchronous create/poll-to-success 233.5 ms. The returned point count was 10,000, RMSE 0.3605246177447699, and abnormal count 1,428 for threshold 0.5.

This was one local engineering smoke run with no warm-up or repetitions. It is not a production benchmark, capacity result, SLO, or throughput claim, and no improvement percentage is inferred. The script deletes its generated CSV exactly and retains its uniquely named business record for inspection; it never deletes existing volumes or unrelated data.

## Planned environment record

Milestone 6 will record the exact commit, JDK, Maven, OS, CPU, memory, MySQL version/configuration, container resources, JVM options, batch size, file size, row count, warm-up, repetitions, and statistic used for each basic measurement actually performed.

## Planned datasets and method

- Deterministic generators: 10, 1,000, and 100,000 rows; 1,000,000 rows is optional and outside the normal suite.
- Compare single inserts with batches of 200, 500, and 1,000 on the same machine and data.
- Separate parse, database, analysis, and total duration; approximate peak memory and batch count.
- Run warm-up and multiple measured repetitions, reporting median or clearly labeled mean.
- Compare only implemented paths. Cache hit/miss or pagination comparisons are optional unless the milestone 6 plan explicitly includes them.

## Reproduction

With the five-service Compose stack healthy, run `.\scripts\performance-smoke.ps1 -Rows 10000`. The script records file size, each measured phase, returned metrics, CPU and physical memory. Larger generated files remain temporary and are never committed.

## Query plans and limitations

The milestone 1 integration suite executed `EXPLAIN` for owner-scoped active-dataset pagination on MySQL 8.4 and confirmed that the SQL is explainable while separately verifying the declared index columns and order. The fixture is intentionally small, so the suite does not force a particular optimizer choice and no speedup, throughput, or large-data conclusion is claimed. Milestone 6 adds only the single local engineering measurement recorded above.

The active route has seven milestones numbered 0-6. The former dedicated performance milestone in the 0-12 route is historical. Only the measured values above are claimed; no unmeasured capacity conclusion is implied.

Milestone 4 bounds analysis memory with configurable 1,000-point keyset batches (`sequence_no > cursor`, ascending, limited). Welford statistics and current interval state are streaming; completed intervals are retained only until the short batch insert. This is a design property, not a latency, throughput, or peak-memory measurement.

Milestone 5 moves long analysis work off the request thread but makes no throughput claim. RabbitMQ prefetch/concurrency remain conservative defaults, retries are bounded and delayed, and messages carry only a version plus task ID. Redis caches only the two repeated summary queries with 10-minute/5-minute TTLs. These are bounded design choices; no cache hit-rate, latency or messages-per-second number is claimed without a recorded benchmark.
