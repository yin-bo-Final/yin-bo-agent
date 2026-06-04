# 后端模块结构

这份文档只说明 backend 服务内部有哪些模块，以及每个模块负责什么功能。内容按当前 `backend/src/main` 的真实代码结构书写。

## 模块总览

```text
backend/src/main/
├─ java/com/yinbo/agent/
│  ├─ YinboAgentServiceApplication.java
│  ├─ admin/       # 后台管理员校验和仪表盘
│  ├─ auth/        # 注册、登录、Session 和角色
│  ├─ chat/        # AI 对话、流式响应、会话和消息
│  ├─ common/      # 统一异常、requestId、Redis 信号量
│  ├─ config/      # Spring Bean 和配置属性
│  ├─ ingestion/   # 文档上传、URL 入库、解析、分块、向量化
│  ├─ infra/       # 远程基础设施客户端
│  ├─ knowledge/   # 后台知识库、文档、分块管理
│  └─ storage/     # RustFS / S3 对象存储封装
└─ resources/
   ├─ application.yml
   └─ db/migration/
      └─ V1__init_schema.sql
```

## `resources` 配置模块

### `application.yml`

后端运行时配置文件，负责把 service 的数据库、Redis、Session、AI、RAG、MQ、对象存储、日志和 Actuator 能力组装起来。

主要功能：

| 配置区域 | 功能 |
| --- | --- |
| `server` | 配置 service 端口、Session 超时时间和 Cookie 属性 |
| `spring.datasource` | 配置 PostgreSQL 连接和 Hikari 启动行为 |
| `spring.data.redis` | 配置 Redis 连接，供 Spring Session 和 Redis 信号量使用 |
| `spring.session.redis` | 配置 Spring Session Redis namespace |
| `spring.sql.init` | 关闭旧 SQL 初始化，数据库结构交给 Flyway |
| `spring.flyway` | 配置 Flyway 迁移脚本位置、校验和禁止 clean |
| `spring.servlet.multipart` | 配置 service 兜底上传大小，默认单文件 `200MB`、单请求 `220MB` |
| `spring.ai.mcp.server` | 配置 MCP server 名称、版本和协议 |
| `rocketmq` | 配置 RocketMQ name server 和 producer group |
| `management` | 收口 Actuator，只启用和暴露 `health`、`info` |
| `logging` | 配置 service 日志文件、日志格式和日志滚动策略 |
| `app.logging` | 配置慢请求阈值 |
| `app.concurrency` | 配置 service 上传兜底并发和 ingestion 消费并发 |
| `app.ai-infra` | 配置 backend 远程调用 ai-infra 的 baseUrl 和超时时间 |
| `app.ai.rag` | 配置 RAG 模型、向量表、分块参数、源文件大小和 MQ topic |
| `app.auth` | 配置本地种子管理员账号 |
| `app.storage` | 配置 RustFS / S3 对象存储连接 |

### `db/migration/V1__init_schema.sql`

Flyway 初始迁移脚本。

主要功能：

| 功能 | 说明 |
| --- | --- |
| pgvector 扩展 | 创建 `vector` 扩展 |
| 认证表 | 创建 `auth_user` |
| 会话表 | 创建 `chat_conversation`、`chat_message` |
| 知识库表 | 创建 `knowledge_base`、`knowledge_document`、`knowledge_chunk` |
| 向量表 | 创建 `knowledge_chunk_vector`，供 ingestion 直接写入 pgvector |
| 索引 | 创建用户、会话、文档、分块和向量检索相关索引 |

### `db/migration/V2__create_ingestion_task.sql`

Flyway 入库任务迁移脚本。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 任务表 | 创建 `ingestion_task` 记录分块和重建向量任务 |
| 失败追踪 | 保存任务状态、重试次数、失败原因、requestId 和 MQ messageId |
| 后台查询 | 支持后台失败任务列表和手动重试 |

当前业务表边界：

| 表 | 主要模块 | 功能 |
| --- | --- | --- |
| `auth_user` | `auth` | 用户、密码哈希、角色、状态和最近登录时间 |
| `chat_conversation` | `chat` | 会话编号、标题、模型、置顶时间和最近消息时间 |
| `chat_message` | `chat` | 消息角色、内容、模型、响应耗时和 token 统计 |
| `knowledge_base` | `knowledge` | 知识库编号、名称、Embedding 模型、collection 和状态 |
| `knowledge_document` | `ingestion` / `knowledge` | 文档来源、对象存储信息、解析状态、分块参数和耗时 |
| `knowledge_chunk` | `ingestion` / `knowledge` | 分块内容、启用状态、token、字符数和向量文档 ID |
| `knowledge_chunk_vector` | `ingestion` | 向量内容、metadata、embedding 和 HNSW 索引 |
| `ingestion_task` | `ingestion` | 分块 / 重建向量任务状态、重试次数、失败原因和 MQ messageId |

## 根包

### `YinboAgentServiceApplication.java`

后端业务服务启动入口。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 启动 Spring Boot | 调用 `SpringApplication.run(...)` 启动 service |
| 加载配置属性 | 启用 `AiInfraProperties`、`AuthProperties`、`ConcurrencyLimitProperties`、`ObjectStorageProperties`、`RagProperties` |
| 扫描 Mapper | 扫描 `auth`、`chat`、`ingestion`、`knowledge` 模块的 MyBatis Mapper |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `main(String[] args)` | 启动后端业务服务并加载配置属性和 MyBatis Mapper |

## `admin` 模块

后台管理员校验和仪表盘模块。

```text
admin/
├─ AdminGuard.java
├─ controller/
│  └─ AdminDashboardController.java
├─ dto/
│  └─ AdminDashboardResponse.java
└─ service/
   └─ AdminDashboardService.java
```

### `AdminGuard.java`

管理员权限门卫。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 登录校验 | 通过 `AuthService.requireActiveUser(...)` 获取当前登录用户 |
| 角色校验 | 要求当前用户角色必须是 `ADMIN` |
| 后台复用 | 后台 Controller 在进入业务方法前调用 `requireAdmin(...)` |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `requireAdmin(HttpServletRequest request)` | 校验当前请求必须来自管理员用户 |

### `AdminDashboardController.java`

管理后台仪表盘接口。

接口：

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `GET` | `/api/admin/dashboard` | 查询管理后台仪表盘统计数据 |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `dashboard(HttpServletRequest request)` | 校验管理员后返回仪表盘数据 |

### `AdminDashboardService.java`

