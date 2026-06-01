# 网关模块结构

这份文档只说明 gateway 服务内部有哪些模块，以及每个模块负责什么功能。

## 模块总览

```text
gateway/src/main/
├─ java/com/yinbo/gateway/
│  ├─ concurrent/   # Redis 分布式信号量
│  ├─ config/       # 网关配置 Bean 和配置属性
│  ├─ filter/       # 全局过滤器
│  ├─ rate/         # 限流身份解析
│  └─ response/     # 统一错误响应
└─ resources/
   └─ application.yml
```

## `resources` 配置模块

### `application.yml`

网关运行时配置文件，负责把 gateway 的各项能力组装起来。

主要功能：

| 配置区域 | 功能 |
| --- | --- |
| `spring.data.redis` | 配置 Redis 连接，供 RedisRateLimiter、Session 用户识别、Redis 信号量使用 |
| `spring.cloud.gateway.server.webflux.globalcors` | 统一处理 `/api/**` 的 CORS |
| `spring.cloud.gateway.server.webflux.default-filters` | 去重 CORS 响应头，避免多层代理产生重复头 |
| `spring.cloud.gateway.server.webflux.routes` | 配置 `/api/**` 到后端 service 的路由转发，并给高成本接口挂频率限流 |
| `logging` | 配置 gateway 日志文件、日志格式和日志滚动策略 |
| `app.logging` | 配置慢请求阈值 |
| `app.concurrency` | 配置上传、URL 入库、AI 对话的并发信号量限制 |
| `app.session.redis.namespace` | 配置 gateway 读取 Spring Session 时使用的 Redis namespace |

当前路由功能：

| route id                      | 功能                             |
| ----------------------------- | ------------------------------ |
| `upload-ip-rate-limit`        | 上传接口频率限流                       |
| `url-ingestion-ip-rate-limit` | URL 入库接口频率限流                   |
| `ai-stream-ip-rate-limit`     | 流式 AI 对话频率限流                   |
| `ai-chat-ip-rate-limit`       | 普通 AI 对话频率限流                   |
| `auth-ip-rate-limit`          | 登录和注册接口频率限流                    |
| `yinbo-agent-service-api`     | 兜底转发所有 `/api/**` 请求到后端 service |

## `concurrent` 模块

### `RedisSemaphoreService.java`

Redis 分布式信号量服务，用来限制高成本接口的“同时执行数量”。

主要功能：

| 功能   | 说明                                             |
| ---- | ---------------------------------------------- |
| 获取许可 | 使用 Redis Sorted Set + Lua 脚本原子判断当前并发数是否小于最大许可数 |
| 许可租约 | 每个许可写入过期时间，防止请求异常中断后许可永久占用                     |
| 释放许可 | 请求完成、异常或取消后删除当前 permitId                       |
| 失败兜底 | 释放失败只记录日志，许可会依赖 TTL 自动过期                       |

核心方法：

| 方法                                                           | 功能                              |
| ------------------------------------------------------------ | ------------------------------- |
| `tryAcquire(String name, int maxPermits, Duration leaseTtl)` | 尝试获取信号量许可，成功返回 `Permit`，并发已满返回空 |
| `release(Permit permit)`                                     | 释放指定许可                          |
| `sanitizeLogValue(String value)`                             | 清洗异常日志文本                        |

`Permit` 保存当前许可的业务名、Redis key 和 permitId，用于后续精确释放。

## `config` 模块

### `ConcurrencyLimitProperties.java`

并发限制配置属性，对应 `app.concurrency`。

主要功能：

| 配置项 | 功能 | 默认值 |
| --- | --- | --- |
| `upload` | 上传并发限制 | `10 / 10m` |
| `urlIngestion` | URL 入库并发限制 | `5 / 10m` |
| `aiChat` | AI 对话并发限制 | `20 / 5m` |

核心结构：

| 结构 | 功能 |
| --- | --- |
| `ConcurrencyLimitProperties` | 保存三个高成本资源的并发配置 |
| `Limit` | 保存单个资源的 `maxPermits` 和 `leaseTtl` |
| `Limit.withDefaults(...)` | 配置缺失或非法时回退默认值 |

### `RateLimitConfig.java`

频率限流配置模块。

主要功能：

| Bean | 功能 |
| --- | --- |
| `userOrIpKeyResolver` | 给 Spring Cloud Gateway RedisRateLimiter 提供限流 key |
| `springSessionRedisTemplate` | 用 JDK 反序列化方式读取 Spring Session 中的 `LOGIN_USER_ID` |

`userOrIpKeyResolver` 会委托 `RateLimitIdentityResolver` 决定当前请求按 `userId` 限流还是按 IP 限流。

## `rate` 模块

### `RateLimitIdentityResolver.java`

