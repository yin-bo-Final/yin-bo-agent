# 音波 AI Agent 智能助手平台

这是一个围绕 Java 后端、Spring AI、Agent、RAG、MCP 和工程化实践持续演进的前后端分离项目。当前核心目标不是做一个单纯的聊天页面，而是把“会话、用户权限、知识库、文档入库流水线、向量检索、工具调用”这些 Agent/RAG 系统必备能力逐步落到真实工程里。

当前主线已经进入知识库和 ingestion 阶段：管理员上传文档后，后端先创建 `UPLOADING` 文档记录，再把原始文件保存到 RustFS，上传完成后变为 `UPLOADED`；管理员再点击“分块”或“重新分块”，后端通过 RocketMQ 异步消费任务，完成 Tika 解析、文本清洗、分块、向量化，并把向量写入 PostgreSQL pgvector。

## 技术栈

| 分层     | 技术                                                                 |
| ------ | ------------------------------------------------------------------ |
| 网关     | Spring Cloud Gateway Server WebFlux、Actuator                       |
| 后端业务服务 | Java 17、Spring Boot 3.5.9、Maven                                    |
| Web    | Spring Web、Validation、Actuator                                     |
| 数据库    | PostgreSQL、pgvector、Flyway、MyBatis-Plus、Spring JDBC                |
| 登录态    | Session、Spring Session Data Redis、Redis、BCrypt                     |
| AI     | 独立 ai-infra 服务、ai-api 契约、OpenAI 兼容 HTTP 调用、模型路由、熔断和故障转移 |
| RAG    | Apache Tika、pgvector、Qwen3 Embedding / Reranker 配置 |
| 异步     | RocketMQ Spring Boot Starter                                       |
| 文件存储   | RustFS，使用 MinIO Java SDK 访问 S3 兼容接口                                |
| 前端     | Vue 3、Vite、marked、DOMPurify                                        |
| 部署     | WSL Docker 中间件、前端 Docker + Nginx、Gateway、后端 Spring Boot            |

## 架构概览

```text
Vue 3 前端
  -> Spring Cloud Gateway 网关
    -> Spring Boot 业务服务
      -> PostgreSQL 保存业务表
      -> pgvector 保存知识库向量
      -> Redis 保存 Session 登录态、gateway 上传并发信号量和 service 兜底信号量
      -> RustFS 保存上传原始文件
      -> RocketMQ 承载异步 ingestion 任务
      -> HTTP 调用 ai-infra 路由 Chat / Embedding / Rerank 模型
      -> Apache Tika 解析 PDF / Word / Markdown / TXT
```

RAG 文档入库链路：

```text
上传文件 / 提交 URL
-> RustFS 保存原始文件
-> knowledge_document.status = UPLOADING -> UPLOADED
-> 管理员点击分块
-> RocketMQ 发送 CHUNK 事务半消息
-> 本地事务 CAS 把文档改为 PROCESSING，并创建 ingestion_task
-> Consumer 读取 RustFS 原始文件
-> Tika 解析纯文本
-> 文本清洗
-> AUTO / RECURSIVE / NONE 分块
-> 分块优化和长度校验
-> 事务外生成 Embedding
-> 短事务内写入 pgvector、knowledge_chunk 并更新文档状态
-> knowledge_document.status = COMPLETED / FAILED
```

重建向量链路：

```text
管理员点击重建向量
-> RocketMQ 发送 REBUILD_VECTORS 事务半消息
-> 本地事务 CAS 把文档改为 PROCESSING，并创建 ingestion_task
-> Consumer 读取已有 knowledge_chunk
-> 事务外重新生成向量
-> 短事务内写入新向量、更新 vectorDocumentId 并删除旧向量
-> 文档状态回到 COMPLETED / FAILED
```

## 当前能力

### 用户和权限

- 注册、登录、退出登录、注销账号
- `ADMIN` / `USER` 两类角色
- 普通用户使用 AI 会话
- 管理员可以进入后台管理知识库和查看 Dashboard
- 密码使用 BCrypt 哈希
- 登录态由 Session + Redis 承载
- 注销账号使用逻辑删除，注销后用户名可以重新注册

### AI 对话