仪表盘统计服务。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 用户统计 | 统计最近 1 天有登录记录且状态为 `1` 的活跃用户数 |
| 会话统计 | 统计总会话数和消息数 |
| 流量统计 | 汇总 `chat_message.content` 字符数 |
| 响应统计 | 统计 assistant 消息平均响应耗时 |
| 数据兜底 | JDBC 查询结果为空时回退为 `0` 或 `null` |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `dashboard()` | 查询后台仪表盘统计数据 |
| `queryLongOrZero(String sql)` | 执行聚合 SQL 并将空结果转成 `0` |
| `queryNullableLong(String sql)` | 执行可能为空的 Long 聚合查询 |
| `nullToZero(Long value)` | 将空 Long 转成 `0` |

### `AdminDashboardResponse.java`

仪表盘响应结构。

字段：

| 字段 | 功能 |
| --- | --- |
| `activeUserCount` | 活跃用户数 |
| `messageCount` | 消息总数 |
| `conversationCount` | 会话总数 |
| `trafficCharacterCount` | 消息内容字符流量 |
| `averageResponseTimeMs` | 平均响应耗时 |
| `knowledgeErrorRate` | 知识库错误率，占位字段，当前 service 返回 `null` |
| `noKnowledgeRate` | 无知识命中率，占位字段，当前 service 返回 `null` |

## `auth` 模块

注册、登录、Session 登录态和用户角色模块。

```text
auth/
├─ AuthBootstrapRunner.java
├─ AuthConstants.java
├─ LoginInterceptor.java
├─ controller/
│  └─ AuthController.java
├─ dto/
│  ├─ AuthUserView.java
│  ├─ CurrentUserResponse.java
│  ├─ DeleteAccountRequest.java
│  ├─ LoginRequest.java
│  ├─ LoginResponse.java
│  ├─ LogoutResponse.java
│  └─ RegisterRequest.java
├─ entity/
│  └─ AuthUser.java
├─ mapper/
│  └─ AuthUserMapper.java
├─ service/
│  ├─ AuthService.java
│  └─ SessionAuthService.java
└─ session/
   └─ LoginUser.java
```

### `AuthConstants.java`

认证模块 Session key 常量。

常量：

| 常量 | 功能 |
| --- | --- |
| `LOGIN_USER_SESSION_KEY` | 保存后端业务校验使用的 `LoginUser` |
| `LOGIN_USER_ID_SESSION_KEY` | 保存 gateway 用户维度限流读取的用户 ID |

### `AuthBootstrapRunner.java`

启动时初始化种子管理员。

核心方法：

| 方法 | 功能 |
| --- | --- |
| `run(ApplicationArguments args)` | 当 `app.auth` 配置了管理员账号和密码时创建默认管理员 |

### `LoginInterceptor.java`

登录态拦截器。

核心方法：

| 方法 | 功能 |
| --- | --- |
| `preHandle(...)` | 校验请求 Session 中存在 `LOGIN_USER`，并通过 `AuthService.requireActiveUser(...)` 校验用户仍可用 |

### `AuthController.java`

用户认证接口。

接口：

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `POST` | `/api/auth/register` | 注册新用户并建立登录会话 |
| `POST` | `/api/auth/login` | 校验账号密码并建立登录会话 |
| `GET` | `/api/auth/me` | 查询当前登录用户信息 |
| `POST` | `/api/auth/logout` | 退出当前登录会话 |
| `POST` | `/api/auth/cancel` | 注销当前账号 |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `register(...)` | 调用认证服务注册用户 |
| `login(...)` | 调用认证服务登录用户 |
| `currentUser(...)` | 查询当前登录用户 |
| `logout(...)` | 退出当前登录会话 |
| `deleteAccount(...)` | 注销当前账号 |

### `AuthService.java`

用户认证服务接口。

核心方法：

| 方法 | 功能 |
| --- | --- |
| `register(...)` | 注册用户并建立登录态 |
| `login(...)` | 登录用户并建立登录态 |
| `currentUser(...)` | 查询当前登录用户 |
| `logout(...)` | 退出当前登录态 |
| `deleteAccount(...)` | 注销当前账号 |
| `requireActiveUser(...)` | 要求当前请求必须有可用登录用户 |

### `SessionAuthService.java`

基于 Spring Session 的认证服务实现。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 注册 | 创建普通用户，默认角色 `USER`，状态 `1` |
| 登录 | 校验 BCrypt 密码，更新最近登录时间 |
| Session 写入 | 写入 `LOGIN_USER` 和 `LOGIN_USER_ID` |
| 当前用户 | 从 Session 读取登录用户并返回视图 |
| 退出 | 使当前 Session 失效 |
| 注销 | 校验密码后把用户状态改为不可用并使 Session 失效 |
| 种子管理员 | 启动时创建角色为 `ADMIN` 的管理员 |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `register(...)` | 注册用户并建立登录态 |
| `login(...)` | 登录用户并建立登录态 |
| `createLoginSession(...)` | 创建 Session 并写入 `LOGIN_USER`、`LOGIN_USER_ID` |
| `currentUser(...)` | 查询当前登录用户 |
| `logout(...)` | 退出当前登录态 |
| `deleteAccount(...)` | 注销当前账号 |
| `createSeedUser(...)` | 创建本地种子管理员 |
| `requireActiveUser(...)` | 校验当前 Session 用户存在且数据库状态可用 |
| `findSingleActiveUserByUsername(...)` | 按用户名查询单个可用用户 |
| `ensureUsernameAvailable(...)` | 校验用户名未被占用 |
| `normalizeUsername(...)` | 规范化用户名 |
| `toView(...)` | 转换为前端用户视图 |

### DTO、Entity、Mapper、Session 文件

| 文件 | 功能 |
| --- | --- |
| `dto/AuthUserView.java` | 返回用户 ID、用户名、展示名和角色 |
| `dto/CurrentUserResponse.java` | 当前用户响应 |
| `dto/DeleteAccountRequest.java` | 注销账号请求，包含密码 |
| `dto/LoginRequest.java` | 登录请求，包含用户名和密码 |
| `dto/LoginResponse.java` | 登录响应，包含用户视图 |
| `dto/LogoutResponse.java` | 退出登录或注销后的响应 |
| `dto/RegisterRequest.java` | 注册请求，包含用户名、密码和展示名 |
| `entity/AuthUser.java` | 映射 `auth_user` 表 |
| `mapper/AuthUserMapper.java` | `BaseMapper<AuthUser>` |
| `session/LoginUser.java` | 保存到 Session 的登录用户快照 |

## `chat` 模块

AI 对话、流式响应、会话和消息模块。

