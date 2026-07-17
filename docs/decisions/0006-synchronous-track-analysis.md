# 0006: Synchronous track analysis

Milestone 4 stores each successful calculation as an immutable `analysis_result` and its contiguous abnormal intervals. The read/compute phase uses keyset batches ordered by `sequence_no`, so it does not retain the entire point set or hold a long database transaction. A short transaction writes the result and intervals together.

Errors are three-dimensional Euclidean distance. Welford's algorithm produces population standard deviation; a point is abnormal only when `error > threshold`. `track_point` remains raw input and does not store `position_error`. RabbitMQ, Redis business caching, analysis tasks, reports and milestone 5 work are deliberately excluded.