- 前端 Chat 模型选择
- Chat / Embedding / Rerank 模型由 ai-infra 的 `app.ai` 配置驱动，支持供应商、候选模型、优先级、熔断和故障转移
- 普通响应和 SSE 流式响应
- 会话生成已拆成 `ConversationFlowExecutor` 编排器和 `chat/flow` 分层阶段服务，查询改写已接入“术语统一 + LLM 改写/问题拆分 + 容错解析 + 降级记录”，意图识别、歧义引导、RAG 检索和工具调用继续作为后续扩展点
- 查询改写前会先使用术语表进行关键词映射，术语表由 PostgreSQL 维护并通过 Redis 旁路缓存整份启用快照；管理员可在后台维护关键词映射并开关 LLM 语义改写
- 会话记忆支持自动上下文压缩和手动压缩，Prompt 使用“头部原文 + 历史摘要 + 最近窗口原文”，原始消息仍完整保存在 `chat_message`
- 前端输入框显示上下文 token 使用圆环，并提供手动压缩按钮；压缩中消息列表显示分割线且禁止继续发送，接近 90% 上下文时会展示自动压缩提示，最终以服务端返回的摘要水位线为准
- 会话列表、搜索、置顶、取消置顶、删除
- 刷新后通过 `/c/{conversationId}` 恢复会话
- assistant 消息记录响应耗时和 token 消耗
- Markdown 渲染前经过 DOMPurify 清洗

### 后台管理

- 会话页头像菜单中管理员可进入“后台管理”
- Dashboard 展示活跃用户、消息数、会话数、流量数、平均响应时间
- Dashboard 趋势分析支持消息、会话、响应时间和活跃用户趋势，并可切换 `24小时` / `本月`
- 知识库支持新建、编辑、删除
- 文档支持上传、URL 录入、分块、重新分块、重建向量、详情、删除
- 分块支持查看、编辑、删除、启用、禁用、批量启用、批量禁用
- 失败入库任务支持查看失败原因、重试次数和手动重试
- 查询预处理后台支持关键词映射管理和 Pipeline 配置，可关闭 LLM 语义改写并保留术语统一兜底
- 后台导航栏支持折叠，整体样式遵循项目自己的灰色工程风格

后台路由：

```text
/admin
/admin/knowledge
/admin/knowledge/{knowledgeBaseId}
/admin/knowledge/{knowledgeBaseId}/docs/{documentId}
/admin/tasks/failed
/admin/mappings
/admin/pipeline
```

### Ingestion 流水线

- 上传阶段只负责 RustFS 落盘和文档元数据保存，文件上传中状态为 `UPLOADING`
- 上传阶段先由 gateway 通过 Redis 信号量限制全系统同时上传任务数，service 层保留兜底信号量
- 分块和向量化通过 RocketMQ 异步执行
- RocketMQ 消费者通过 Redis 信号量限制全系统同时处理的分块 / 向量化任务数
- 支持状态：`UPLOADING`、`UPLOADED`、`PROCESSING`、`COMPLETED`、`FAILED`
- 支持分块策略：`AUTO`、`RECURSIVE`、`NONE`
- 自动策略会根据文本长度调整切块参数
- 分块过大时返回业务错误，避免把模型上下文错误裸露给前端
- 文档详情记录文本提取、分块、向量化、其他耗时和总耗时
- 原始文件在 RustFS，分块元数据在 `knowledge_chunk`，向量在 `knowledge_chunk_vector`
- 分块和向量重建任务会写入 `ingestion_task`，用于后台展示失败任务和手动重试
- 消费者最大重试次数和任务表 `maxRetries` 对齐，耗尽后进入 `DEAD` 并等待 RocketMQ 死信队列处理
- `UPLOADING` / `PROCESSING` 文档禁止删除和修改分块，避免和异步入库事务互相覆盖

## 项目结构