```text
chat/
├─ controller/
│  └─ ChatController.java
├─ dto/
│  ├─ ChatMessage.java
│  ├─ ChatRequest.java
│  ├─ ChatResponse.java
│  ├─ ChatStreamEvent.java
│  ├─ ConversationDetailResponse.java
│  ├─ ConversationMessageResponse.java
│  ├─ ConversationSummaryResponse.java
│  └─ PinConversationRequest.java
├─ entity/
│  ├─ ChatConversation.java
│  └─ ChatMessageEntity.java
├─ mapper/
│  ├─ ChatConversationMapper.java
│  └─ ChatMessageMapper.java
└─ service/
   ├─ ChatMessageCacheService.java
   └─ ChatService.java
```

### `ChatController.java`

AI 对话和会话管理接口。

接口：

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `GET` | `/api/models` | 查询前端可选择的 AI 模型列表 |
| `POST` | `/api/chat` | 发起普通非流式 AI 对话 |
| `POST` | `/api/chat/stream` | 发起 SSE 流式 AI 对话 |
| `GET` | `/api/conversations` | 查询当前用户的会话列表 |
| `GET` | `/api/conversations/{conversationId}` | 查询指定会话详情和消息列表 |
| `POST/PATCH` | `/api/conversations/{conversationId}/pin` | 更新会话置顶状态 |
| `POST` | `/api/conversations/{conversationId}/unpin` | 取消会话置顶 |
| `DELETE` | `/api/conversations/{conversationId}` | 删除指定会话及其消息 |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `models()` | 通过 `AiInfraClient` 查询 ai-infra 暴露的模型列表 |
| `chat(...)` | 校验登录用户并调用普通对话 |
| `streamChat(...)` | 校验登录用户并创建 SSE 对话 |
| `conversations(...)` | 查询当前用户会话列表 |
| `conversationDetail(...)` | 查询当前用户指定会话详情 |
| `updateConversationPin(...)` | 更新会话置顶状态 |
| `unpinConversation(...)` | 取消会话置顶 |
| `deleteConversation(...)` | 删除会话 |

### `ChatService.java`

AI 对话核心业务服务。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 普通对话 | 通过 `AiInfraClient` 远程调用 ai-infra 获取完整响应 |
| 流式对话 | 使用 `SseEmitter` 推送 `start`、`delta`、`done`、`error` 事件 |
| 会话管理 | 创建、查询、置顶、取消置顶、删除会话 |
| 消息保存 | 保存 user / assistant 消息、模型、响应耗时和 token |
| 请求构造 | 按历史消息构造 ai-api 中的 `LLMRequest` |
| 缓存 | 读取和失效 Redis 会话消息缓存 |
| 兜底响应 | 模型调用失败时返回友好兜底内容并记录日志 |
| 统计 | 估算 token、读取模型 usage、记录 `event=ai_chat_completed` |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `chat(AuthUser authUser, ChatRequest request)` | 发起普通非流式 AI 对话 |
| `streamChat(AuthUser authUser, ChatRequest request)` | 创建 SSE 流式 AI 对话 |
| `listConversations(AuthUser authUser)` | 查询当前用户会话列表 |
| `getConversationDetail(...)` | 查询会话详情和消息 |
| `updateConversationPin(...)` | 更新置顶状态 |
| `unpinConversation(...)` | 取消置顶 |
| `deleteConversation(...)` | 删除会话和消息 |
| `doStreamChat(...)` | 执行流式模型调用和事件推送 |
| `buildLlmRequest(...)` | 构造 ai-infra 远程调用请求 |
| `loadConversationMessages(...)` | 从缓存或数据库加载历史消息 |
| `evictConversationMessagesAfterCommit(...)` | 事务提交后清理消息缓存 |
| `usageFrom(...)` | 从 `LLMResponse` 提取 token usage |
| `estimateTokenCount(String content)` | 估算 token 数 |
| `buildConversationTitle(String content)` | 根据首条消息生成会话标题 |

### `ChatMessageCacheService.java`

会话消息 Redis 缓存服务。

核心方法：

| 方法 | 功能 |
| --- | --- |
| `getMessages(Long userId, Long conversationId, Supplier<List<ChatMessageEntity>> dbLoader)` | 从 Redis 读取会话消息缓存，缓存缺失时回源并写回 |
| `putMessages(...)` | 写入会话消息缓存 |
| `evictMessages(...)` | 删除会话消息缓存 |
| `cacheKey(...)` | 生成缓存 key |
| `CachedChatMessage.from(...)` | 从数据库消息实体转换缓存消息 |

### DTO、Entity、Mapper 文件

| 文件 | 功能 |
| --- | --- |
| `dto/ChatMessage.java` | 前端提交的单条聊天消息 |
| `dto/ChatRequest.java` | 对话请求，包含消息、模型、会话 ID、think 模式 |
| `dto/ChatResponse.java` | 普通对话响应 |
| `dto/ChatStreamEvent.java` | SSE 事件结构，提供 `start`、`delta`、`done`、`error` 工厂方法 |
| `dto/ConversationDetailResponse.java` | 会话详情和消息列表响应 |
| `dto/ConversationMessageResponse.java` | 单条会话消息响应 |
| `dto/ConversationSummaryResponse.java` | 会话列表摘要响应 |
| `dto/PinConversationRequest.java` | 置顶状态请求，`pinnedEnabled()` 处理空值 |
| `entity/ChatConversation.java` | 映射 `chat_conversation` 表 |
| `entity/ChatMessageEntity.java` | 映射 `chat_message` 表 |
| `mapper/ChatConversationMapper.java` | `BaseMapper<ChatConversation>` |
| `mapper/ChatMessageMapper.java` | `BaseMapper<ChatMessageEntity>` |

## `common` 模块

统一错误响应、链路日志和公共 Redis 信号量模块。

```text
common/
├─ ApiErrorResponse.java
├─ BusinessException.java
├─ GlobalExceptionHandler.java
├─ RequestIdFilter.java
└─ service/
   └─ RedisSemaphoreService.java
```

### `ApiErrorResponse.java`

统一错误响应结构。

字段：

| 字段 | 功能 |
| --- | --- |
| `status` | HTTP 状态码 |
| `message` | 前端展示的错误信息 |
| `timestamp` | 错误发生时间 |

### `BusinessException.java`

带 HTTP 状态码的业务异常。

核心方法：

| 方法 | 功能 |
| --- | --- |
| `BusinessException(HttpStatus status, String message)` | 创建带状态码和消息的业务异常 |
| `getStatus()` | 获取业务异常对应的 HTTP 状态码 |

### `GlobalExceptionHandler.java`

