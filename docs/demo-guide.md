# V1.0 可重复端到端演示

## 演示内容

本项目解决多来源三维航迹文件的安全存储、解析、误差分析、来源对比和 HTML 报告留档问题。演示数据不是客户、试验或涉密数据，而是脚本用公开公式和固定随机种子 `20260719` 生成的合成数据。

三份 CSV 共享相同的 2000 点真实轨迹和 0.1 秒时间间隔。真实轨迹包含 x 方向轻微加速、y 方向平滑转弯和 z 方向小幅周期变化。FUSION、RADAR、INFRARED 依次使用较小、中等、较大噪声，并分别加入 1、2、3 组连续偏移区间。统一异常阈值为 60 米；设计目标是三者都有正常点和异常点，且 FUSION 的 RMSE、平均误差和异常比例低于 RADAR，RADAR 低于 INFRARED。

## 准备与运行

需要 JDK 17、正在运行的 Docker Desktop/Engine 和 Compose v2。先从 `.env.example` 创建被忽略的 `.env`，替换所有占位值；不要打印 `.env` 或解析后的 Compose 配置。

```powershell
Copy-Item .env.example .env
$env:JAVA_HOME='D:\java\JDK17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
docker compose --env-file .env config --quiet
```

只启动基础设施并手工启动应用：

```powershell
docker compose --env-file .env up -d
Get-Content .env | Where-Object { $_ -match '^[A-Z][A-Z0-9_]*=' } | ForEach-Object { $n,$v=$_ -split '=',2; Set-Item "Env:$n" $v }
.\mvnw.cmd spring-boot:run
```

只生成并自检仿真数据：

```powershell
.\scripts\generate-demo-tracks.ps1
```

一条命令执行完整演示：

```powershell
.\scripts\demo.ps1
```

`demo.ps1` 会校验 Java/Docker/环境，按需启动基础设施和应用，生成数据，创建带唯一后缀的临时用户和数据集，真实调用 API 完成三个来源的上传、解析和同步分析，对 FUSION 执行 RabbitMQ 异步分析，验证 Redis 缓存的建立、失效与重建，生成并下载 HTML 报告，最后写出安全摘要。它只停止自己启动的应用进程，不删除 Compose 数据卷、MinIO bucket 或演示业务数据。

## 查看结果

- Swagger UI：<http://127.0.0.1:8080/swagger-ui.html>
- RabbitMQ 管理界面：<http://127.0.0.1:15672>。账号来自本机 `.env`；正常结束时 `track.analysis.queue` 和 `track.analysis.dead.queue` 均无消息堆积。
- Redis：可在容器内用 `redis-cli` 检查 `analysis:latest:<userId>:<fileId>` 和 `analysis:comparison:<userId>:<datasetId>`；脚本使用容器环境中的密码，不把密码放入命令参数或摘要。
- HTML 报告：`demo-output/reports/`。
- 机器可读摘要：`demo-output/demo-summary.json`；便于阅读的摘要：`demo-output/demo-summary.md`。

正常结果应包括四个基础设施容器 healthy、应用 health 为 UP、Swagger HTTP 200、三个文件均为 PARSED 且各 2000 点、同步和异步分析成功、缓存验证全部为 true、主队列和死信队列深度为 0、报告包含三个来源，并满足 `FUSION < RADAR < INFRARED` 的质量排序。

## 常见故障

- Java 检查失败：确认 `JAVA_HOME` 指向真实 JDK 17；JDK 21/25 不能替代本次验收。
- Compose 配置失败：检查 `.env` 是否存在、是否仍含占位值以及 Redis 密码是否符合仓库策略。不要输出凭据排查。
- Docker 不可用：启动 Docker Desktop/Engine 后重试 `docker version`。
- 容器未 healthy：运行 `docker compose --env-file .env ps`，再针对单个服务查看日志；不要执行 `down -v`。
- 应用未 UP：查看被忽略的 `demo-output/application.log` 和 `application-error.log`，修复后重新运行脚本。
- HTTP 或异步任务失败：脚本会在固定超时内停止并只报告安全的请求路径/状态；结合应用日志和 RabbitMQ 管理界面定位，不要跳过失败步骤或直接改数据库。
- 用户名或数据集冲突：脚本默认加入时间戳和随机后缀，可安全重复运行；每次会生成新的临时密码且不会输出或保存它。