频率限流身份解析模块。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 未登录限流 | 没有 Session 或 Session 里没有 `LOGIN_USER_ID` 时，返回 `ip:{clientIp}` |
| 登录后限流 | Redis Session 中存在 `LOGIN_USER_ID` 时，返回 `user:{userId}` |
| Session 读取 | 从 `SESSION` Cookie 拼出 Spring Session Redis key 并读取用户 ID |
| IP 解析 | 按 `X-Forwarded-For`、`X-Real-IP`、`remoteAddress` 的优先级解析客户端 IP |
| 异常兜底 | 读取 Redis Session 失败时记录 warn，并回退到 IP 限流 |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `resolve(ServerWebExchange exchange)` | 返回 RedisRateLimiter 使用的限流 key |
| `toUserKey(Object value)` | 把 Session 中的用户 ID 转成 `user:{id}` |
| `resolveSessionId(ServerHttpRequest request)` | 从 Cookie 中解析 Session ID |
| `resolveClientIp(ServerHttpRequest request)` | 解析客户端 IP |
| `sanitizeIdentity(String value)` | 清洗限流 key，避免非法字符进入 Redis key |
| `sanitizeLogValue(String value)` | 清洗日志文本 |

## `filter` 模块

全局过滤器模块负责在请求进入后端 service 前后处理通用网关能力。

当前过滤器：

| 过滤器 | 功能 |
| --- | --- |
| `RequestIdGlobalFilter` | 生成或透传 `X-Request-Id`，写入响应头并记录访问日志 |
| `GatewayErrorGlobalFilter` | 捕获 gateway 转发链路异常，并写入统一 JSON 错误响应 |
| `RateLimitResponseGlobalFilter` | 改写 RedisRateLimiter 产生的 `429` 响应 |
| `ResourceConcurrencyGlobalFilter` | 对上传、URL 入库、AI 对话做 Redis 信号量并发限制 |

### `RequestIdGlobalFilter.java`

RequestId 链路追踪全局过滤器。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 生成 requestId | 请求头没有合法 `X-Request-Id` 时生成新的 requestId |
| 透传 requestId | 请求头已有合法 `X-Request-Id` 时继续使用 |
| 写入请求头 | 把 requestId 写入转发给后端 service 的请求头 |
| 写入响应头 | 把 requestId 写回前端响应头 |
| 访问日志 | 请求结束后记录 method、path、status、costMs、clientIp、userAgent |
| 慢请求标记 | 超过慢请求阈值时使用 WARN，并标记 `slow=true` |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `filter(ServerWebExchange exchange, GatewayFilterChain chain)` | 处理 requestId、继续过滤器链、记录访问日志 |
| `getOrder()` | 保证 requestId 过滤器最先执行 |
| `resolveRequestId(ServerHttpRequest request)` | 解析或生成 requestId |
| `resolveClientIp(ServerHttpRequest request)` | 解析客户端 IP |
| `sanitizeLogValue(String value)` | 清洗日志文本 |

### `GatewayErrorGlobalFilter.java`

Gateway 异常统一响应全局过滤器。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 捕获异常 | 捕获 gateway 转发链路中的运行时异常 |
| 统一响应 | 调用 `GatewayErrorResponseWriter` 写入统一 JSON |
| 状态映射 | 将超时映射为 `504`，连接失败或域名失败映射为 `503`，其他异常映射为 `500` |
| 安全提示 | 对前端返回通用提示，不暴露底层异常堆栈 |
| 错误日志 | 记录 `event=gateway_error`，包含 requestId、path、status、异常类型和简短 message |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `filter(ServerWebExchange exchange, GatewayFilterChain chain)` | 包装后续过滤器链并捕获异常 |
| `getOrder()` | 让异常兜底过滤器在 RequestId 后执行 |
| `writeGatewayErrorResponse(...)` | 记录异常日志并写入统一 JSON |
| `resolveStatus(Throwable throwable)` | 根据异常类型决定响应状态码 |
| `resolveMessage(HttpStatus status)` | 根据状态码决定前端提示语 |
| `containsCause(...)` | 判断异常链中是否包含指定异常类型 |
| `sanitizeLogValue(String value)` | 清洗日志文本 |

### `RateLimitResponseGlobalFilter.java`

频率限流响应全局过滤器。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 拦截 429 | 捕获 RedisRateLimiter 产生的 `429 Too Many Requests` |
| 统一响应体 | 把默认空响应改成统一 JSON |
| 写入 requestId | 限流响应也带 `X-Request-Id` |
| 限流日志 | 记录 routeId、path、clientIp、requestId |
| 防缓存 | 写入 `Cache-Control: no-store` |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `filter(ServerWebExchange exchange, GatewayFilterChain chain)` | 装饰响应对象并拦截 429 |
| `getOrder()` | 控制过滤器顺序 |
| `writeRateLimitResponse(...)` | 写入统一频率限流响应 |
| `requestId(...)` | 读取 requestId |
| `routeId(...)` | 读取当前命中的路由 ID |
| `resolveClientIp(...)` | 解析客户端 IP |
| `sanitizeLogValue(...)` | 清洗日志文本 |

统一响应示例：

```json
{
  "status": 429,
  "message": "请求过于频繁，请稍后再试",
  "requestId": "xxx",
  "path": "/api/chat",
  "timestamp": "2026-06-01T08:00:00Z"
}
```