全局异常响应处理器。

核心方法：

| 方法 | 功能 |
| --- | --- |
| `handleBusiness(...)` | 处理 `BusinessException` |
| `handleValidation(...)` | 处理参数校验异常 |
| `handleBadRequest(...)` | 处理请求体、请求参数和类型转换异常 |
| `handleMaxUploadSize(...)` | 处理 multipart 文件大小超限，返回 `413` 和 `文件大小不能超过 200MB` |
| `handleMultipart(...)` | 处理 multipart 请求异常 |
| `handleUnexpected(...)` | 处理未预期系统异常 |
| `requestId()` | 从 MDC 读取 requestId |
| `sanitizeLogValue(...)` | 清洗日志文本 |

### `RequestIdFilter.java`

RequestId 链路追踪过滤器。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 生成 requestId | 请求头没有合法 `X-Request-Id` 时生成新的 requestId |
| 透传 requestId | 请求头已有合法 `X-Request-Id` 时继续使用 |
| 写入 MDC | 将 requestId 写入 MDC，日志 pattern 自动带上 |
| 写入响应头 | 将 requestId 写回响应头 |
| 访问日志 | 记录 method、path、status、costMs、clientIp、userAgent |
| 慢请求 | 超过 `app.logging.slow-request-threshold-ms` 时使用 WARN |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `doFilterInternal(...)` | 处理 requestId、继续过滤链、记录访问日志 |
| `resolveRequestId(...)` | 解析或生成 requestId |
| `resolveClientIp(...)` | 从 `X-Forwarded-For` 或 `remoteAddr` 解析客户端 IP |
| `sanitizeLogValue(...)` | 清洗日志文本 |

### `RedisSemaphoreService.java`

Redis 分布式信号量服务。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 获取许可 | 使用 Redis ZSET + Lua 原子判断并发数是否小于最大许可数 |
| 许可租约 | ZSET score 使用过期时间，过期许可会被清理 |
| 自动释放 | `Permit` 实现 `AutoCloseable`，适合 `try-with-resources` |
| 释放兜底 | 释放失败只记录 warn，许可最终依赖 TTL 过期 |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `tryAcquire(String name, int maxPermits, Duration leaseTtl)` | 尝试获取信号量许可 |
| `release(String name, String key, String permitId)` | 释放指定许可 |
| `Permit.close()` | 自动释放许可 |
| `sanitizeLogValue(String value)` | 清洗日志文本 |

## `config` 模块

Spring Bean 和配置属性模块。

```text
config/
├─ AiInfraProperties.java
├─ AuthProperties.java
├─ ConcurrencyLimitProperties.java
├─ MybatisPlusAutoFillConfig.java
├─ ObjectStorageProperties.java
├─ PasswordConfig.java
├─ RagProperties.java
└─ WebConfig.java
```

### 配置属性文件

| 文件 | 配置前缀 | 功能 |
| --- | --- | --- |
| `AiInfraProperties.java` | `app.ai-infra` | 保存 ai-infra baseUrl 和远程调用超时时间 |
| `AuthProperties.java` | `app.auth` | 保存种子管理员用户名和密码 |
| `ConcurrencyLimitProperties.java` | `app.concurrency` | 保存上传兜底并发和 ingestion 消费并发配置 |
| `ObjectStorageProperties.java` | `app.storage` | 保存 RustFS / S3 provider、endpoint、accessKey、secretKey、bucket |
| `RagProperties.java` | `app.ai.rag` | 保存 RAG 模型、维度、向量表、分块默认值、源文件大小和 MQ topic |

### `ConcurrencyLimitProperties.java`

高成本任务并发限制配置。

| 配置项 | 功能 | 默认值 |
| --- | --- | --- |
| `upload` | service 上传兜底并发限制 | `10 / 10m` |
| `ingestion` | RocketMQ 分块 / 向量化消费并发限制 | `5 / 30m` |

### `RagProperties.java`

RAG 入库和向量检索配置。

| 配置项 | 功能 | 默认值 |
| --- | --- | --- |
| `embeddingModel` | Embedding 模型 | `Qwen/Qwen3-Embedding-8B` |
| `rerankerModel` | Reranker 模型 | `Qwen/Qwen3-Reranker-8B` |
| `embeddingDimensions` | Embedding 维度 | `1024` |
| `vectorIndexType` | pgvector 索引类型 | `HNSW` |
| `vectorTableName` | 向量表名 | `knowledge_chunk_vector` |
| `defaultChunkSize` | 默认分块大小 | `1000` |
| `defaultChunkOverlap` | 默认分块重叠 | `150` |
| `defaultMaxChunks` | 默认最大分块数 | `200` |
| `minChunkSize` | 最小分块大小 | `80` |
| `maxSourceBytes` | RAG 源文件最大字节数 | `209715200` |
| `ingestionTopic` | RocketMQ topic | `rag-ingestion-task` |
| `ingestionConsumerGroup` | RocketMQ consumer group | `yinbo-agent-ingestion-consumer` |

### `AiInfraProperties.java`

backend 调用 ai-infra 的远程配置。

| 配置项 | 功能 | 默认值 |
| --- | --- | --- |
| `baseUrl` | ai-infra HTTP 地址 | `http://localhost:8082` |
| `requestTimeout` | backend 调用 ai-infra 的超时时间 | `5m` |

### Bean 配置文件

| 文件 | Bean / 方法 | 功能 |
| --- | --- | --- |
| `PasswordConfig.java` | `passwordEncoder()` | 创建 BCrypt 密码编码器 |
| `MybatisPlusAutoFillConfig.java` | `insertFill(...)` | 插入时自动填充 `createdAt` 和 `updatedAt` |
| `MybatisPlusAutoFillConfig.java` | `updateFill(...)` | 更新时自动填充 `updatedAt` |
| `WebConfig.java` | `addInterceptors(...)` | 注册登录拦截器保护 `/api/auth/me`、`/api/chat`、`/api/conversations/**`、`/api/ingestion/**`、`/api/admin/**` |

## `infra` 模块

backend 调用外部基础设施服务的客户端模块。

```text
infra/
└─ ai/
   └─ AiInfraClient.java
```

### `AiInfraClient.java`

ai-infra 远程 HTTP 客户端，同时实现 ai-api 中的 `LLMService`、`EmbeddingService`、`RerankService`。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 模型列表 | 调用 `GET /internal/ai/models`，供 `/api/models` 返回前端模型下拉列表 |
| 普通对话 | 调用 `POST /internal/ai/chat` |
| 流式对话 | 调用 `POST /internal/ai/chat/stream`，解析 NDJSON 并转发 delta 给 SSE |
| 向量化 | 调用 `POST /internal/ai/embeddings`，把 JSON 数组转回 `float[]` |
| 重排序 | 调用 `POST /internal/ai/rerank` |

