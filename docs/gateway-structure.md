# 网关项目结构

这份文档专门给 gateway 模块改动使用。当前网关是独立于后端业务服务的 Spring Cloud Gateway 服务，先承担统一入口和 `/api/**` 路由转发。

## 目录总览

```text
gateway/
├─ pom.xml
└─ src/main/
   ├─ java/com/yinbo/gateway/
   │  ├─ YinboAgentGatewayApplication.java
   │  ├─ concurrent/
   │  │  └─ RedisSemaphoreService.java
   │  ├─ config/
   │  │  ├─ ConcurrencyLimitProperties.java
   │  │  └─ RateLimitConfig.java
   │  ├─ filter/
   │  │  ├─ RequestIdGlobalFilter.java
   │  │  ├─ ResourceConcurrencyGlobalFilter.java
   │  │  └─ RateLimitResponseGlobalFilter.java
   │  └─ rate/
   │     └─ RateLimitIdentityResolver.java
   └─ resources/
      └─ application.yml
```

## 服务命名

| 模块        | artifactId            | spring.application.name | 默认端口   |
| --------- | --------------------- | ----------------------- | ------ |
| `gateway` | `yinbo-agent-gateway` | `yinbo-agent-gateway`   | `8081` |
| `backend` | `yinbo-agent-service` | `yinbo-agent-service`   | `8080` |

## 路由

当前网关会把 `/api/**` 转发到业务服务：

```text
/api/** -> ${YINBO_AGENT_SERVICE_URI:http://localhost:8080}
```

其中上传、URL 入库、AI 对话、登录注册会先匹配专门的限流路由，再转发到同一个业务服务。也就是说，前端只需要访问 gateway 的 `/api`，后端业务服务仍然保留原来的 Controller 路径。

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
- 网关暂时做入口转发、CORS、IP 限流、上传并发限流、响应头去重、`X-Request-Id` 生成透传、访问日志和 Actuator 暴露。
- 后续如果要做黑白名单、统一鉴权、链路日志，优先放在 gateway 模块。
- 如果以后接注册中心，`YINBO_AGENT_SERVICE_URI` 可以从固定地址切换成服务发现地址。

## 注释规范

gateway 模块的类和方法统一使用一行中文注释，注释放在类名或方法名前，直接说明它的职责：

```java
// 频率限流响应全局过滤器。
public class RateLimitResponseGlobalFilter implements GlobalFilter, Ordered {

    // 拦截 RedisRateLimiter 产生的 429 响应并改写响应体。
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 复杂逻辑内部可以补充关键步骤注释，但不要逐行翻译代码。
    }
}
```

`application.yml` 中的关键配置块也使用同样风格：

```yml
# 网关服务端口。
server:
  port: ${GATEWAY_PORT:8081}
```

新增 gateway 功能时，至少要给类、入口方法、顺序控制方法、Redis / 限流 / 响应写入方法补注释。注释要短、准、能说明职责，避免只重复方法名。

## CORS

CORS 统一配置在 gateway 的 `application.yml`，后端业务服务不再单独注册 CORS。当前允许本地前端开发地址：

```text
http://localhost:5173
http://127.0.0.1:5173
http://localhost:5174
http://127.0.0.1:5174
```

gateway 会允许 `/api/**` 的常见 HTTP 方法和请求头，并暴露 `X-Request-Id` 响应头，方便前端或浏览器调试时拿到链路 ID。

## 频率限流

gateway 使用 Spring Cloud Gateway `RedisRateLimiter` 做频率限流。限流 key 通过 `userOrIpKeyResolver` 解析：

```text
请求带有效 SESSION 且 Redis Session 中存在 LOGIN_USER_ID -> user:{userId}
否则 -> ip:{clientIp}
```

客户端 IP 解析优先级：

```text
X-Forwarded-For 第一个 IP -> X-Real-IP -> remoteAddress -> unknown
```

