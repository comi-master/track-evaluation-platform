# 多源航迹分析与报告管理平台

Java 17 / Spring Boot 3.5 模块化单体后端，完成注册登录、用户数据隔离、CSV 流式解析、MinIO 原文件存储、MySQL 批量入库、同步与 RabbitMQ 异步分析、Redis Cache-Aside，以及不可变 HTML 分析报告。

## 技术栈与架构

Spring MVC、Security/JWT、Validation、MyBatis-Plus/MyBatis XML、Flyway、MySQL 8.4、MinIO、RabbitMQ、Redis、Springdoc、Testcontainers。控制器只处理 HTTP；应用服务编排用例和事务；规则与状态位于业务模块；基础设施适配器隔离数据库和中间件。

## 从零运行

要求 JDK 17、Docker Desktop/Engine 与 Compose v2。复制 `.env.example` 为被 Git 忽略的 `.env`，替换所有占位凭据；端口默认只绑定 `127.0.0.1`。

```powershell
Copy-Item .env.example .env
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d
Get-Content .env | Where-Object { $_ -match '^[A-Z][A-Z0-9_]*=' } | ForEach-Object { $n,$v=$_ -split '=',2; Set-Item "Env:$n" $v }
.\mvnw.cmd spring-boot:run
```

本地应用：`http://127.0.0.1:8080`；Swagger UI：`/swagger-ui.html`；OpenAPI：`/v3/api-docs`；健康检查：`/actuator/health`。

容器化运行应用及四个依赖：

```powershell
docker compose --env-file .env -f compose.yaml -f compose.app.yaml up -d --build
docker compose -f compose.yaml -f compose.app.yaml ps
```

镜像使用 Java 17 多阶段构建，运行层仅包含 JRE 和应用 JAR，并以 UID 10001 非 root 用户运行。不要使用 `docker compose down -v`，除非明确要删除所有命名卷。

## 核心流程

注册/登录 → 创建数据集 → 上传三个来源 CSV → 流式解析与批量入库 → 同步或异步分析 → latest/comparison 查询 → 生成报告 → 查询历史 → 在线查看或下载 HTML。

`samples/` 是人工构造的非敏感小样例。可重复的完整演示会用固定种子自动生成三份 2000 点仿真航迹，然后真实执行上传、解析、同步/异步分析、Redis 缓存和 HTML 报告链路：

```powershell
$env:JAVA_HOME='D:\java\JDK17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\scripts\demo.ps1
```

脚本会按需启动四个 Compose 依赖和本地应用，完成后只停止本次启动的应用进程。它不会打印或保存密码、完整 JWT、Secret，也不会删除数据卷、bucket 或业务记录。生成的 CSV、摘要、日志和报告均写入被 Git 忽略的 `demo-output/`。完整说明见 `docs/demo-guide.md`。

## 验证

```powershell
$env:JAVA_HOME='D:\java\JDK17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spotless:apply
.\mvnw.cmd spotless:check
.\mvnw.cmd clean verify
docker version
docker info
.\mvnw.cmd clean verify -Pit
.\mvnw.cmd dependency:tree -Dverbose
```

`-Pit` 使用 Testcontainers 验证 MySQL 8.4 等真实基础设施。性能烟测生成临时合成 CSV，不提交大文件；结果和限制记录在 `docs/performance.md`。

## 环境变量

必需项为 MySQL 用户/密码、JWT Secret、Redis 密码、RabbitMQ 用户/密码、MinIO 用户/密码。完整清单和安全占位值见 `.env.example`。Redis 密码限定为 16–128 位 ASCII 字母、数字、点、下划线或连字符。

## 文档

- `docs/api.md`：接口和状态码
- `docs/architecture.md`：上下文、模块与关键流程图
- `docs/database.md`：V1–V9、表、外键和索引
- `docs/testing.md`：测试策略与验收证据
- `docs/performance.md`：本机工程烟测方法、实测数据与限制
- `docs/interview.md`：简历描述、一分钟/三分钟介绍和追问

## 七个里程碑

0 工程骨架、1 持久化、2 认证与数据集、3 CSV/MinIO、4 同步分析闭环、5 RabbitMQ/Redis、6 报告与最终交付。状态和真实验收记录以 `PLANS.md` 为准。

## 已知限制

第一版不包含 PDF/Word/Excel 导出、AI 报告、完整前端、邮件/定时/审批、Transactional Outbox、微服务、Kubernetes、Kafka 或 Prometheus/Grafana 平台。HTML 报告是生成时快照，不作算法显著性、吞吐量或生产部署结论。