## `storage` 模块

RustFS / S3 对象存储封装模块。当前使用 MinIO Client 访问 RustFS，因为 RustFS 兼容 S3 协议。

```text
storage/
├─ StoredObject.java
└─ service/
   └─ ObjectStorageService.java
```

### `StoredObject.java`

对象存储结果结构。

字段：

| 字段 | 功能 |
| --- | --- |
| `provider` | 存储提供方 |
| `bucket` | bucket 名称 |
| `objectKey` | 对象 key |
| `etag` | 对象 ETag |
| `sizeBytes` | 对象大小 |

### `ObjectStorageService.java`

对象存储服务。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 上传原始文档 | 写入 RustFS bucket 并返回 `StoredObject` |
| 打开对象 | 根据 bucket 和 objectKey 返回输入流给 Tika |
| 静默删除 | 删除原始对象，失败只记录日志 |
| bucket 兜底 | 上传前确保 bucket 存在 |
| key 生成 | 使用 `ingestion/original/{yyyy}/{MM}/{dd}/{uuid}/{fileName}` |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `uploadOriginalDocument(...)` | 上传原始文档 |
| `open(String bucket, String objectKey)` | 打开对象输入流 |
| `deleteQuietly(...)` | 静默删除对象 |
| `ensureBucket()` | 确保 bucket 存在 |
| `originalDocumentObjectKey(...)` | 生成原始文档对象 key |
| `sanitizeObjectFileName(...)` | 清洗对象文件名 |

## `ingestion` 模块

文档上传、URL 入库、解析、清洗、分块、向量化和 MQ 消费模块。

```text
ingestion/
├─ cleaner/
│  └─ DocumentTextCleaner.java
├─ controller/
│  ├─ IngestionController.java
│  └─ IngestionTaskAdminController.java
├─ dto/
│  ├─ IngestionTaskResponse.java
│  ├─ IngestionResponse.java
│  └─ UrlIngestionRequest.java
├─ entity/
│  ├─ IngestionTask.java
│  ├─ KnowledgeChunk.java
│  └─ KnowledgeDocument.java
├─ mapper/
│  ├─ IngestionTaskMapper.java
│  ├─ KnowledgeChunkMapper.java
│  └─ KnowledgeDocumentMapper.java
├─ model/
│  ├─ ChunkingOptions.java
│  ├─ ChunkingStrategy.java
│  ├─ DocumentChunk.java
│  ├─ DocumentSourceType.java
│  ├─ ParsedDocument.java
│  └─ RawDocument.java
├─ optimizer/
│  └─ DocumentChunkOptimizer.java
├─ parser/
│  └─ TikaDocumentParser.java
├─ queue/
│  ├─ DocumentIngestionTaskConsumer.java
│  └─ IngestionTaskMessage.java
├─ service/
│  ├─ DocumentIngestionService.java
│  ├─ IngestionTaskAdminService.java
│  └─ IngestionTaskService.java
├─ source/
│  └─ DocumentSourceReader.java
├─ splitter/
│  └─ RecursiveDocumentChunkSplitter.java
└─ vector/
   ├─ PgVectorRepository.java
   └─ PgVectorRow.java
```

### `IngestionController.java`

文档入库接口。

接口：

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `POST` | `/api/ingestion/documents/upload` | 上传文件并创建待分块文档 |
| `POST` | `/api/ingestion/documents/url` | 读取 URL 内容并创建待分块文档 |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `uploadDocument(...)` | 校验管理员后调用 `DocumentIngestionService.ingestUpload(...)` |
| `ingestUrl(...)` | 校验管理员后调用 `DocumentIngestionService.ingestUrl(...)` |

### `IngestionTaskAdminController.java`

管理后台入库任务接口。

接口：

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `GET` | `/api/admin/ingestion/tasks/failed` | 查询失败或死信状态的入库任务 |
| `POST` | `/api/admin/ingestion/tasks/{taskId}/retry` | 手动重试失败入库任务 |
| `DELETE` | `/api/admin/ingestion/tasks/{taskId}` | 删除失败入库任务 |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `failedTasks(...)` | 校验管理员后返回失败任务列表 |
| `retryTask(...)` | 校验管理员后重新投递失败任务 |
| `deleteTask(...)` | 校验管理员后删除失败任务 |

### `DocumentSourceReader.java`

文档来源读取器。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 上传读取 | 校验 MultipartFile 非空和大小，保存原始文件到对象存储 |
| URL 下载 | 校验 HTTP/HTTPS URL，下载内容并保存到对象存储 |
| SSRF 防护 | 阻止 localhost、内网、链路本地、组播、CGNAT、ULA 等地址 |
| 重定向控制 | 最多跟随 `5` 次 HTTP 重定向 |
| 大小限制 | 使用 `RagProperties.maxSourceBytes()`，默认 `200MB` |
| 文件名清洗 | 清洗 URL 或上传文件名 |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `fromUpload(MultipartFile file)` | 读取上传文件并保存原始文件 |
| `fromUrl(String rawUrl, String requestedFileName)` | 下载 URL 内容并保存原始文件 |
| `parseHttpUri(...)` | 解析并校验 HTTP URL |
| `openSafeConnection(...)` | 打开带 SSRF 防护和重定向限制的连接 |
| `validateSafeRemoteUri(...)` | 校验远程地址安全性 |
| `readWithLimit(...)` | 读取流并限制最大字节数 |
| `sanitizeFileName(...)` | 清洗文件名 |

### `DocumentIngestionService.java`

