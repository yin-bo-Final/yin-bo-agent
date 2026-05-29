# 项目结构说明

这份文档给新对话和后续开发接手项目用。README 讲“项目是什么、怎么启动”，这里讲“代码放在哪里、模块边界是什么、改功能先看哪里”。

## 总览

```text
SpringAI-Program/
├─ backend/                         # Spring Boot 后端
├─ frontend/                        # Vue 3 前端
├─ docs/                            # 项目文档
├─ local-secrets.example.yml        # 本地私密配置模板
├─ local-secrets.yml                # 本地私密配置，不提交
├─ pom.xml                          # Maven 聚合工程，目前聚合 backend
└─ README.md                        # 项目入口文档
```

当前工程采用“前端单页 + 后端模块化包 + 中间件外置”的结构。后端先按业务边界拆包，等功能继续变大后，再考虑拆 Maven 多模块，例如 `auth`、`chat`、`rag`、`knowledge`、`ingestion`、`mcp`。

## 后端

```text
backend/
├─ pom.xml
└─ src/main/
   ├─ java/com/yinbo/agent/
   │  ├─ YinboAgentApplication.java
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
      └─ schema.sql
```

### 启动类

| 文件 | 说明 |
| --- | --- |
| `YinboAgentApplication` | Spring Boot 启动入口，启用配置属性和 RocketMQ 消费 |

### `admin/`

后台管理公共能力。

| 文件或目录                        | 说明                        |
| ---------------------------- | ------------------------- |
| `AdminGuard`                 | 管理员权限门卫，后台接口需要先调用         |
| `AdminDashboardController`   | `/api/admin/dashboard`    |
| `AdminDashboardService`      | 活跃用户、消息数、会话数、流量数、平均响应时间统计 |
| `dto/AdminDashboardResponse` | Dashboard 响应结构            |

约定：后台新增模块时，优先复用 `AdminGuard.requireAdmin()`，不要在 Controller 里散落角色判断。

### `auth/`

认证、登录态和用户角色。

```text
auth/
├─ AuthController.java
├─ AuthService.java
├─ SessionAuthService.java
├─ LoginInterceptor.java
├─ AuthBootstrapRunner.java
├─ AuthConstants.java
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

相关表：

```text
auth_user
```

### `chat/`

AI 对话、会话和消息统计。

```text
chat/
├─ ChatController.java
├─ ChatService.java
├─ ChatMessageCacheService.java
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

相关表：

```text
chat_conversation
chat_message
```

### `common/`

统一错误响应。

| 文件 | 说明 |
| --- | --- |
| `BusinessException` | 带 HTTP 状态码的业务异常 |
| `ApiErrorResponse` | 返回给前端的错误结构 |
| `GlobalExceptionHandler` | 全局异常处理 |

约定：能预期的业务失败抛 `BusinessException`，前端会读取 `message` 并显示在对应弹窗或页面里。

### `config/`

Spring 配置和 `@ConfigurationProperties`。

| 文件                          | 说明                            |
| --------------------------- | ----------------------------- |
| `AiModelProperties`         | `app.ai.models` 模型列表          |
| `AuthProperties`            | 本地种子管理员配置                     |
| `RagProperties`             | RAG 模型、维度、切块参数、RocketMQ topic |
| `ObjectStorageProperties`   | RustFS / S3 对象存储配置            |
| `RagVectorStoreConfig`      | Spring AI PGVector Store 配置   |
| `MybatisPlusAutoFillConfig` | 自动填充创建时间和更新时间                 |
| `PasswordConfig`            | BCrypt 密码编码器                  |
| `WebConfig`                 | CORS、登录拦截器和静态 Web 配置          |

重要配置前缀：

```text
spring.datasource
spring.data.redis
spring.session.redis
spring.ai.openai
rocketmq
app.ai.models
app.ai.rag
app.auth
app.storage
```

### `storage/`

对象存储封装。当前底层是 RustFS，Java SDK 使用 MinIO Client，因为 RustFS 兼容 S3 协议。

```text
storage/
├─ ObjectStorageService.java
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

### `ingestion/`

RAG 文档 ETL 核心模块。它把“原始文档”加工成“可检索向量”。

```text
ingestion/
├─ DocumentIngestionService.java
├─ IngestionController.java
├─ RawDocument.java
├─ ParsedDocument.java
├─ DocumentChunk.java
├─ ChunkingOptions.java
├─ ChunkingStrategy.java
├─ DocumentSourceType.java
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

分块阶段：

```text
KnowledgeAdminService.rechunkDocument
-> RocketMQ 发送 CHUNK 消息
-> DocumentIngestionTaskConsumer
-> DocumentIngestionService.processDocument
-> TikaDocumentParser 读取 RustFS 并解析
-> DocumentTextCleaner 清洗文本
-> ChunkingOptions 根据文本长度适配参数
-> RecursiveDocumentChunkSplitter 分块
-> DocumentChunkOptimizer 后处理
-> VectorStore.add 写入 pgvector
-> knowledge_chunk 写入分块元数据
-> status = COMPLETED / FAILED
```

重建向量阶段：

```text
KnowledgeAdminService.rebuildDocumentVectors
-> RocketMQ 发送 REBUILD_VECTORS 消息
-> DocumentIngestionTaskConsumer
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

### `knowledge/`

后台知识库管理接口。

```text
knowledge/
├─ KnowledgeAdminController.java
├─ KnowledgeAdminService.java
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

### `resources/`

| 文件 | 说明 |
| --- | --- |
| `application.yml` | 服务端口、数据源、Redis、Session、AI、RAG、RocketMQ、RustFS、multipart 限制 |
| `schema.sql` | 幂等初始化表结构、字段和索引 |