`LOGIN_USER_ID` 由后端业务服务登录或注册成功后写入 Spring Session。老 session 没有这个字段时会自动回退到 IP 限流。受限接口依赖 Redis；本地启动 gateway 前要保证 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` 指向可访问的 Redis，否则命中限流路由时会因为无法访问 Redis 而失败。

当前限流路由：

| 路由 ID                         | 接口                                                                                                | 平均速率           | 突发  |
| ----------------------------- | ------------------------------------------------------------------------------------------------- | -------------- | --- |
| `upload-ip-rate-limit`        | `/api/ingestion/documents/upload`、`/api/admin/knowledge/bases/{knowledgeBaseId}/documents/upload` | 同一 IP 每分钟 5 次  | 2 次 |
| `url-ingestion-ip-rate-limit` | `/api/ingestion/documents/url`、`/api/admin/knowledge/bases/{knowledgeBaseId}/documents/url`       | 同一 IP 每分钟 3 次  | 1 次 |
| `ai-stream-ip-rate-limit`     | `/api/chat/stream`                                                                                | 同一 IP 每分钟 10 次 | 1 次 |
| `ai-chat-ip-rate-limit`       | `/api/chat`                                                                                       | 同一 IP 每分钟 20 次 | 2 次 |
| `auth-ip-rate-limit`          | `/api/auth/login`、`/api/auth/register`                                                            | 同一 IP 每分钟 10 次 | 1 次 |

这里用的是“每次请求消耗 60 个 token”的写法。例如上传接口：

```text
replenishRate=5, requestedTokens=60, burstCapacity=120
=> 每分钟补 300 token
=> 300 / 60 = 每分钟 5 次
=> 120 / 60 = 最多突发 2 次
```

没有令牌时 gateway 会直接返回 `429 Too Many Requests`，响应体统一为：

```json
{
  "status": 429,
  "message": "请求过于频繁，请稍后再试",
  "requestId": "xxx",
  "path": "/api/chat",
  "timestamp": "2026-05-31T11:00:00Z"
}
```

同时 gateway 记录：

```text
event=rate_limited requestId=xxx routeId=upload-ip-rate-limit path=/api/ingestion/documents/upload clientIp=127.0.0.1
```

## 资源并发限流

`ResourceConcurrencyGlobalFilter` 统一处理上传、URL 入库和 AI 对话的 Redis 信号量并发限制。上传接口会在 gateway 层先抢许可，这样可以避免请求已经进入后端 Tomcat 后才被拒绝，减少 service 线程、multipart 临时文件和磁盘 IO 压力。

当前资源规则：

| 资源 | 接口 | 默认并发 | Redis key |
| --- | --- | --- | --- |
| 上传 | `/api/ingestion/documents/upload`、`/api/admin/knowledge/bases/{knowledgeBaseId}/documents/upload` | 10 | `gateway:ingestion:upload:global` |
| URL 入库 | `/api/ingestion/documents/url`、`/api/admin/knowledge/bases/{knowledgeBaseId}/documents/url` | 5 | `gateway:ingestion:url:global` |
| AI 对话 | `/api/chat`、`/api/chat/stream` | 20 | `gateway:ai:chat:global` |

配置项：

```text
UPLOAD_GATEWAY_MAX_CONCURRENCY=10
UPLOAD_GATEWAY_CONCURRENCY_LEASE_TTL=10m
URL_INGESTION_GATEWAY_MAX_CONCURRENCY=5
URL_INGESTION_GATEWAY_CONCURRENCY_LEASE_TTL=10m
AI_CHAT_GATEWAY_MAX_CONCURRENCY=20
AI_CHAT_GATEWAY_CONCURRENCY_LEASE_TTL=5m
```

拿不到上传许可时 gateway 直接返回：

```json
{
  "status": 429,
  "message": "当前上传任务较多，请稍后再试",
  "requestId": "xxx",
  "path": "/api/admin/knowledge/bases/default/documents/upload",
  "timestamp": "2026-06-01T02:40:00Z"
}
```

同时记录：

```text
event=resource_concurrency_limited resource=upload requestId=xxx path=/api/admin/knowledge/bases/default/documents/upload clientIp=127.0.0.1 maxPermits=10
```

请求正常完成、异常或客户端断开时都会释放许可。后端业务服务仍保留上传 Redis 信号量，key 为 `service:ingestion:upload:global`，定位是兜底保护，用来防止绕过 gateway 直接访问 service。gateway 和 service 不共用同一个 key，避免一次上传占用两个许可。

拿不到许可时 gateway 返回 `429`，例如：

```json
{
  "status": 429,
  "message": "当前 AI 对话任务较多，请稍后再试",
  "requestId": "xxx",
  "path": "/api/chat/stream",
  "timestamp": "2026-06-01T02:55:00Z"
}
```

AI 和 URL 入库的对应日志也使用同一个事件名，通过 `resource` 字段区分资源：

```text
event=resource_concurrency_limited resource=ai_chat requestId=xxx path=/api/chat/stream clientIp=127.0.0.1 maxPermits=20
```

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
