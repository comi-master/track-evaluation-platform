# 局域网访问

本项目恢复为手动启动方式，不提供一键 BAT 安装入口。启动 Docker Desktop 后，在服务端电脑的项目根目录执行：

```powershell
docker compose --env-file .env -f compose.yaml -f compose.app.yaml up -d --build
```

Compose 当前默认行为是：

- Spring Boot 应用端口 `8080` 绑定到 `0.0.0.0`，可由局域网其他电脑访问；
- MySQL、Redis、RabbitMQ、MinIO 端口仍绑定到 `127.0.0.1`，不直接暴露给局域网；
- 应用容器通过 Docker 内部网络访问这些中间件。

假设服务端电脑的局域网 IP 是 `192.168.1.20`，其他电脑访问：

```text
http://192.168.1.20:8080/login
```

还需要在服务端 Windows 防火墙中允许 TCP `8080` 入站，并确保客户端和服务端位于同一局域网且没有客户端隔离。

如需显式配置，可在本地 `.env` 中设置：

```env
APP_BIND_ADDRESS=0.0.0.0
MIDDLEWARE_BIND_ADDRESS=127.0.0.1
```

不要把 MySQL、Redis、RabbitMQ 或 MinIO 端口改为公网或局域网开放地址。停止服务使用 `docker compose stop`，不要使用 `down -v`，以免删除持久化数据卷。