文档入库核心业务服务。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 上传入库 | 先创建 `UPLOADING` 状态文档记录，RustFS 保存成功后改为 `UPLOADED` |
| URL 入库 | 下载 URL 后创建 `UPLOADED` 状态文档记录 |
| 上传兜底并发 | 使用 `service:ingestion:upload:global` Redis 信号量 |
| 分块处理 | 将文档状态置为 `PROCESSING`，解析、清洗、分块、优化、向量化 |
| 向量重建 | 事务外生成 embedding，短事务内写入新向量、更新分块并删除旧向量 |
| 向量写入 | 使用 `PgVectorRepository` 直接写入 `knowledge_chunk_vector`，和 `knowledge_chunk` 共用 PostgreSQL 事务 |
| 失败处理 | 标记 `FAILED`，记录错误信息和耗时 |
| 对象清理 | 入库失败时清理已上传的原始文件 |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `ingestUpload(AuthUser, MultipartFile, ...)` | 上传文件并创建默认知识库外的待处理文档 |
| `ingestUpload(AuthUser, KnowledgeBase, MultipartFile, ...)` | 上传文件并创建指定知识库下的待处理文档 |
| `ingestUrl(AuthUser, String, ...)` | 录入 URL 并创建默认知识库外的待处理文档 |
| `ingestUrl(AuthUser, KnowledgeBase, String, ...)` | 录入 URL 并创建指定知识库下的待处理文档 |
| `processDocument(String documentId, ChunkingOptions options)` | 执行分块和向量化 |
| `rebuildDocumentVectors(String documentId)` | 重建文档已有分块的向量 |
| `markDocumentFailed(String documentId, String message)` | 在重试耗尽时把文档标记为失败 |
| `withUploadPermit(...)` | 在上传并发许可保护下执行上传动作 |
| `embedTexts(...)` | 事务外调用 Embedding 模型生成向量 |
| `toVectorRow(...)` | 组装待写入 PGVector 表的向量行 |
| `validateChunksForEmbedding(...)` | 校验分块适合向量化 |
| `parseDocument(...)` | 调用 Tika 解析文档 |
| `toRawDocument(...)` | 从文档实体还原原始文档定位信息 |
| `requireDocumentForUpdate(...)` | 最终提交时锁定文档行，防止并发提交 |
| `markFailed(...)` | 标记文档处理失败 |
| `toResponse(...)` | 转换为入库响应 |

### `IngestionTaskService.java`

文档入库任务状态服务。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 创建任务 | 分块或重建向量投递前写入 `PENDING` 任务 |
| 投递记录 | MQ 投递成功后记录 messageId，投递失败后记录错误 |
| 消费状态 | 只有 `PENDING` / `RETRYING` 可以 CAS 抢占为 `RUNNING`，成功后标记 `COMPLETED` |
| 失败记录 | 不可重试失败标记 `FAILED`，可重试失败累计 retryCount |
| 死信前置 | retryCount 达到 `maxRetries` 时标记 `DEAD` |
| 手动重试 | 通过事务消息重置失败任务为 `PENDING` 并重新投递 |

### `IngestionTaskProducerService.java`

文档入库任务事务消息发送服务。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 分块提交 | 发送 CHUNK 事务半消息，等待本地事务提交 |
| 重建向量提交 | 发送 REBUILD_VECTORS 事务半消息，等待本地事务提交 |
| 手动重试提交 | 发送 RETRY 事务半消息，等待本地事务重置失败任务 |
| 事务标识 | 半消息携带 taskId、documentId、action、transactionType 和 requestId |

### `IngestionTaskTransactionListener.java`

RocketMQ 事务消息监听器。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 本地事务 | 半消息发送成功后执行文档状态 CAS 和任务表更新 |
| 分块 CAS | `UPLOADED` / `COMPLETED` -> `PROCESSING` |
| 重建 CAS | `COMPLETED` -> `PROCESSING` |
| 重试 CAS | `FAILED` -> `PROCESSING`，并把任务重置为 `PENDING` |
| Broker 回查 | 根据 taskId 和 sourceRequestId 判断事务消息是否应提交 |

### `IngestionTaskAdminService.java`

管理后台入库任务服务。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 失败任务列表 | 查询 `FAILED` 和 `DEAD` 状态任务并转换后台响应 |
| 手动重试 | 校验任务和文档状态后发送 RocketMQ 事务消息 |
| 删除任务 | 删除 `FAILED` 或 `DEAD` 状态任务记录 |
| 失败兜底 | 重试投递失败时回写任务和文档失败原因 |

文档状态：

| 状态 | 功能 |
| --- | --- |
| `UPLOADING` | 原始文件正在上传到 RustFS，不能分块 |
| `UPLOADED` | 原始文件已保存，等待分块 |
| `PROCESSING` | MQ 消费处理中 |
| `COMPLETED` | 解析、分块、向量化完成 |
| `FAILED` | 入库或重建失败 |

### `DocumentIngestionTaskConsumer.java`

RocketMQ 文档入库任务消费者。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 消费 CHUNK | 调用 `DocumentIngestionService.processDocument(...)` |
| 消费 REBUILD_VECTORS | 调用 `DocumentIngestionService.rebuildDocumentVectors(...)` |
| 绑定 requestId | 将消息中的 `sourceRequestId` 放入 MDC |
| 消费并发限制 | 使用 Redis 信号量限制全局 ingestion 消费并发 |
| 任务状态记录 | 根据 `taskId` 更新 `ingestion_task` 的运行、完成、失败、重试和 DEAD 状态 |
| 重试控制 | 可重试失败抛异常交给 RocketMQ 重试，不可重试失败正常 ACK |
| 死信控制 | 消费者最大重试次数和 `ingestion_task.maxRetries` 对齐，DEAD 任务继续抛异常等待 Broker 投递 DLQ |
| MQ 日志 | 记录消费开始、完成、失败、并发限制和不可用日志 |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `onMessage(IngestionTaskMessage message)` | RocketMQ 消息入口 |
| `consumeWithIngestionPermit(...)` | 在 ingestion 许可保护下执行消费 |
| `handleExecutionResult(...)` | 根据入库执行结果决定 ACK、重试或标记任务失败 |
| `bindRequestId(...)` | 将源请求 requestId 绑定到 MDC |
| `elapsedMillis(...)` | 计算消费耗时 |
| `sanitizeLogValue(...)` | 清洗日志文本 |

### `IngestionTaskMessage.java`

RocketMQ 文档入库任务消息。

主要功能：

| 功能 | 说明 |
| --- | --- |
| CHUNK 消息 | 保存文档 ID 和分块参数 |
| REBUILD_VECTORS 消息 | 通过 `rebuildVectors(...)` 创建重建向量消息 |
| taskId 串联 | 关联 `ingestion_task.task_no`，用于后台失败任务展示和手动重试 |
| requestId 串联 | 读取 MDC 中的 requestId 作为 `sourceRequestId` |
| action 兜底 | `resolvedAction()` 默认回退为 `CHUNK` |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `rebuildVectors(String documentId)` | 创建重建向量消息 |
| `resolvedRequestId()` | 返回消息 requestId 或 `-` |
| `currentRequestId()` | 从 MDC 读取当前 requestId |
| `resolvedAction()` | 返回消息 action 或默认 `CHUNK` |

### 解析、清洗、分块和优化文件

