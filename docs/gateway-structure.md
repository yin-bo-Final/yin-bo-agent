# 网关项目结构

这份文档专门给 gateway 模块改动使用。当前网关是独立于后端业务服务的 Spring Cloud Gateway 服务，先承担统一入口和 `/api/**` 路由转发。

## 目录总览

```text
gateway/
├─ pom.xml
└─ src/main/
   ├─ java/com/yinbo/gateway/
   │  └─ YinboAgentGatewayApplication.java
   └─ resources/
      └─ application.yml
```

## 服务命名

| 模块 | artifactId | spring.application.name | 默认端口 |
| --- | --- | --- | --- |
| `gateway` | `yinbo-agent-gateway` | `yinbo-agent-gateway` | `8081` |
| `backend` | `yinbo-agent-service` | `yinbo-agent-service` | `8080` |

## 路由

当前网关只配置一条业务路由：

```text
/api/** -> ${YINBO_AGENT_SERVICE_URI:http://localhost:8080}
```

也就是说，前端只需要访问 gateway 的 `/api`，后端业务服务仍然保留原来的 Controller 路径。

## 本地启动

先启动后端业务服务：

```powershell
cd backend
mvn spring-boot:run
```

再启动网关：

```powershell
cd gateway
mvn spring-boot:run
```

默认访问入口：

```text
http://localhost:8081
```

前端开发环境的 `vite.config.js` 已经把 `/api` 代理到 `http://localhost:8081`。

## 边界约定

- 网关模块包名根路径是 `com.yinbo.gateway`，不要放到 `com.yinbo.agent` 下。
- 当前登录态、角色校验、业务异常仍由后端业务服务处理。
- 网关暂时做入口转发、响应头去重、`X-Request-Id` 生成透传、访问日志和 Actuator 暴露。
- 后续如果要做限流、黑白名单、统一鉴权、链路日志，优先放在 gateway 模块。
- 如果以后接注册中心，`YINBO_AGENT_SERVICE_URI` 可以从固定地址切换成服务发现地址。

## RequestId 链路追踪

gateway 会在请求进入时读取 `X-Request-Id`：

```text
请求头已有合法 X-Request-Id -> 继续使用
请求头没有或不合法 -> 生成新的 requestId
```

gateway 会把 `X-Request-Id` 继续转发给后端业务服务，也会写入响应头返回给前端。gateway 访问日志默认位于当前启动工作目录下：

```text
.logs/gateway.log
```

后端业务服务会通过 `RequestIdFilter` 读取同一个 `X-Request-Id`，放入 MDC，让 service 日志自动带上 requestId，并记录一行 service 访问日志。service 日志默认位于当前启动工作目录下：

```text
.logs/service.log
```

排查时可以拿浏览器响应头里的 `X-Request-Id` 搜日志：

```powershell
Select-String -Path .logs\*.log -Pattern "你的-request-id"
```

## 日志风格和滚动

gateway 和 service 都使用 key-value 日志风格：

```text
2026-05-31 18:57:00.000 [INFO ] app=yinbo-agent-gateway requestId=xxx logger=c.y.g.filter.RequestIdGlobalFilter - event=access requestId=xxx method=GET path=/api/models status=200 costMs=12 slow=false clientIp=127.0.0.1 userAgent=curl/8.19.0
```

后端业务异常会记录：

```text
event=exception requestId=xxx type=BusinessException status=401 message=未登录或会话已过期，请重新登录
```

gateway 自身异常也会记录 `event=exception requestId=xxx`，例如后端业务服务未启动导致的连接失败。

后端业务服务还会记录关键业务事件：

```text
event=user_login_success userId=1 username=admin role=ADMIN
event=knowledge_base_created userId=1 knowledgeBaseId=xxx collectionName=default
event=document_uploaded userId=1 knowledgeBaseId=xxx documentId=xxx sourceType=UPLOAD fileName=demo.pdf sizeBytes=1024 strategy=AUTO
event=ai_chat_completed mode=sync userId=1 conversationId=xxx modelId=deepseek-ai/DeepSeek-V4-Flash costMs=1200 promptTokens=100 completionTokens=300 totalTokens=400
```

RocketMQ 链路日志会带 `sourceRequestId`，用于把 HTTP 请求和异步消费串起来：

```text
event=mq_send topic=rag-ingestion-task action=CHUNK documentId=xxx sourceRequestId=xxx messageId=xxx sendStatus=SEND_OK
event=mq_consume_started topic=rag-ingestion-task action=CHUNK documentId=xxx sourceRequestId=xxx
event=ingestion_completed action=CHUNK documentId=xxx chunkCount=12 parseMs=100 chunkMs=20 embeddingMs=800 totalMs=950
event=mq_consume_completed topic=rag-ingestion-task action=CHUNK documentId=xxx costMs=980
```

慢请求阈值由 `APP_SLOW_REQUEST_THRESHOLD_MS` 控制，默认 `3000ms`。超过阈值的访问日志使用 `WARN` 级别，并且 `slow=true`。

日志滚动配置在各自的 `application.yml`：

```yml
logging:
  logback:
    rollingpolicy:
      file-name-pattern: .logs/gateway.%d{yyyy-MM-dd}.%i.log.gz
      max-file-size: 20MB
      max-history: 14
      total-size-cap: 1GB
```

含义：

```text
max-file-size: 当前日志文件超过 20MB 时切出一个历史文件
%d{yyyy-MM-dd}: 每天一个日期维度
%i: 同一天内的第几个滚动文件
max-history: 最多保留 14 天
total-size-cap: 所有历史日志压缩包总量最多 1GB
```
