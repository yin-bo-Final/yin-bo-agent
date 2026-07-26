# 服务器中间件 Docker Compose

这份 compose 用来在服务器上启动项目依赖的中间件：

- PostgreSQL + pgvector
- Redis
- RocketMQ NameServer / Broker / Dashboard
- RustFS

## 启动

```bash
cd deploy
cp .env.middleware.example .env
vim .env
docker compose --env-file .env -f docker-compose.middleware.yml up -d
```

## 检查状态

```bash
docker compose --env-file .env -f docker-compose.middleware.yml ps
docker compose --env-file .env -f docker-compose.middleware.yml logs -f
```

## 停止

```bash
docker compose --env-file .env -f docker-compose.middleware.yml down
```

## 本机端口

compose 里的端口都绑定到服务器 `127.0.0.1`，默认不给公网直接访问。

| 服务 | 地址 |
| --- | --- |
| PostgreSQL | `127.0.0.1:5432` |
| Redis | `127.0.0.1:6379` |
| RocketMQ NameServer | `127.0.0.1:9876` |
| RocketMQ Dashboard | `127.0.0.1:18082` |
| RustFS S3 | `127.0.0.1:9000` |
| RustFS Console | `127.0.0.1:9001` |

如果要在自己电脑浏览器打开 RocketMQ Dashboard 或 RustFS Console，使用 SSH 隧道：

```powershell
ssh -L 18082:127.0.0.1:18082 -L 9001:127.0.0.1:9001 root@你的服务器公网IP
```

然后在自己电脑浏览器访问：

```text
http://localhost:18082
http://localhost:9001/rustfs/console/index.html
```

## 后端 local-secrets.yml 对应配置

Java 服务跑在服务器宿主机时，可以这样配置：

```yml
POSTGRES_URL: jdbc:postgresql://127.0.0.1:5432/yinbo_agent
POSTGRES_USERNAME: yinbo
POSTGRES_PASSWORD: .env 里的 POSTGRES_PASSWORD
REDIS_HOST: 127.0.0.1
REDIS_PORT: 6379
REDIS_PASSWORD: .env 里的 REDIS_PASSWORD
ROCKETMQ_NAME_SERVER: 127.0.0.1:9876
RUSTFS_ENDPOINT: http://127.0.0.1:9000
RUSTFS_ACCESS_KEY: .env 里的 RUSTFS_ACCESS_KEY
RUSTFS_SECRET_KEY: .env 里的 RUSTFS_SECRET_KEY
RUSTFS_BUCKET: yinbo-agent-documents
```
