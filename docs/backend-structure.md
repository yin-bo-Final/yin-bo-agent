# 后端项目结构

这份文档专门给后端开发和 RAG ingestion 改动使用。README 讲项目是什么，本文讲后端代码放在哪里、模块边界是什么、改功能先看哪里。

## 目录总览

```text
backend/
├─ pom.xml
└─ src/main/
   ├─ java/com/yinbo/agent/
   │  ├─ YinboAgentServiceApplication.java
   │  ├─ admin/
   │  ├─ auth/
   │  ├─ chat/
   │  ├─ common/
   │  ├─ config/
   │  ├─ ingestion/
   │  ├─ knowledge/
   │  └─ storage/
   └─ resources/
      ├─ application.yml
      └─ db/migration/
         └─ V1__init_schema.sql
```

## 启动类

| 文件 | 说明 |
| --- | --- |
| `YinboAgentServiceApplication` | 后端业务服务启动入口，启用配置属性和 Mapper 扫描 |

## `admin/`

后台管理公共能力。

| 文件或目录 | 说明 |
| --- | --- |
| `AdminGuard` | 管理员权限门卫，后台接口需要先调用 |
| `controller/AdminDashboardController` | `/api/admin/dashboard` |
| `service/AdminDashboardService` | 活跃用户、消息数、会话数、流量数、平均响应时间统计 |
| `dto/AdminDashboardResponse` | Dashboard 响应结构 |

约定：后台新增模块时，优先复用 `AdminGuard.requireAdmin()`，不要在 Controller 里散落角色判断。

## `auth/`

认证、登录态和用户角色。

```text
auth/
├─ LoginInterceptor.java
├─ AuthBootstrapRunner.java
├─ AuthConstants.java
├─ controller/
│  └─ AuthController.java
├─ service/
│  ├─ AuthService.java
│  └─ SessionAuthService.java
├─ dto/
├─ entity/
├─ mapper/
└─ session/
```

职责：

- 注册、登录、退出登录、注销账号
- Session 登录态读写
- Redis Session 承载
- `ADMIN` / `USER` 角色
- 启动时按本地配置创建管理员账号

登录或注册成功后，后端会同时写入：

```text
LOGIN_USER     # 后端业务校验使用的 LoginUser
LOGIN_USER_ID  # gateway 读取后做登录用户维度限流
```

不要相信前端传入的 userId 请求头；gateway 的用户维度限流只读取 Redis Session 中的 `LOGIN_USER_ID`。

相关表：

```text
auth_user
```

## `chat/`

AI 对话、会话和消息统计。

```text
chat/
├─ controller/
│  └─ ChatController.java
├─ service/
│  ├─ ChatService.java
│  └─ ChatMessageCacheService.java
├─ dto/
├─ entity/
└─ mapper/
```

主要链路：

```text
ConversationPage
-> /api/chat 或 /api/chat/stream
-> LoginInterceptor
-> ChatController
-> ChatService
-> Spring AI ChatModel
-> 保存 user / assistant 消息
-> 记录响应耗时和 token
-> 更新会话最近消息时间
```

关键日志：

```text
event=ai_chat_completed
event=ai_call_failed
event=ai_stream_failed
```

相关表：

```text
chat_conversation
chat_message
```

## `common/`

统一错误响应。

| 文件 | 说明 |
| --- | --- |
| `BusinessException` | 带 HTTP 状态码的业务异常 |
| `ApiErrorResponse` | 返回给前端的错误结构 |
| `GlobalExceptionHandler` | 全局异常处理 |
| `service/RedisSemaphoreService` | 基于 Redis ZSET + Lua 的分布式信号量，用于高成本任务并发保护 |

约定：能预期的业务失败抛 `BusinessException`，前端会读取 `message` 并显示在对应弹窗或页面里。

## `config/`

Spring 配置和 `@ConfigurationProperties`。

| 文件 | 说明 |
| --- | --- |
| `AiModelProperties` | `app.ai.models` 模型列表 |
| `AuthProperties` | 本地种子管理员配置 |
| `RagProperties` | RAG 模型、维度、切块参数、RocketMQ topic |
| `ObjectStorageProperties` | RustFS / S3 对象存储配置 |
| `RagVectorStoreConfig` | Spring AI PGVector Store 配置 |
| `MybatisPlusAutoFillConfig` | 自动填充创建时间和更新时间 |
| `PasswordConfig` | BCrypt 密码编码器 |
| `WebConfig` | 登录拦截器和静态 Web 配置，CORS 统一放在 gateway |