| 文件 | 核心方法 | 功能 |
| --- | --- | --- |
| `cleaner/DocumentTextCleaner.java` | `clean(String rawText)` | 统一换行、压缩空白、清洗不可见字符 |
| `parser/TikaDocumentParser.java` | `parse(RawDocument rawDocument)` | 使用 Apache Tika 解析上传或对象存储中的文档 |
| `parser/TikaDocumentParser.java` | `parserName()` | 返回解析器名称 |
| `splitter/RecursiveDocumentChunkSplitter.java` | `split(...)` | 按标题、分隔符和 chunkSize 递归分块 |
| `optimizer/DocumentChunkOptimizer.java` | `optimize(...)` | 规范化、去重、合并过短分块和自动合并 |

### Model、DTO、Entity、Mapper 文件

| 文件 | 功能 |
| --- | --- |
| `model/ChunkingOptions.java` | 保存分块策略、大小、重叠和最大分块数；`from(...)` 从配置和请求构造；`adaptForTextLength(...)` 按文本长度调整 |
| `model/ChunkingStrategy.java` | 分块策略枚举，支持 `AUTO`、`RECURSIVE`、`NONE`，`from(...)` 解析请求值 |
| `model/DocumentChunk.java` | 内存中的分块结构 |
| `model/DocumentSourceType.java` | 文档来源枚举，包含 `UPLOAD`、`URL` |
| `model/ParsedDocument.java` | Tika 解析后的标题、文本和 metadata |
| `model/RawDocument.java` | 原始文档定位信息，`hasStoredObject()` 判断是否有对象存储定位 |
| `dto/IngestionResponse.java` | 上传或 URL 入库响应 |
| `dto/UrlIngestionRequest.java` | URL 入库请求 |
| `entity/KnowledgeDocument.java` | 映射 `knowledge_document` 表 |
| `entity/KnowledgeChunk.java` | 映射 `knowledge_chunk` 表 |
| `mapper/KnowledgeDocumentMapper.java` | `BaseMapper<KnowledgeDocument>` |
| `mapper/KnowledgeChunkMapper.java` | `BaseMapper<KnowledgeChunk>` |

### 入库链路

```text
上传文件 / 提交 URL
-> DocumentIngestionService 创建 knowledge_document，status = UPLOADING
-> DocumentSourceReader 保存原始文件到 RustFS
-> knowledge_document.status = UPLOADED
-> 管理员点击分块后发送 RocketMQ CHUNK 事务半消息
-> 本地事务 CAS：knowledge_document.status -> PROCESSING，创建 ingestion_task
-> DocumentIngestionTaskConsumer 获取 ingestion Redis 信号量
-> DocumentIngestionService.processDocument
-> TikaDocumentParser 解析
-> DocumentTextCleaner 清洗
-> RecursiveDocumentChunkSplitter 分块
-> DocumentChunkOptimizer 优化
-> 事务外通过 AiInfraClient 调用 ai-infra 生成向量
-> 短事务内写入 knowledge_chunk_vector 和 knowledge_chunk
-> knowledge_document.status = COMPLETED / FAILED
```

## `knowledge` 模块

后台知识库、文档和分块管理模块。

```text
knowledge/
├─ controller/
│  └─ KnowledgeAdminController.java
├─ dto/
│  ├─ ChunkEnabledRequest.java
│  ├─ CreateKnowledgeBaseRequest.java
│  ├─ KnowledgeBaseResponse.java
│  ├─ KnowledgeChunkResponse.java
│  ├─ KnowledgeDocumentResponse.java
│  ├─ KnowledgeOverviewResponse.java
│  ├─ KnowledgeUrlIngestionRequest.java
│  ├─ RechunkDocumentRequest.java
│  ├─ UpdateChunkRequest.java
│  └─ UpdateKnowledgeBaseRequest.java
├─ entity/
│  └─ KnowledgeBase.java
├─ mapper/
│  └─ KnowledgeBaseMapper.java
└─ service/
   └─ KnowledgeAdminService.java
```

### `KnowledgeAdminController.java`

管理后台知识库接口。

接口：

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `GET` | `/api/admin/knowledge/overview` | 查询知识库后台概览统计 |
| `GET` | `/api/admin/knowledge/bases` | 查询知识库列表 |
| `POST` | `/api/admin/knowledge/bases` | 创建知识库 |
| `GET` | `/api/admin/knowledge/bases/{knowledgeBaseId}` | 查询知识库详情 |
| `PATCH` | `/api/admin/knowledge/bases/{knowledgeBaseId}` | 更新知识库基础信息 |
| `DELETE` | `/api/admin/knowledge/bases/{knowledgeBaseId}` | 删除知识库 |
| `GET` | `/api/admin/knowledge/bases/{knowledgeBaseId}/documents` | 查询知识库下的文档列表 |
| `POST` | `/api/admin/knowledge/bases/{knowledgeBaseId}/documents/upload` | 向指定知识库上传文档 |
| `POST` | `/api/admin/knowledge/bases/{knowledgeBaseId}/documents/url` | 向指定知识库录入 URL 文档 |
| `GET` | `/api/admin/knowledge/documents/{documentId}` | 查询文档详情 |
| `GET` | `/api/admin/knowledge/documents/{documentId}/chunks` | 查询文档分块列表 |
| `POST` | `/api/admin/knowledge/documents/{documentId}/rechunk` | 投递文档重新分块任务 |
| `POST` | `/api/admin/knowledge/documents/{documentId}/vectors/rebuild` | 投递文档向量重建任务 |
| `PATCH` | `/api/admin/knowledge/documents/{documentId}/chunks/enabled` | 批量更新文档分块启用状态 |
| `DELETE` | `/api/admin/knowledge/documents/{documentId}` | 删除指定文档 |
| `PATCH` | `/api/admin/knowledge/chunks/{chunkId}/enabled` | 更新单个分块启用状态 |
| `PATCH` | `/api/admin/knowledge/chunks/{chunkId}` | 更新单个分块内容 |
| `DELETE` | `/api/admin/knowledge/chunks/{chunkId}` | 删除单个分块 |

### `KnowledgeAdminService.java`

管理后台知识库业务服务。

主要功能：

| 功能 | 说明 |
| --- | --- |
| 概览统计 | 统计知识库数量、文档数量、有文档的知识库数量和当前 Embedding 模型 |
| 知识库管理 | 创建、查询、更新、删除知识库 |
| 文档管理 | 查询、删除文档，删除时清理分块、向量和原始对象 |
| 分块管理 | 查询、编辑、删除、启用、禁用分块 |
| 忙碌保护 | `UPLOADING` / `PROCESSING` 文档禁止删除和修改分块 |
| MQ 投递 | 投递 CHUNK 和 REBUILD_VECTORS 任务 |
| 向量清理 | 删除文档或分块时同步删除 pgvector 中的向量 |
| 响应转换 | 将实体转换为后台响应 DTO |

