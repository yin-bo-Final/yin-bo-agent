# 应用服务部署

这份文档说明如何把四个 Java 服务部署到服务器：

- `yinbo-ai-infra`
- `yinbo-mcp-server`
- `yinbo-agent-service`
- `yinbo-agent-gateway`

## 服务器目录

```bash
useradd -r -m -s /usr/sbin/nologin yinbo || true
mkdir -p /opt/yinbo-agent/apps /opt/yinbo-agent/.logs
chown -R yinbo:yinbo /opt/yinbo-agent
```

## 上传配置

```bash
cp /opt/yinbo-agent/deploy/local-secrets.server.example.yml /opt/yinbo-agent/local-secrets.yml
vim /opt/yinbo-agent/local-secrets.yml
chown yinbo:yinbo /opt/yinbo-agent/local-secrets.yml
chmod 600 /opt/yinbo-agent/local-secrets.yml
```

如果 PostgreSQL 数据库名不是 `yinbo_agent`，要同步修改 `POSTGRES_URL`。

## 安装 systemd 服务

```bash
cp /opt/yinbo-agent/deploy/systemd/*.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable yinbo-ai-infra yinbo-mcp-server yinbo-backend yinbo-gateway
```

## 启动顺序

```bash
systemctl start yinbo-ai-infra
systemctl start yinbo-mcp-server
systemctl start yinbo-backend
systemctl start yinbo-gateway
```

## 查看状态和日志

```bash
systemctl status yinbo-ai-infra --no-pager
systemctl status yinbo-mcp-server --no-pager
systemctl status yinbo-backend --no-pager
systemctl status yinbo-gateway --no-pager

journalctl -u yinbo-backend -f
tail -f /opt/yinbo-agent/.logs/service.log
```

## 健康检查

```bash
curl -fsS http://127.0.0.1:8082/actuator/health
curl -fsS http://127.0.0.1:8083/actuator/health
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8081/actuator/health
```

## 后续更新

在 Windows 本地项目根目录执行：

```powershell
.\deploy\scripts\update-backend.ps1
```

只更新 gateway：

```powershell
.\deploy\scripts\update-gateway.ps1
```

更新全部 Java 服务：

```powershell
.\deploy\scripts\update-all.ps1
```

如果已经提前打包，可以跳过构建：

```powershell
.\deploy\scripts\update-backend.ps1 -SkipBuild
```
