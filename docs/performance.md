# Performance Evidence

## Status

No benchmark has been executed. There are no parsing, insertion, analysis, memory, throughput, cache, pagination, or optimization numbers to report.

## Planned environment record

Milestone 6 will record the exact commit, JDK, Maven, OS, CPU, memory, MySQL version/configuration, container resources, JVM options, batch size, file size, row count, warm-up, repetitions, and statistic used for each basic measurement actually performed.

## Planned datasets and method

- Deterministic generators: 10, 1,000, and 100,000 rows; 1,000,000 rows is optional and outside the normal suite.
- Compare single inserts with batches of 200, 500, and 1,000 on the same machine and data.
- Separate parse, database, analysis, and total duration; approximate peak memory and batch count.
- Run warm-up and multiple measured repetitions, reporting median or clearly labeled mean.
- Compare only implemented paths. Cache hit/miss or pagination comparisons are optional unless the milestone 6 plan explicitly includes them.

## Results

Pending. Empty by design to prevent invented measurements.

## Query plans and limitations

The milestone 1 integration suite executed `EXPLAIN` for owner-scoped active-dataset pagination on MySQL 8.4 and confirmed that the SQL is explainable while separately verifying the declared index columns and order. The fixture is intentionally small, so the suite does not force a particular optimizer choice and no speedup, latency, throughput, or large-data conclusion is claimed. Basic performance measurements remain pending milestone 6.

The active route has seven milestones numbered 0-6. The former dedicated performance milestone in the 0-12 route is a historical plan, has been retired, and is not the current execution route. No performance result is claimed until it is measured.

Milestone 4 bounds analysis memory with configurable 1,000-point keyset batches (`sequence_no > cursor`, ascending, limited). Welford statistics and current interval state are streaming; completed intervals are retained only until the short batch insert. This is a design property, not a latency, throughput, or peak-memory measurement.

Milestone 5 moves long analysis work off the request thread but makes no throughput claim. RabbitMQ prefetch/concurrency remain conservative defaults, retries are bounded and delayed, and messages carry only a version plus task ID. Redis caches only the two repeated summary queries with 10-minute/5-minute TTLs. These are bounded design choices; no cache hit-rate, latency or messages-per-second number is claimed without a recorded benchmark.