```text
SpringAI-Program/
├─ ai-api/                          # backend 和 ai-infra 共享的 HTTP 契约
│  └─ src/main/java/com/yinbo/ai/api/
├─ ai-infra/                        # 独立 AI 基础设施服务
│  ├─ pom.xml
│  └─ src/main/
│     ├─ java/com/yinbo/ai/infra/   # 模型路由、熔断和供应商客户端
│     └─ resources/application.yml
├─ backend/                         # Spring Boot 后端模块
│  ├─ pom.xml
│  └─ src/main/
│     ├─ java/com/yinbo/agent/
│     │  ├─ admin/                  # 后台 Dashboard 和管理员校验
│     │  ├─ auth/                   # 登录、注册、Session、角色
│     │  ├─ chat/                   # 聊天、会话、消息统计和会话处理流水线
│     │  ├─ common/                 # 业务异常和统一错误响应
│     │  ├─ config/                 # Web、RAG、ai-infra、对象存储配置
│     │  ├─ ingestion/              # 文档 ETL 和 RocketMQ 消费
│     │  ├─ infra/                  # ai-infra 等远程基础设施客户端
│     │  ├─ knowledge/              # 知识库后台管理
│     │  ├─ storage/                # RustFS / S3 对象存储封装
│     │  └─ YinboAgentServiceApplication.java
│     └─ resources/
│        ├─ application.yml
│        └─ db/migration/
│           ├─ V1__init_schema.sql
│           ├─ V2__create_ingestion_task.sql
│           ├─ V3__add_chat_message_created_index.sql
│           ├─ V4__add_dashboard_trend_indexes.sql
│           └─ V5__create_conversation_memory_summary.sql
├─ gateway/                         # Spring Cloud Gateway 网关模块
│  ├─ pom.xml
│  └─ src/main/
│     ├─ java/com/yinbo/gateway/
│     │  ├─ concurrent/              # Redis 分布式信号量
│     │  ├─ config/                  # 网关配置 Bean 和配置属性
│     │  ├─ filter/                  # 全局过滤器
│     │  ├─ ip/                      # 可信代理和真实 IP 解析
│     │  ├─ rate/                    # 限流身份解析
│     │  ├─ response/                # 统一 gateway 错误响应
│     │  └─ YinboAgentGatewayApplication.java
│     └─ resources/
│        └─ application.yml
├─ frontend/                        # Vue 3 前端
│  ├─ src/
│  │  ├─ api/                       # 请求封装
│  │  ├─ pages/                     # 登录页、会话页、后台页
│  │  ├─ App.vue
│  │  ├─ main.js
│  │  └─ styles.css
│  ├─ public/
│  ├─ nginx/default.conf
│  └─ vite.config.js
├─ project-structure.md             # 项目结构总览和文档导航
├─ codex.md                         # 给 AI / Codex 阅读的协作规则和提示词
├─ docs/
│  ├─ gateway-structure.md          # 网关模块边界和路由
│  ├─ ai-infra-structure.md         # AI 基础设施服务和 HTTP 契约
│  ├─ backend-structure.md          # 后端模块边界
│  ├─ rag-conversation-pipeline-flow.md # RAG 会话流水线和记忆压缩流程
│  ├─ frontend-structure.md         # 前端模块边界
│  └─ frontend-style-guide.md       # 前端样式约定
├─ local-secrets.example.yml        # 本地私密配置模板
├─ local-secrets.yml                # 本地私密配置，不提交
└─ pom.xml                          # Maven 聚合工程
```

整体结构导航见 [project-structure.md](project-structure.md)。
AI / Codex 协作规则见 [codex.md](codex.md)。
网关模块说明见 [docs/gateway-structure.md](docs/gateway-structure.md)，
AI 基础设施说明见 [docs/ai-infra-structure.md](docs/ai-infra-structure.md)，
后端模块说明见 [docs/backend-structure.md](docs/backend-structure.md)，
RAG 会话流水线和记忆压缩流程见 [docs/rag-conversation-pipeline-flow.md](docs/rag-conversation-pipeline-flow.md)，
前端模块说明见 [docs/frontend-structure.md](docs/frontend-structure.md)。
前端 UI 风格和交互约定见 [docs/frontend-style-guide.md](docs/frontend-style-guide.md)。

## 本地配置

`ai-infra`、`backend` 和 `gateway` 的 `application.yml` 都会加载根目录或模块上级目录的 `local-secrets.yml`：

```yml
spring:
  config:
    import: optional:file:./local-secrets.yml,optional:file:../local-secrets.yml
```

本地开发时复制 [local-secrets.example.yml](local-secrets.example.yml) 为 `local-secrets.yml`，再填入自己的真实配置。至少需要：

```yml
POSTGRES_USERNAME: your-postgres-username
POSTGRES_PASSWORD: your-postgres-password
REDIS_PASSWORD: your-redis-password
OPENAI_API_KEY: your-siliconflow-api-key
AUTH_SEED_ADMIN_USERNAME: admin
AUTH_SEED_ADMIN_PASSWORD: replace-with-a-dev-only-password
RUSTFS_ACCESS_KEY: rustfsadmin
RUSTFS_SECRET_KEY: rustfsadmin
```