重要配置前缀：

```text
spring.datasource
spring.data.redis
spring.session.redis
spring.flyway
spring.ai.openai
rocketmq
app.ai.models
app.ai.rag
app.concurrency
app.auth
app.storage
```

## `storage/`

对象存储封装。当前底层是 RustFS，Java SDK 使用 MinIO Client，因为 RustFS 兼容 S3 协议。

```text
storage/
├─ service/
│  └─ ObjectStorageService.java
└─ StoredObject.java
```

职责：

- 上传原始文档
- 打开对象输入流给 Tika 解析
- 删除对象
- 返回 provider、bucket、objectKey、etag、size 等元数据

对象 key 规则：

```text
ingestion/original/{yyyy}/{MM}/{dd}/{uuid}/{fileName}
```

数据库不保存大文件二进制，只保存对象定位信息。

## `ingestion/`

RAG 文档 ETL 核心模块。它把“原始文档”加工成“可检索向量”。

```text
ingestion/
├─ controller/
│  └─ IngestionController.java
├─ service/
│  └─ DocumentIngestionService.java
├─ model/
│  ├─ RawDocument.java
│  ├─ ParsedDocument.java
│  ├─ DocumentChunk.java
│  ├─ ChunkingOptions.java
│  ├─ ChunkingStrategy.java
│  └─ DocumentSourceType.java
├─ cleaner/
│  └─ DocumentTextCleaner.java
├─ dto/
│  ├─ IngestionResponse.java
│  └─ UrlIngestionRequest.java
├─ entity/
│  ├─ KnowledgeDocument.java
│  └─ KnowledgeChunk.java
├─ mapper/
│  ├─ KnowledgeDocumentMapper.java
│  └─ KnowledgeChunkMapper.java
├─ optimizer/
│  └─ DocumentChunkOptimizer.java
├─ parser/
│  └─ TikaDocumentParser.java
├─ queue/
│  ├─ IngestionTaskMessage.java
│  └─ DocumentIngestionTaskConsumer.java
├─ source/
│  └─ DocumentSourceReader.java
└─ splitter/
   └─ RecursiveDocumentChunkSplitter.java
```

上传阶段：

```text
MultipartFile / URL
-> DocumentSourceReader
-> ObjectStorageService 上传 RustFS
-> createUploadedDocument
-> 插入 knowledge_document
-> status = UPLOADED
```

上传并发限流的第一道防线在 gateway，避免大文件请求进入 service 后才被拒绝。service 层仍会尝试获取 `service:ingestion:upload:global` Redis 信号量，默认全局最多 `10` 个上传任务同时执行，定位是兜底保护，防止绕过 gateway 直连 service。拿不到许可时抛 `BusinessException(429)`，前端会展示统一限流弹窗。许可有租约时间，默认 `10m`，正常路径在 `finally` 等价的 `try-with-resources` 中释放。

分块阶段：

```text
KnowledgeAdminService.rechunkDocument
-> RocketMQ 发送 CHUNK 消息并记录 event=mq_send
-> DocumentIngestionTaskConsumer 记录 event=mq_consume_started / event=mq_consume_completed
-> Redis 信号量限制全局 ingestion 消费并发
-> DocumentIngestionService.processDocument
-> TikaDocumentParser 读取 RustFS 并解析
-> DocumentTextCleaner 清洗文本
-> ChunkingOptions 根据文本长度适配参数
-> RecursiveDocumentChunkSplitter 分块
-> DocumentChunkOptimizer 后处理
-> VectorStore.add 写入 pgvector
-> knowledge_chunk 写入分块元数据
-> status = COMPLETED / FAILED
-> 记录 event=ingestion_completed / event=ingestion_failed
```

重建向量阶段：

```text
KnowledgeAdminService.rebuildDocumentVectors
-> RocketMQ 发送 REBUILD_VECTORS 消息并记录 event=mq_send
-> DocumentIngestionTaskConsumer 记录 MQ 消费日志
-> Redis 信号量限制全局 ingestion 消费并发
-> DocumentIngestionService.rebuildDocumentVectors
-> 读取已有 knowledge_chunk
-> 写入新向量
-> 更新 chunk.vectorDocumentId
-> 事务提交后删除旧向量
```

文档状态：

| 状态 | 含义 | 前端按钮 |
| --- | --- | --- |
| `UPLOADED` | 原始文件已保存，未分块 | 分块 |
| `PROCESSING` | MQ 消费处理中 | 处理中 |
| `COMPLETED` | 入库成功 | 重新分块 |
| `FAILED` | 入库失败 | 分块 |

