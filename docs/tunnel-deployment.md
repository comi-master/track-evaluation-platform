# SimBench 本地 Tunnel 部署

Tunnel 让公网域名通过一条出站连接访问本地应用，不需要开放家庭网络的 8080、3306、6379、5672 或 MinIO 端口。

```text
用户 -> HTTPS 域名 -> Cloudflare Tunnel -> Docker 内网 app:8080
                                      -> MySQL/Redis/RabbitMQ/MinIO 不对外开放
```

## 一次性配置

1. 注册 Cloudflare 账号，把域名 DNS 托管到 Cloudflare。
2. 在 Cloudflare Zero Trust 中创建 remotely-managed Tunnel。
3. 添加 Public Hostname，例如 `simbench.example.com`。
4. 将 Service 设置为 `http://app:8080`。Tunnel 容器和应用容器位于同一个 Compose 网络，不要填写 `127.0.0.1:8080`。
5. 将 Tunnel Token 写入本地 `.env` 的 `CLOUDFLARE_TUNNEL_TOKEN`，不要提交到 Git。

## 启动

```powershell
docker compose --env-file .env -f compose.yaml -f compose.app.yaml -f compose.tunnel.yaml config --quiet
docker compose --env-file .env -f compose.yaml -f compose.app.yaml -f compose.tunnel.yaml up -d --build
docker compose -f compose.yaml -f compose.app.yaml -f compose.tunnel.yaml ps
```

应用端口只绑定到 `127.0.0.1`，公网流量由 Tunnel 转发；数据库、Redis、RabbitMQ 和 MinIO 保持内网访问。

## 上线前检查

- 修改曾经暴露过的管理员密码。
- 轮换 JWT_SECRET 和所有中间件密码。
- 保持 `APP_PUBLIC_REGISTRATION_ENABLED=false`，先由管理员创建用户或设计邀请机制。
- 关闭公网 Swagger，或增加管理员访问保护。
- 确认 Tunnel 日志不输出 Token。
- 用域名验证健康检查、登录、上传、异步评测和排行榜。

这个方案适合 Demo、小规模试用和开源项目体验。电脑关机、休眠或网络断开时服务会不可用；需要长期稳定在线时再迁移到云服务器。
