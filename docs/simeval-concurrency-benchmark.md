# SimBench 局域网并发验收

`scripts/simeval-concurrency-smoke.ps1` 是可重复的本地并发验收脚本。它使用管理员账号创建临时数据集和已解析航迹文件，然后模拟多个虚拟用户并发创建异步分析任务，等待 RabbitMQ Worker 完成任务，最后自动删除本轮临时数据。

## 运行方式

在项目根目录执行：

```powershell
.\scripts\simeval-concurrency-smoke.ps1 `
  -AdminUsername "<已有管理员账号>" `
  -AdminPassword "<已有管理员密码>" `
  -VirtualUsers 100 `
  -TasksPerUser 10
```

默认会产生 1000 个异步任务。管理员账号只用于本地验收，脚本不会把账号写入仓库，也不会把密码写入日志。当前 Docker 配置中的 `RABBITMQ_CONCURRENCY` 和 `RABBITMQ_MAX_CONCURRENCY` 决定 Worker 并发度；修改后重建 app 容器，再分别运行同一组参数，才能形成可比较的 2、4、8 Worker 数据。

## 指标解释

- `SUCCESS`、`FAILED`、`FAILURE_RATE_PERCENT`：任务结果和失败率。
- `THROUGHPUT_TASKS_PER_SECOND`：从提交完成到所有任务结束的任务吞吐量。
- `TASK_AVERAGE_MS`、`TASK_P95_MS`：数据库记录的单任务创建到完成耗时。
- `QUEUE_PEAK_MESSAGES`：轮询 RabbitMQ 队列得到的峰值 ready/unacknowledged 消息数。
- `SUBMIT_HTTP_MS`：1000 个创建请求全部返回所需时间，反映 API 写入压力，不等于任务处理耗时。

脚本模拟的是 100 个并发客户端身份下的请求压力；为了避免创建 100 个长期账号，验收数据使用同一管理员身份。它证明的是 API、数据库、可靠发布和 Worker 队列的并发行为，不应被表述为 100 个真实租户之间的权限隔离测试。权限隔离仍由业务验收脚本单独验证。

## 当前本地实测记录

在本机 Docker 环境、8 个 RabbitMQ Worker、prefetch=1、outbox batch-size=100 下，执行 100 个虚拟用户 × 10 个任务：

- 1000/1000 成功，失败率 0%
- 提交请求窗口 3114.5ms
- 全部完成窗口 41.18s
- 吞吐量 24.28 tasks/s
- 单任务平均耗时 21825.47ms，P95 39449.41ms
- 观测到的 RabbitMQ 队列峰值 2 条消息

这组数字只代表当前电脑、当前数据规模和当前容器配置。它说明系统在该配置下完成了 1000 个异步任务，不等于生产环境容量上限。此前单条 outbox 发布器在 300 秒内只完成 291 个任务；现已改为每轮最多批量发布 100 条，并通过本轮 1000 任务验收。

## 故障恢复验收

故障恢复不能用一次普通压测结果代替。需要在一轮小任务运行期间停止 app 容器，等待任务租约超时后重新启动，再确认任务最终 `SUCCESS`。该操作会改变本地容器状态，应作为单独的人工验收步骤执行，并记录租约配置、停止时刻、恢复时刻和最终任务状态。