常用可选配置：

```yml
POSTGRES_URL: jdbc:postgresql://localhost:5432/yinbo_agent
REDIS_HOST: localhost
REDIS_PORT: 6379
OPENAI_BASE_URL: https://api.siliconflow.cn
AI_CHAT_DEFAULT_MODEL: deepseek-ai/DeepSeek-V4-Flash
AI_EMBEDDING_DEFAULT_MODEL: qwen-emb-8b
OPENAI_EMBEDDING_MODEL: Qwen/Qwen3-Embedding-8B
RAG_EMBEDDING_DIMENSIONS: 1024
RAG_VECTOR_INDEX_TYPE: HNSW
RAG_INGESTION_TOPIC: rag-ingestion-task
ROCKETMQ_NAME_SERVER: localhost:9876
RUSTFS_ENDPOINT: http://localhost:9000
RUSTFS_BUCKET: yinbo-agent-documents
YINBO_AGENT_SERVICE_URI: http://localhost:8080
YINBO_AI_INFRA_URI: http://localhost:8082
AI_INFRA_REQUEST_TIMEOUT: 5m
GATEWAY_INTERNAL_TOKEN: replace-with-a-dev-internal-token
APP_SLOW_REQUEST_THRESHOLD_MS: 3000
CHAT_MEMORY_CONTEXT_MAX_TOKENS: 100000
CHAT_MEMORY_OUTPUT_RESERVE_TOKENS: 8000
CHAT_MEMORY_RAG_RESERVE_TOKENS: 12000
CHAT_MEMORY_TOOL_RESERVE_TOKENS: 4000
CHAT_MEMORY_SAFETY_MARGIN_TOKENS: 4000
CHAT_MEMORY_RECENT_WINDOW_TOKENS: 20000
CHAT_MEMORY_HEAD_MESSAGE_COUNT: 4
CHAT_MEMORY_MIN_COMPRESS_MESSAGE_COUNT: 8
CHAT_MEMORY_COMPRESSION_WINDOW_TOKENS: 24000
CHAT_MEMORY_MAX_SUMMARY_TOKENS: 4000
CHAT_MEMORY_AUTO_COMPRESS_THRESHOLD_RATIO: 0.9
CHAT_MEMORY_COMPRESSION_VERSION: v1
CHAT_QUERY_REWRITE_TERMINOLOGY_ENABLED: true
CHAT_QUERY_REWRITE_LLM_ENABLED: true
CHAT_QUERY_REWRITE_RULE_SPLIT_ENABLED: true
CHAT_QUERY_REWRITE_FALLBACK_POLICY: TERM_ONLY
CHAT_QUERY_REWRITE_TIMEOUT_MS: 3000
CHAT_QUERY_REWRITE_CONTEXT_TURNS: 3
CHAT_TERMINOLOGY_CACHE_TTL: 60m
CHAT_PIPELINE_CONFIG_CACHE_TTL: 10m
UPLOAD_GATEWAY_RATE_REPLENISH: 20
UPLOAD_GATEWAY_RATE_BURST: 240
UPLOAD_GATEWAY_RATE_REQUESTED: 60
URL_INGESTION_GATEWAY_RATE_REPLENISH: 12
URL_INGESTION_GATEWAY_RATE_BURST: 120
URL_INGESTION_GATEWAY_RATE_REQUESTED: 60
AI_STREAM_GATEWAY_RATE_REPLENISH: 60
AI_STREAM_GATEWAY_RATE_BURST: 300
AI_STREAM_GATEWAY_RATE_REQUESTED: 60
AI_CHAT_GATEWAY_RATE_REPLENISH: 60
AI_CHAT_GATEWAY_RATE_BURST: 300
AI_CHAT_GATEWAY_RATE_REQUESTED: 60
AUTH_GATEWAY_RATE_REPLENISH: 30
AUTH_GATEWAY_RATE_BURST: 180
AUTH_GATEWAY_RATE_REQUESTED: 60
UPLOAD_GATEWAY_MAX_CONCURRENCY: 10
UPLOAD_GATEWAY_CONCURRENCY_LEASE_TTL: 10m
URL_INGESTION_GATEWAY_MAX_CONCURRENCY: 5
URL_INGESTION_GATEWAY_CONCURRENCY_LEASE_TTL: 10m
AI_CHAT_GATEWAY_MAX_CONCURRENCY: 20
AI_CHAT_GATEWAY_CONCURRENCY_LEASE_TTL: 5m
UPLOAD_MAX_CONCURRENCY: 10
UPLOAD_CONCURRENCY_LEASE_TTL: 10m
INGESTION_MAX_CONCURRENCY: 5
INGESTION_CONCURRENCY_LEASE_TTL: 30m
UPLOAD_GATEWAY_MAX_BODY_SIZE: 200MB
INGESTION_MAX_FILE_SIZE: 200MB
INGESTION_MAX_REQUEST_SIZE: 220MB
RAG_MAX_SOURCE_BYTES: 209715200
```