相关表：

```text
knowledge_document
knowledge_chunk
knowledge_chunk_vector
```

## `knowledge/`

后台知识库管理接口。

```text
knowledge/
├─ controller/
│  └─ KnowledgeAdminController.java
├─ service/
│  └─ KnowledgeAdminService.java
├─ dto/
├─ entity/
└─ mapper/
```

职责：

- 知识库概览
- 知识库新建、编辑、删除
- 文档列表、上传、URL 录入、删除
- 投递分块和重建向量任务
- 分块列表、查看、编辑、删除、启用、禁用、批量操作

前端路由到接口的大致对应：

```text
/admin/knowledge
-> GET /api/admin/knowledge/bases

/admin/knowledge/{knowledgeBaseId}
-> GET /api/admin/knowledge/bases/{knowledgeBaseId}/documents

/admin/knowledge/{knowledgeBaseId}/docs/{documentId}
-> GET /api/admin/knowledge/documents/{documentId}/chunks
```

相关表：

```text
knowledge_base
knowledge_document
knowledge_chunk
knowledge_chunk_vector
```

## `resources/`

| 文件 | 说明 |
| --- | --- |
| `application.yml` | 服务端口、数据源、Redis、Session、Flyway、AI、RAG、RocketMQ、RustFS、multipart 限制 |
| `db/migration/V1__init_schema.sql` | Flyway 初始迁移，创建业务表、pgvector 扩展、向量表和 HNSW 索引 |

高成本任务并发配置：

```text
app.concurrency.upload.max-permits      # 默认 10，service 兜底上传并发数
app.concurrency.upload.lease-ttl        # 默认 10m，service 上传许可租约
app.concurrency.ingestion.max-permits   # 默认 5，全局同时分块 / 向量化任务数
app.concurrency.ingestion.lease-ttl     # 默认 30m，ingestion 许可租约
```

数据库结构由 Flyway 接管。新增字段、索引或表时，不要恢复 `schema.sql`，应该新增 `V2__xxx.sql` 这类递增迁移脚本。

`knowledge_chunk_vector.embedding` 当前固定为 `vector(1024)`，调整 Embedding 维度时要配套新增迁移并重建向量数据。

## 数据表边界

| 表                        | 主要模块                      | 说明                            |
| ------------------------ | ------------------------- | ----------------------------- |
| `auth_user`              | `auth`                    | 用户、密码哈希、角色、状态                 |
| `chat_conversation`      | `chat`                    | 会话、模型、置顶、最近消息时间               |
| `chat_message`           | `chat`                    | 消息内容、耗时、token                 |
| `knowledge_base`         | `knowledge`               | 知识库名称、collection、Embedding 模型 |
| `knowledge_document`     | `ingestion` / `knowledge` | 文档元数据、RustFS 对象信息、状态、耗时       |
| `knowledge_chunk`        | `ingestion` / `knowledge` | 分块内容、启用状态、token、字符数、向量 ID     |
| `knowledge_chunk_vector` | Spring AI PGVector        | 向量存储表                         |

## 常见改动入口

| 要改的内容 | 优先看这里 |
| --- | --- |
| 登录、注册、角色 | `auth/`、`auth_user` |
| 普通聊天、流式响应 | `chat/service/ChatService`、`chat/controller/ChatController` |
| token 和响应时间统计 | `chat/service/ChatService`、`chat_message`、`admin/service/AdminDashboardService` |
| 后台权限 | `admin/AdminGuard` |
| Dashboard 指标 | `admin/service/AdminDashboardService` |
| 知识库管理 | `knowledge/service/KnowledgeAdminService` |
| 上传到 RustFS | `ingestion/source/DocumentSourceReader`、`storage/service/ObjectStorageService` |
| 文档解析 | `ingestion/parser/TikaDocumentParser` |
| 文本清洗 | `ingestion/cleaner/DocumentTextCleaner` |
| 分块策略 | `ingestion/model/ChunkingOptions`、`ingestion/model/ChunkingStrategy`、`ingestion/splitter/RecursiveDocumentChunkSplitter` |
| RocketMQ 任务 | `ingestion/queue/*`、`knowledge/service/KnowledgeAdminService` |
| pgvector 写入和删除 | `ingestion/service/DocumentIngestionService`、`RagVectorStoreConfig` |
| 数据库结构变更 | `backend/src/main/resources/db/migration` |