核心方法：

| 方法 | 功能 |
| --- | --- |
| `overview()` | 查询知识库概览统计 |
| `create(AuthUser adminUser, CreateKnowledgeBaseRequest request)` | 创建知识库 |
| `list()` | 查询知识库列表 |
| `detail(String knowledgeBaseId)` | 查询知识库详情 |
| `updateKnowledgeBase(...)` | 更新知识库基础信息 |
| `deleteKnowledgeBase(...)` | 删除知识库及其文档和分块 |
| `requireKnowledgeBase(...)` | 根据业务编号获取知识库 |
| `listDocuments(...)` | 查询知识库下的文档列表 |
| `documentDetail(...)` | 查询文档详情 |
| `listChunks(...)` | 查询文档分块列表 |
| `rechunkDocument(...)` | 投递文档重新分块任务 |
| `rebuildDocumentVectors(...)` | 投递文档向量重建任务 |
| `deleteDocument(...)` | 删除指定文档 |
| `updateChunkEnabled(...)` | 更新单个分块启用状态 |
| `updateChunk(...)` | 更新单个分块内容 |
| `updateDocumentChunksEnabled(...)` | 批量更新文档分块启用状态 |
| `deleteChunk(...)` | 删除单个分块 |
| `deleteVectorDocuments(...)` | 删除向量存储中的向量文档 |
| `deleteOriginalDocumentAfterCommit(...)` | 事务提交后删除原始对象 |
| `toKnowledgeBaseResponse(...)` | 转换知识库响应 |
| `toDocumentResponse(...)` | 转换文档响应 |
| `toChunkResponse(...)` | 转换分块响应 |

### DTO、Entity、Mapper 文件

| 文件 | 功能 |
| --- | --- |
| `dto/ChunkEnabledRequest.java` | 分块启用状态请求，`enabledValue()` 处理空值 |
| `dto/CreateKnowledgeBaseRequest.java` | 创建知识库请求 |
| `dto/KnowledgeBaseResponse.java` | 知识库响应 |
| `dto/KnowledgeChunkResponse.java` | 分块响应 |
| `dto/KnowledgeDocumentResponse.java` | 文档响应 |
| `dto/KnowledgeOverviewResponse.java` | 知识库概览响应 |
| `dto/KnowledgeUrlIngestionRequest.java` | 指定知识库 URL 入库请求 |
| `dto/RechunkDocumentRequest.java` | 重新分块请求 |
| `dto/UpdateChunkRequest.java` | 更新分块内容请求 |
| `dto/UpdateKnowledgeBaseRequest.java` | 更新知识库请求 |
| `entity/KnowledgeBase.java` | 映射 `knowledge_base` 表 |
| `mapper/KnowledgeBaseMapper.java` | `BaseMapper<KnowledgeBase>` |

## Actuator 安全收口

service 的 Actuator 默认只保留健康检查和基础信息。

主要配置：

| 配置 | 功能 |
| --- | --- |
| `management.endpoints.access.default=none` | 默认禁止访问所有 actuator endpoint |
| `management.endpoint.health.access=read-only` | 只读开放健康检查 |
| `management.endpoint.info.access=read-only` | 只读开放基础信息 |
| `management.endpoints.web.exposure.include=health,info` | Web 入口只暴露 `health` 和 `info` |
| `management.endpoint.health.show-details=never` | 健康检查不展示组件详情 |
| `management.endpoint.env.show-values=never` | 即使以后启用 env，也不展示配置值 |
| `management.endpoint.configprops.show-values=never` | 即使以后启用 configprops，也不展示配置值 |

默认可访问：

```text
/actuator/health
/actuator/info
```

默认不可暴露：

```text
/actuator/env
/actuator/configprops
/actuator/beans
/actuator/mappings
```

## 功能清单

| 功能 | 主要实现位置 |
| --- | --- |
| 登录注册 | `AuthController`、`AuthService`、`SessionAuthService` |
| Session 登录态 | `SessionAuthService`、`LoginInterceptor`、`WebConfig` |
| gateway 用户维度限流所需用户 ID | `AuthConstants.LOGIN_USER_ID_SESSION_KEY`、`SessionAuthService.createLoginSession(...)` |
| 管理员权限校验 | `AdminGuard` |
| 后台仪表盘 | `AdminDashboardController`、`AdminDashboardService` |
| 普通 AI 对话 | `ChatController`、`ChatService.chat(...)` |
| SSE 流式 AI 对话 | `ChatController`、`ChatService.streamChat(...)` |
| 会话管理 | `ChatController`、`ChatService`、`ChatMessageCacheService` |
| 统一业务异常 | `BusinessException`、`GlobalExceptionHandler`、`ApiErrorResponse` |
| requestId 链路日志 | `RequestIdFilter` |
| Redis 兜底信号量 | `RedisSemaphoreService` |
| RustFS 原始文件存储 | `ObjectStorageService`、`StoredObject` |
| 上传文件入库 | `IngestionController`、`DocumentSourceReader`、`DocumentIngestionService` |
| URL 文档入库 | `IngestionController`、`DocumentSourceReader`、`DocumentIngestionService` |
| SSRF 防护 | `DocumentSourceReader` |
| 文档解析 | `TikaDocumentParser` |
| 文本清洗 | `DocumentTextCleaner` |
| 分块 | `ChunkingOptions`、`ChunkingStrategy`、`RecursiveDocumentChunkSplitter` |
| 分块优化 | `DocumentChunkOptimizer` |
| RocketMQ 入库任务 | `IngestionTaskMessage`、`DocumentIngestionTaskConsumer`、`KnowledgeAdminService` |
| 入库任务状态记录 | `IngestionTask`、`IngestionTaskMapper`、`IngestionTaskService` |
| 失败任务后台管理 | `IngestionTaskAdminController`、`IngestionTaskAdminService` |
| 向量写入和重建 | `DocumentIngestionService`、`PgVectorRepository`、`AiInfraClient` |
| 知识库管理 | `KnowledgeAdminController`、`KnowledgeAdminService` |
| 文档和分块管理 | `KnowledgeAdminController`、`KnowledgeAdminService` |
| 数据库迁移 | `resources/db/migration/V1__init_schema.sql`、`resources/db/migration/V2__create_ingestion_task.sql` |
| Actuator 安全收口 | `application.yml` |
