# Performance Evidence

## Milestone 6 measured engineering smoke

On 2026-07-17, commit worktree based on `477fbb0` was measured once after review fixes on Windows, Intel Core i7-14650HX, 34,075,090,944 bytes physical memory, Docker Desktop Linux containers and Java 17. The deterministic input had 10,000 rows and was 253,877 bytes. Results: upload 59.6 ms, parse plus 500-row batch persistence 332.3 ms, synchronous analysis 145.4 ms, and asynchronous create/poll-to-success 233.5 ms. The returned point count was 10,000, RMSE 0.3605246177447699, and abnormal count 1,428 for threshold 0.5.

This was one local engineering smoke run with no warm-up or repetitions. It is not a production benchmark, capacity result, SLO, or throughput claim, and no improvement percentage is inferred. The script deletes its generated CSV exactly and retains its uniquely named business record for inspection; it never deletes existing volumes or unrelated data.

## Local asynchronous concurrency baseline

On 2026-08-17, commit `aa0aa47` was tested against the running Docker Compose stack on Windows 11, Intel Core i7-14650HX, 34,075,090,944 bytes physical memory. The test used a temporary ordinary user, one eight-row parsed CSV file, 100 concurrent virtual users, and 10 asynchronous analysis submissions per user (1,000 tasks total). RabbitMQ was configured with 8 consumers, a maximum concurrency of 8, and `prefetch=1`.

Measured output from `scripts/simeval-concurrency-smoke.ps1`:

| Metric | Result |
| --- | ---: |
| Task submissions | 1,000 |
| HTTP submission window | 2,937.9 ms |
| Successful tasks | 1,000 |
| Failed tasks | 0 |
| Failure rate | 0% |
| Completion window | 42.16 s |
| Throughput | 23.72 tasks/s |
| Task average duration | 22,368.94 ms |
| Task P95 duration | 40,294.84 ms |
| Queue peak | 2 messages |

This is a local engineering baseline, not a production capacity claim. The successful completion rate demonstrates that the current asynchronous task path handled this workload without observed task loss or failure. The gap between the short submission window and the longer task P95 includes queue wait and database/task-state contention; it must not be interpreted as 40 seconds of pure metric-computation time.

Further experiments should compare the same deterministic workload with one variable changed at a time: intermediate consumer counts, database connection-pool limits, and batch size. Each variant should use warm-up plus repeated runs and report median, P95, throughput, failure rate, and queue peak. No concurrency tuning should be claimed until that A/B record exists.

### Worker-concurrency comparison

Using the same workload immediately after the baseline, the application was temporarily recreated with 2 consumers and then restored to 8 consumers. Both runs completed all 1,000 tasks successfully:

| Consumers | Submission window | Completion window | Throughput | Task average | Task P95 | Failures |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 2 | 3.20 s | 43.02 s | 23.25 tasks/s | 22.45 s | 40.53 s | 0 |
| 8 | 2.94 s | 42.16 s | 23.72 tasks/s | 22.37 s | 40.29 s | 0 |

A third run with 8 consumers and a temporary Hikari maximum pool size of 20 produced 23.57 tasks/s, a 42.42 s completion window, and a 40.94 s P95, again with 1,000 successes and 0 failures. The pool was restored to the default maximum of 10 after the run.

The measured difference is small, so this workload does not justify claiming that simply increasing RabbitMQ consumers improves performance. It indicates that the next optimization target should be the repeated analysis of the same file and its database read/write path: measure SQL time, transaction wait, connection-pool saturation, and per-task CPU separately before changing the architecture. The 8-consumer configuration was restored after the comparison.

### Post-query-optimization comparison

After `TrackPointMapper.selectAfterSequence` was narrowed to the columns consumed by the streaming analysis (`sequence_no`, time, true position, and track position), the same 100-user/1,000-task workload was repeated with the default 8 consumers and Hikari maximum pool size 10:

| Metric | Result |
| --- | ---: |
| Task submissions | 1,000 |
| HTTP submission window | 3,497.4 ms |
| Successful tasks | 1,000 |
| Failed tasks | 0 |
| Failure rate | 0% |
| Completion window | 43.27 s |
| Throughput | 23.11 tasks/s |
| Task average duration | 22,594.85 ms |
| Task P95 duration | 40,803.89 ms |
| Queue peak | 2 messages |

The run remained fully successful, but one run is not evidence of a performance improvement: throughput and P95 were within the expected variance of the previous baseline. The change is therefore recorded as a low-risk reduction in unused database columns and result payload, not as a measured throughput gain. A repeated-file cache or a more detailed SQL/connection-pool profile is the next evidence-based optimization target.

### Outbox polling optimization comparison

The next run kept the same 100-user/1,000-task workload, 8 RabbitMQ consumers, Hikari maximum pool size 10, and the reduced point query. The Outbox polling interval was changed from the previous 1,000 ms default to a configurable 250 ms default:

| Metric | Previous run | 250 ms polling |
| --- | ---: | ---: |
| Successful tasks | 1,000 | 1,000 |
| Failed tasks | 0 | 0 |
| Completion window | 43.27 s | 35.92 s |
| Throughput | 23.11 tasks/s | 27.84 tasks/s |
| Task average duration | 22.59 s | 19.09 s |
| Task P95 duration | 40.80 s | 34.31 s |
| Queue peak | 2 messages | 2 messages |

The observed run improved throughput by approximately 20.5% and reduced the completion window by approximately 17.0%. These are two local engineering runs rather than a production capacity claim; repeated runs on the same host are still required before treating the percentage as a stable benchmark result. The stage metrics for the optimized run recorded average queue wait of 19.06 s, Outbox publication of 1.23 ms, point reads of 0.82 ms, metric computation of 0.01 ms, and result writes of 18.38 ms.

## Runtime stage metrics

The application now exposes the low-cardinality Micrometer timer `track.analysis.stage` through Actuator. Its `stage` tag contains `task.queue.wait`, `task.execution`, `outbox.publish`, `points.read`, `metrics.compute`, and `result.write`. The timer is created on first use, so a fresh process may return 404 for this metric until an analysis task has completed. The database pool is explicitly configurable through `MYSQL_POOL_MAX_SIZE`, `MYSQL_POOL_MIN_IDLE`, `MYSQL_POOL_CONNECTION_TIMEOUT_MS`, and `MYSQL_POOL_VALIDATION_TIMEOUT_MS`; the defaults preserve the previous Hikari pool size while making A/B tuning reproducible. Outbox polling is configurable through `OUTBOX_POLL_MILLISECONDS` and defaults to 250 ms so a newly created task does not wait for a full one-second polling interval before publication.

A post-deployment one-task smoke confirmed the metric endpoint was created and reported all five analysis stages. The metric intentionally does not include task ID, user ID, or file ID tags, preventing high-cardinality monitoring data from becoming a second performance problem.

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
