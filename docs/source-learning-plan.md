# 航迹仿真评测平台源码学习计划

## 学习目标

完成后能够从开发人员角度独立说明：项目解决什么问题、一次请求如何进入系统、数据如何落库、CSV 如何解析、指标如何计算、异步任务如何投递和恢复、用户如何隔离，以及如何定位常见故障。

## 阶段安排

### 阶段 0：建立全局地图

学习项目定位、用户流程、模块边界、启动方式、Docker 依赖和数据库表关系。重点文件：`README.md`、`docs/final-version-baseline.md`、`pom.xml`、`compose.yaml`、`application.yml`、`TrackAnalysisApplication.java`。

产出：能够画出“浏览器/API → Controller → Application Service → Mapper/Storage/Queue → 数据库”的架构图。

### 阶段 1：Spring Boot 启动与配置

学习 Spring Boot 自动配置、Bean 扫描、Profile、配置绑定、健康检查、MyBatis-Plus、Flyway 和 Docker 环境变量。

重点回答：应用为什么能启动、依赖为什么要先健康、配置如何从 `.env` 进入容器、数据库迁移何时执行。

### 阶段 2：认证、注册与多用户隔离

学习 REST JWT 与网页 Session 的双安全链路、登录注册、BCrypt、角色、CSRF、`auth_version`、用户名规范化和数据所有权校验。

重点文件：`auth`、`user`、`SecurityConfig.java`、`WebIdentityService.java`、`V1/V10/V15` 迁移。

产出：能够解释“用户名重名如何处理”“为什么普通用户看不到其他用户数据”“密码修改后旧会话为什么失效”。

### 阶段 3：数据库模型与 MyBatis

按迁移顺序学习 `sys_user → dataset → track_file → track_point → analysis_result → task → report`，再学习角色、审计、租约和评测扩展表。

重点掌握主键、外键、唯一约束、索引、逻辑删除、乐观锁、分页和批量插入。

### 阶段 4：仿真数据生成与 CSV 解析

学习运动模型、随机种子、噪声注入、二维/三维坐标、CSV 协议、流式复制、哈希校验、解析状态和批量入库。

重点文件：`SimulationGeneratorService.java`、`CsvTrackParser.java`、`TrackFileApplicationService.java`。

### 阶段 5：指标计算与航迹可视化

学习三维误差、RMSE、MAE、分量 RMSE、异常点、速度 RMSE、末点误差、航迹长度、连续性和 XY/3D 投影绘图。

重点文件：`AnalysisApplicationService.java`、`TrajectoryQualityMetricService.java`、`DatasetPageController.java`、`dataset-detail.html`。

### 阶段 6：异步任务与可靠性

学习 RabbitMQ 发布/消费、任务状态机、幂等、租约、心跳、失败重试、取消、Outbox 和数据集删除清理。

重点文件：`AnalysisTaskApplicationService.java`、`AnalysisTaskConsumer.java`、`TaskLeaseHeartbeat.java`、`TaskPublicationWorker.java`、`DatasetDeletionWorker.java`。

### 阶段 7：测试、故障排查与面试表达

学习单元测试、MockMvc、Testcontainers、迁移测试、Redis Session 测试、Docker 验收和日志定位。最后练习 1 分钟项目介绍、3 分钟架构介绍和技术追问。

## 每个阶段的学习方法

每次按“业务目的 → 请求入口 → 调用链 → 数据结构 → 异常路径 → 测试证据 → 面试表达”七步学习。不要一开始逐行背代码，先理解一个完整用例，再回到类和方法细节。

## 第一阶段开始顺序

1. 阅读 `docs/final-version-baseline.md`，明确当前最终产品边界。
2. 阅读 `TrackAnalysisApplication.java` 和 `pom.xml`，认识运行时。
3. 阅读 `compose.yaml`、`compose.app.yaml` 和 `application.yml`，画出容器依赖图。
4. 阅读 `V1__create_sys_user.sql` 到 `V5__create_track_point.sql`，建立核心数据模型。
5. 用一个“注册 → 生成 CSV → 上传算法结果 → 查看指标”的真实请求贯穿前五个阶段。