### `ResourceConcurrencyGlobalFilter.java`

资源并发限流全局过滤器。

主要功能：

| 功能          | 说明                                 |
| ----------- | ---------------------------------- |
| 上传并发限制      | 限制上传接口同时转发到 service 的请求数量          |
| URL 入库并发限制  | 限制 URL 入库同时执行数量                    |
| AI 对话并发限制   | 限制普通对话和流式对话同时执行数量                  |
| Redis 信号量   | 使用 `RedisSemaphoreService` 获取和释放许可 |
| 许可释放        | 正常完成、异常、客户端取消时都释放许可                |
| 超限响应        | 拿不到许可时返回统一 `429` JSON              |
| Redis 不可用响应 | Redis 信号量异常时返回 `503` JSON          |
| 完成日志        | 请求结束时记录资源类型、状态码、结束信号和耗时            |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `filter(ServerWebExchange exchange, GatewayFilterChain chain)` | 判断是否需要并发限流，获取许可，管理释放 |
| `getOrder()` | 控制过滤器顺序 |
| `resolveResourceLimit(ServerHttpRequest request)` | 按请求路径识别资源类型 |
| `writeLimitedResponse(...)` | 写入并发超限响应 |
| `writeUnavailableResponse(...)` | 写入并发控制不可用响应 |
| `logCompleted(...)` | 记录资源请求完成日志 |
| `requestId(...)` | 读取 requestId |
| `resolveClientIp(...)` | 解析客户端 IP |
| `sanitizeLogValue(...)` | 清洗日志文本 |

当前资源规则：

| 资源 | 路径 | 默认并发 |
| --- | --- | --- |
| `upload` | `/api/ingestion/documents/upload` | `10` |
| `upload` | `/api/admin/knowledge/bases/{knowledgeBaseId}/documents/upload` | `10` |
| `url_ingestion` | `/api/ingestion/documents/url` | `5` |
| `url_ingestion` | `/api/admin/knowledge/bases/{knowledgeBaseId}/documents/url` | `5` |
| `ai_chat` | `/api/chat` | `20` |
| `ai_chat` | `/api/chat/stream` | `20` |

超限响应示例：

```json
{
  "status": 429,
  "message": "当前上传任务较多，请稍后再试",
  "requestId": "xxx",
  "path": "/api/ingestion/documents/upload",
  "timestamp": "2026-06-01T08:00:00Z"
}
```

## `response` 模块

### `GatewayErrorResponseWriter.java`

Gateway 统一错误响应写入器。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 统一 JSON 格式 | 所有 gateway 层错误响应都使用 `status/message/requestId/path/timestamp` |
| 统一响应头 | 写入 `Content-Type: application/json`、`Cache-Control: no-store`、`X-Request-Id` |
| 复用响应对象 | 支持直接写 exchange 响应，也支持写被装饰过的 response |
| 序列化兜底 | ObjectMapper 序列化失败时返回最小 JSON |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `write(ServerWebExchange exchange, HttpStatus status, String message)` | 使用当前 exchange 的响应对象写入统一错误响应 |
| `write(ServerWebExchange exchange, ServerHttpResponse response, HttpStatus status, String message)` | 使用指定响应对象写入统一错误响应 |
| `requestId(ServerHttpRequest request)` | 从请求头读取 requestId |
| `serializeBody(...)` | 序列化统一错误响应体 |
| `sanitizeLogValue(String value)` | 清洗日志文本 |
| `escapeJson(String value)` | 转义兜底 JSON 文本 |

## 功能清单

| 功能                    | 主要实现位置                                                            |
| --------------------- | ----------------------------------------------------------------- |
| `/api/**` 路由转发        | `application.yml`                                                 |
| CORS                  | `application.yml`                                                 |
| CORS 响应头去重            | `application.yml`                                                 |
| `X-Request-Id` 生成和透传  | `RequestIdGlobalFilter`                                           |
| gateway 访问日志          | `RequestIdGlobalFilter`                                           |
| RedisRateLimiter 频率限流 | `application.yml`、`RateLimitConfig`、`RateLimitIdentityResolver`   |
| 未登录按 IP 限流            | `RateLimitIdentityResolver`                                       |
| 登录后按 userId 限流        | `RateLimitIdentityResolver`                                       |
| 429 统一 JSON           | `RateLimitResponseGlobalFilter`、`ResourceConcurrencyGlobalFilter`、`GatewayErrorResponseWriter` |
| gateway 异常统一 JSON    | `GatewayErrorGlobalFilter`、`GatewayErrorResponseWriter`           |
| 上传并发限制                | `ResourceConcurrencyGlobalFilter`、`RedisSemaphoreService`         |
| URL 入库并发限制            | `ResourceConcurrencyGlobalFilter`、`RedisSemaphoreService`         |
| AI 对话并发限制             | `ResourceConcurrencyGlobalFilter`、`RedisSemaphoreService`         |
| 日志文件和滚动               | `application.yml`                                                 |