`local-secrets.yml` 不提交。账号、密码、API Key 都不要写进 `application.yml`。

## 本地中间件

本地默认假设 PostgreSQL、Redis、RocketMQ、RustFS 部署在 WSL Docker 中，并把端口映射到 Windows：

| 服务 | 默认地址 |
| --- | --- |
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| RocketMQ NameServer | `localhost:9876` |
| RocketMQ Dashboard | [http://localhost:18082/](http://localhost:18082/) |
| RustFS S3 Endpoint | `http://localhost:9000` |
| RustFS Dashboard | [http://localhost:9001/rustfs/console/index.html](http://localhost:9001/rustfs/console/index.html) |

RustFS Dashboard 使用 `local-secrets.yml` 中的 `RUSTFS_ACCESS_KEY` 和 `RUSTFS_SECRET_KEY` 登录。

PostgreSQL 需要安装 pgvector 扩展。应用启动时由 Flyway 执行 `CREATE EXTENSION IF NOT EXISTS vector` 和表结构迁移。当前 Embedding 默认降维到 `1024`，可以继续使用 HNSW 索引；如果改成大于 2000 维，pgvector 的 HNSW 索引会报维度限制。向量维度已经写入 Flyway 初始迁移，后续如果要调整维度，需要新增迁移并重建向量表数据。

## 启动方式

### AI 基础设施服务

```powershell
cd ai-infra
mvn spring-boot:run
```

AI 基础设施服务默认地址：

```text
http://localhost:8082
```

### 后端业务服务

```powershell
cd backend
mvn spring-boot:run
```

如果本机默认 Java 不是 17：

```powershell
$env:JAVA_HOME="C:\Users\35575\.jdks\ms-17.0.17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd ai-infra
mvn spring-boot:run
cd ../backend
mvn spring-boot:run
```

后端业务服务默认地址：

```text
http://localhost:8080
```

### 网关

```powershell
cd gateway
mvn spring-boot:run
```

网关默认地址：

```text
http://localhost:8081
```

网关默认把 `/api/**` 转发到 `YINBO_AGENT_SERVICE_URI`，本地默认是 `http://localhost:8080`；`/internal/ai/**` 转发到 `YINBO_AI_INFRA_URI`，本地默认是 `http://localhost:8082`，并要求请求携带匹配 `GATEWAY_INTERNAL_TOKEN` 的 `X-Internal-Token`。backend 自身默认通过 `YINBO_AI_INFRA_URI` 直接调用 ai-infra。

### 前端

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

Vite 会把 `/api` 代理到 `http://localhost:8081`，由网关再转发给后端业务服务。

## 主要接口

### 认证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/register` | 注册并自动登录 |
| `POST` | `/api/auth/login` | 登录 |
| `GET` | `/api/auth/me` | 获取当前用户 |
| `POST` | `/api/auth/logout` | 退出登录 |
| `POST` | `/api/auth/cancel` | 注销账号 |

### 聊天和会话

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/models` | 查询模型列表 |
| `POST` | `/api/chat` | 普通聊天 |
| `POST` | `/api/chat/stream` | SSE 流式聊天 |
| `GET` | `/api/conversations` | 查询会话列表 |
| `GET` | `/api/conversations/{conversationId}` | 查询会话详情 |
| `POST` | `/api/conversations/{conversationId}/memory/compress` | 手动压缩会话记忆 |
| `POST/PATCH` | `/api/conversations/{conversationId}/pin` | 置顶或更新置顶 |
| `POST` | `/api/conversations/{conversationId}/unpin` | 取消置顶 |
| `DELETE` | `/api/conversations/{conversationId}` | 删除会话 |

### 后台知识库

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/dashboard` | Dashboard 指标 |
| `GET` | `/api/admin/knowledge/overview` | 知识库概览 |
| `GET` | `/api/admin/knowledge/bases` | 知识库列表 |
| `POST` | `/api/admin/knowledge/bases` | 新建知识库 |
| `PATCH` | `/api/admin/knowledge/bases/{knowledgeBaseId}` | 修改知识库 |
| `DELETE` | `/api/admin/knowledge/bases/{knowledgeBaseId}` | 删除知识库 |
| `POST` | `/api/admin/knowledge/bases/{knowledgeBaseId}/documents/upload` | 上传文档到 RustFS |
| `POST` | `/api/admin/knowledge/bases/{knowledgeBaseId}/documents/url` | URL 文档录入 |
| `POST` | `/api/admin/knowledge/documents/{documentId}/rechunk` | 投递分块 / 重新分块任务 |
| `POST` | `/api/admin/knowledge/documents/{documentId}/vectors/rebuild` | 投递重建向量任务 |
| `GET` | `/api/admin/knowledge/documents/{documentId}/chunks` | 查询分块 |
| `PATCH` | `/api/admin/knowledge/chunks/{chunkId}` | 修改分块内容 |
| `PATCH` | `/api/admin/knowledge/chunks/{chunkId}/enabled` | 启用或禁用分块 |
| `DELETE` | `/api/admin/knowledge/chunks/{chunkId}` | 删除分块 |
| `GET` | `/api/admin/ingestion/tasks/failed` | 查询失败入库任务 |
| `POST` | `/api/admin/ingestion/tasks/{taskId}/retry` | 手动重试失败入库任务 |
| `DELETE` | `/api/admin/ingestion/tasks/{taskId}` | 删除失败入库任务 |
| `GET` | `/api/admin/query/terminology/mappings` | 查询关键词映射 |
| `POST` | `/api/admin/query/terminology/mappings` | 新增关键词映射 |
| `PATCH` | `/api/admin/query/terminology/mappings/{aliasId}` | 修改关键词映射 |
| `PATCH` | `/api/admin/query/terminology/mappings/{aliasId}/enabled` | 启用或禁用关键词映射 |
| `DELETE` | `/api/admin/query/terminology/mappings/{aliasId}` | 删除关键词映射 |
| `GET` | `/api/admin/query/pipeline/config` | 查询查询预处理 Pipeline 配置 |
| `PATCH` | `/api/admin/query/pipeline/config` | 更新查询预处理 Pipeline 配置 |

## 数据表

当前使用 Flyway 管理数据库结构，迁移脚本位于 [backend/src/main/resources/db/migration](backend/src/main/resources/db/migration)。`V1__init_schema.sql` 负责初始化业务表、pgvector 扩展、向量表和 HNSW 索引，后续 `V2` 到 `V5` 继续补充入库任务表、Dashboard 趋势查询索引和会话记忆摘要表。

为了兼容已经存在的本地数据库，`application.yml` 开启了 `spring.flyway.baseline-on-migrate=true`，并把 `baseline-version` 设置为 `0`。这样老库首次切换到 Flyway 时会先建立 `flyway_schema_history`，再执行 `V1` 中的幂等 DDL；新库则会直接从 `V1` 开始迁移。

| 表 | 说明 |
| --- | --- |
| `auth_user` | 用户、密码哈希、角色、状态 |
| `chat_conversation` | 会话信息、置顶时间、最近消息时间 |
| `chat_message` | 消息内容、模型、响应耗时、token 统计 |
| `conversation_memory_summary` | 会话记忆压缩摘要、水位线、压缩模型和触发方式 |
| `chat_terminology_term` | 查询预处理标准术语 |
| `chat_terminology_alias` | 查询预处理术语别名和关键词映射 |
| `chat_query_rewrite_record` | 语义改写、子问题拆分和降级记录 |
| `chat_pipeline_config` | 查询预处理 Pipeline 开关和降级策略 |
| `knowledge_base` | 知识库、Embedding 模型、collection |
| `knowledge_document` | 文档元数据、RustFS 对象信息、状态、耗时 |
| `knowledge_chunk` | 分块内容、启用状态、token 数、字符数、向量文档 ID |
| `knowledge_chunk_vector` | pgvector 向量表，由 ingestion 事务直接写入 |
| `ingestion_task` | 文档分块 / 重建向量任务状态、重试次数、失败原因和 MQ messageId |

## 开发约定

- 后台接口统一走 `/api/admin/**`，并通过 `AdminGuard` 校验管理员。
- 前端请求统一进入 gateway，gateway 负责 `/api/**` 转发、CORS、可信代理真实 IP 解析、`X-Request-Id`、高成本接口频率限流、资源并发限流和统一 gateway 错误响应；登录态和业务权限仍由后端业务服务校验。
- 频率限流基于 Spring Cloud Gateway `RedisRateLimiter`，当前覆盖上传、URL 入库、AI 对话、登录注册；未登录时按 IP 限流，登录后按 `userId` 限流，触发后返回统一 `429` JSON。
- 上传并发限流第一道防线在 gateway，默认全局最多 `10` 个上传请求同时转发；URL 入库默认最多 `5` 个同时转发，AI 对话默认最多 `20` 个同时转发；业务服务保留上传信号量做兜底保护，RocketMQ ingestion 默认全局最多 `5` 个同时处理。
- gateway 会生成或透传 `X-Request-Id`，并写入当前启动工作目录下的 `.logs/gateway.log`；后端业务服务会把同一个 requestId 写入 `.logs/service.log`。
- gateway 和 service 日志使用统一 key-value 风格；超过 `APP_SLOW_REQUEST_THRESHOLD_MS` 的请求会以 `WARN` 记录，默认阈值 `3000ms`。
- 日志按“日期 + 大小”滚动：单文件最大 `20MB`，保留 `14` 天，总日志体积上限 `1GB`，历史文件会压缩为 `.gz`。
- Actuator 默认只启用并暴露 `health` 和 `info`，不暴露 gateway 路由、env、configprops 等内部信息。
- 关键业务日志使用 `event=...`：登录注册、知识库变更、文档上传、AI 调用、RocketMQ 投递消费、ingestion 完成或失败都会有明确事件。
- 模型调用不要散落在业务 Service 中，backend 只通过 `AiInfraClient` 调 ai-infra；HTTP 契约放在 `ai-api`，模型供应商实现只放在 `ai-infra`。
- 会话编排不要继续堆进 `ChatService`，新增查询改写、意图识别、歧义引导、RAG 检索或工具调用时优先扩展 `chat/flow` 下对应子包服务，并通过 `ChatExecutionContext` 传递阶段结果。
- 查询改写结果写入 `ctx.rewriteResult`，只作为当前流水线中间产物，不写入 `chat_message`；如需评估和回放，写入 `chat_query_rewrite_record`。
- RAG 会话流水线和记忆压缩流程见 [docs/rag-conversation-pipeline-flow.md](docs/rag-conversation-pipeline-flow.md)，压缩只写 `conversation_memory_summary`，不要删除或覆盖 `chat_message` 原始消息。
- 前端请求错误依赖后端返回的 `message` 字段，所以业务错误优先抛 `BusinessException`。
- 数据库结构变更必须新增 Flyway 迁移脚本。
- 上传文件大小默认限制为单文件 `200MB`；gateway 会先拦截超过 `200MB` 的上传请求，service multipart 和前端 Nginx 单请求默认上限为 `220MB`。
- 原始文件只进 RustFS，不把大文件二进制塞进 PostgreSQL。
- 分块文本改动后必须重建向量，否则 pgvector 中仍是旧文本语义。
- RocketMQ 当前负责异步分块和异步重建向量；消费者拿不到 Redis 信号量或遇到可重试失败时抛异常交给 RocketMQ 重试，耗尽后进入失败任务后台和死信处理。
- 前端后台 UI 继续沿用当前灰色工程风格，改样式前先看 [docs/frontend-style-guide.md](docs/frontend-style-guide.md)。

## 下一步

1. 增加 RAG 检索接口：pgvector 召回 + Qwen3 Reranker 重排。
2. 给知识库和分块检索补权限过滤。
3. 给 ingestion 核心链路补单元测试和 RocketMQ 集成测试。
4. 将后台页面组件化，拆出知识库表格、文档表格、分块表格和通用弹窗。
5. 增加 chunk/vector 一致性巡检和修复工具。