当前没有使用 Flyway。后续表结构继续增长时，建议用 Flyway 或 Liquibase 接管迁移。

## 前端

```text
frontend/
├─ package.json
├─ vite.config.js
├─ Dockerfile
├─ index.html
├─ nginx/
│  └─ default.conf
├─ public/
│  ├─ yinbo-logo.png
│  └─ yinbo-logo.svg
└─ src/
   ├─ App.vue
   ├─ main.js
   ├─ styles.css
   ├─ api/
   │  ├─ adminApi.js
   │  ├─ authApi.js
   │  └─ chatApi.js
   └─ pages/
      ├─ AdminPage.vue
      ├─ AuthPage.vue
      └─ ConversationPage.vue
```

### `App.vue`

顶层状态分发。

- 启动时请求 `/api/auth/me`
- 判断当前路径是认证页、会话页还是后台页
- 非管理员访问 `/admin` 时回到会话页
- 维护当前用户状态

### `pages/AuthPage.vue`

登录和注册页。

- 登录表单
- 注册表单
- 表单错误展示
- 成功后通知 `App.vue`

### `pages/ConversationPage.vue`

会话主界面。

- 模型选择
- 新建会话
- 普通聊天和流式聊天
- 会话列表、搜索、回放
- 会话置顶、取消置顶、删除
- 用户菜单
- 管理员显示“后台管理”入口
- 侧边栏展开和折叠

### `pages/AdminPage.vue`

后台管理页，目前包含 Dashboard 和知识库管理。

- 后台侧边栏和折叠状态
- Dashboard 指标
- 知识库列表、新建、编辑、删除
- 文档列表、上传、URL 录入、分块、重新分块、重建向量、详情、删除
- 分块列表、查看、编辑、删除、启用、禁用、批量操作
- 自定义弹窗、下拉栏、tooltip
- 根据 `/admin/knowledge/...` 解析内部视图
- 文档处于 `PROCESSING` 时轮询刷新

### `api/`

| 文件 | 说明 |
| --- | --- |
| `authApi.js` | 注册、登录、当前用户、退出、注销 |
| `chatApi.js` | 模型列表、聊天、流式聊天、会话管理 |
| `adminApi.js` | Dashboard、知识库、文档、分块管理 |

所有需要登录态的请求都带 `credentials: 'include'`。

### `styles.css`

当前前端样式集中在一个文件里。继续改 UI 前先读 [frontend-style-guide.md](frontend-style-guide.md)。

大致分区：

```text
全局字体和背景
认证页
聊天布局
会话侧边栏和折叠 rail
消息列表和 Markdown
模型选择浮层
用户菜单和确认弹窗
后台布局
知识库 / 文档 / 分块表格
通用按钮、弹窗、状态、下拉栏、tooltip
响应式规则
动画 keyframes
```

## 数据表边界

| 表 | 主要模块 | 说明 |
| --- | --- | --- |
| `auth_user` | `auth` | 用户、密码哈希、角色、状态 |
| `chat_conversation` | `chat` | 会话、模型、置顶、最近消息时间 |
| `chat_message` | `chat` | 消息内容、耗时、token |
| `knowledge_base` | `knowledge` | 知识库名称、collection、Embedding 模型 |
| `knowledge_document` | `ingestion` / `knowledge` | 文档元数据、RustFS 对象信息、状态、耗时 |
| `knowledge_chunk` | `ingestion` / `knowledge` | 分块内容、启用状态、token、字符数、向量 ID |
| `knowledge_chunk_vector` | Spring AI PGVector | 向量存储表 |

## 常见改动入口

| 要改的内容 | 优先看这里 |
| --- | --- |
| 登录、注册、角色 | `auth/`、`auth_user` |
| 普通聊天、流式响应 | `chat/ChatService`、`chat/ChatController`、`ConversationPage.vue` |
| token 和响应时间统计 | `chat/ChatService`、`chat_message`、`AdminDashboardService` |
| 后台权限 | `admin/AdminGuard` |
| Dashboard 指标 | `admin/AdminDashboardService`、`AdminPage.vue` |
| 知识库管理 | `knowledge/KnowledgeAdminService`、`AdminPage.vue` |
| 上传到 RustFS | `ingestion/source/DocumentSourceReader`、`storage/ObjectStorageService` |
| 文档解析 | `ingestion/parser/TikaDocumentParser` |
| 文本清洗 | `ingestion/cleaner/DocumentTextCleaner` |
| 分块策略 | `ChunkingOptions`、`ChunkingStrategy`、`RecursiveDocumentChunkSplitter` |
| RocketMQ 任务 | `ingestion/queue/*`、`KnowledgeAdminService` |
| pgvector 写入和删除 | `DocumentIngestionService`、`RagVectorStoreConfig` |
| 后台样式 | `frontend/src/styles.css` 中的 `.kc-*`、`.admin-*` |

## 工程约定

- 后端包名根路径是 `com.yinbo.agent`。
- 后台接口路径统一放在 `/api/admin/**`。
- 业务错误优先抛 `BusinessException`。
- 原始文件进入 RustFS，数据库保存对象定位信息。
- 分块内容和启用状态在 `knowledge_chunk`。
- 向量在 pgvector，由 `vectorDocumentId` 关联业务分块。
- 文档处理状态以 `knowledge_document.status` 为准。
- RocketMQ 当前负责分块和重建向量异步化。
- 前端暂时没有 Vue Router，使用路径解析和 `window.history` 维护页面状态。
- 新增后台 UI 要复用现有按钮、表格、弹窗、下拉栏和 tooltip 风格。
