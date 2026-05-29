# 音波 AI Agent 智能助手平台

这是一个围绕 Java 后端、Spring AI、Agent、RAG、MCP 和工程化实践持续演进的前后端分离项目。当前核心目标不是做一个单纯的聊天页面，而是把“会话、用户权限、知识库、文档入库流水线、向量检索、工具调用”这些 Agent/RAG 系统必备能力逐步落到真实工程里。

当前主线已经进入知识库和 ingestion 阶段：管理员上传文档后，后端先把原始文件保存到 RustFS，对外立即返回 `UPLOADED`；管理员再点击“分块”或“重新分块”，后端通过 RocketMQ 异步消费任务，完成 Tika 解析、文本清洗、分块、向量化，并把向量写入 PostgreSQL pgvector。

## 技术栈

| 分层 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5.9、Maven |
| Web | Spring Web、Validation、Actuator |
| 数据库 | PostgreSQL、pgvector、Flyway、MyBatis-Plus、Spring JDBC |
| 登录态 | Session、Spring Session Data Redis、Redis、BCrypt |
| AI | Spring AI 1.1.6、OpenAI Compatible ChatModel、EmbeddingModel |
| RAG | Apache Tika、Spring AI PGVector Store、Qwen3 Embedding / Reranker 配置 |
| 异步 | RocketMQ Spring Boot Starter |
| 文件存储 | RustFS，使用 MinIO Java SDK 访问 S3 兼容接口 |
| 前端 | Vue 3、Vite、marked、DOMPurify |
| 部署 | WSL Docker 中间件、前端 Docker + Nginx、后端 Spring Boot |

## 架构概览

```text
Vue 3 前端
  -> Spring Boot 后端
    -> PostgreSQL 保存业务表
    -> pgvector 保存知识库向量
    -> Redis 保存 Session 登录态
    -> RustFS 保存上传原始文件
    -> RocketMQ 承载异步 ingestion 任务
    -> Spring AI 调用聊天模型和 Embedding 模型
    -> Apache Tika 解析 PDF / Word / Markdown / TXT
```

RAG 文档入库链路：

```text
上传文件 / 提交 URL
-> RustFS 保存原始文件
-> knowledge_document.status = UPLOADED
-> 管理员点击分块
-> RocketMQ 投递 CHUNK 任务
-> Consumer 读取 RustFS 原始文件
-> Tika 解析纯文本
-> 文本清洗
-> AUTO / RECURSIVE / NONE 分块
-> 分块优化和长度校验
-> Embedding 向量化
-> pgvector 保存向量
-> knowledge_chunk 保存分块元数据
-> knowledge_document.status = COMPLETED / FAILED
```

重建向量链路：

```text
管理员点击重建向量
-> RocketMQ 投递 REBUILD_VECTORS 任务
-> Consumer 读取已有 knowledge_chunk
-> 重新生成向量并更新 vectorDocumentId
-> 事务成功后清理旧向量
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

- 前端模型选择
- 普通响应和 SSE 流式响应
- 会话列表、搜索、置顶、取消置顶、删除
- 刷新后通过 `/c/{conversationId}` 恢复会话
- assistant 消息记录响应耗时和 token 消耗
- Markdown 渲染前经过 DOMPurify 清洗

### 后台管理

- 会话页头像菜单中管理员可进入“后台管理”
- Dashboard 展示活跃用户、消息数、会话数、流量数、平均响应时间
- 知识库支持新建、编辑、删除
- 文档支持上传、URL 录入、分块、重新分块、重建向量、详情、删除
- 分块支持查看、编辑、删除、启用、禁用、批量启用、批量禁用
- 后台导航栏支持折叠，整体样式遵循项目自己的灰色工程风格

后台路由：

```text
/admin
/admin/knowledge
/admin/knowledge/{knowledgeBaseId}
/admin/knowledge/{knowledgeBaseId}/docs/{documentId}
```

### Ingestion 流水线

- 上传阶段只负责 RustFS 落盘和文档元数据保存
- 分块和向量化通过 RocketMQ 异步执行
- 支持状态：`UPLOADED`、`PROCESSING`、`COMPLETED`、`FAILED`
- 支持分块策略：`AUTO`、`RECURSIVE`、`NONE`
- 自动策略会根据文本长度调整切块参数
- 分块过大时返回业务错误，避免把模型上下文错误裸露给前端
- 文档详情记录文本提取、分块、向量化、其他耗时和总耗时
- 原始文件在 RustFS，分块元数据在 `knowledge_chunk`，向量在 `knowledge_chunk_vector`

## 项目结构

```text
SpringAI-Program/
├─ backend/                         # Spring Boot 后端模块
│  ├─ pom.xml
│  └─ src/main/
│     ├─ java/com/yinbo/agent/
│     │  ├─ admin/                  # 后台 Dashboard 和管理员校验
│     │  ├─ auth/                   # 登录、注册、Session、角色
│     │  ├─ chat/                   # 聊天、会话、消息统计
│     │  ├─ common/                 # 业务异常和统一错误响应
│     │  ├─ config/                 # Web、RAG、PGVector、对象存储配置
│     │  ├─ ingestion/              # 文档 ETL 和 RocketMQ 消费
│     │  ├─ knowledge/              # 知识库后台管理
│     │  ├─ storage/                # RustFS / S3 对象存储封装
│     │  └─ YinboAgentApplication.java
│     └─ resources/
│        ├─ application.yml
│        └─ db/migration/
│           └─ V1__init_schema.sql
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
├─ docs/
│  ├─ project-structure.md          # 项目结构总览和文档导航
│  ├─ backend-structure.md          # 后端模块边界
│  ├─ frontend-structure.md         # 前端模块边界
│  ├─ frontend-style-guide.md       # 前端样式约定
│  └─ prompt.md
├─ local-secrets.example.yml        # 本地私密配置模板
├─ local-secrets.yml                # 本地私密配置，不提交
└─ pom.xml                          # Maven 聚合工程
```

整体结构导航见 [docs/project-structure.md](docs/project-structure.md)。后端模块说明见 [docs/backend-structure.md](docs/backend-structure.md)，前端模块说明见 [docs/frontend-structure.md](docs/frontend-structure.md)。前端 UI 风格和交互约定见 [docs/frontend-style-guide.md](docs/frontend-style-guide.md)。

## 本地配置

`backend/src/main/resources/application.yml` 会加载根目录或后端上级目录的 `local-secrets.yml`：

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
OPENAI_CHAT_MODEL: deepseek-ai/DeepSeek-V4-Flash
OPENAI_EMBEDDING_MODEL: Qwen/Qwen3-Embedding-8B
RAG_EMBEDDING_DIMENSIONS: 1024
RAG_VECTOR_INDEX_TYPE: HNSW
RAG_INGESTION_TOPIC: rag-ingestion-task
ROCKETMQ_NAME_SERVER: localhost:9876
RUSTFS_ENDPOINT: http://localhost:9000
RUSTFS_BUCKET: yinbo-agent-documents
INGESTION_MAX_FILE_SIZE: 50MB
INGESTION_MAX_REQUEST_SIZE: 100MB
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

### 后端

```powershell
cd backend
mvn spring-boot:run
```

如果本机默认 Java 不是 17：

```powershell
$env:JAVA_HOME="C:\Users\35575\.jdks\ms-17.0.17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd backend
mvn spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

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

Vite 会把 `/api` 代理到 `http://localhost:8080`。

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

## 数据表

当前使用 Flyway 管理数据库结构，迁移脚本位于 [backend/src/main/resources/db/migration](backend/src/main/resources/db/migration)。`V1__init_schema.sql` 负责初始化业务表、pgvector 扩展、向量表和 HNSW 索引。

为了兼容已经存在的本地数据库，`application.yml` 开启了 `spring.flyway.baseline-on-migrate=true`，并把 `baseline-version` 设置为 `0`。这样老库首次切换到 Flyway 时会先建立 `flyway_schema_history`，再执行 `V1` 中的幂等 DDL；新库则会直接从 `V1` 开始迁移。

| 表 | 说明 |
| --- | --- |
| `auth_user` | 用户、密码哈希、角色、状态 |
| `chat_conversation` | 会话信息、置顶时间、最近消息时间 |
| `chat_message` | 消息内容、模型、响应耗时、token 统计 |
| `knowledge_base` | 知识库、Embedding 模型、collection |
| `knowledge_document` | 文档元数据、RustFS 对象信息、状态、耗时 |
| `knowledge_chunk` | 分块内容、启用状态、token 数、字符数、向量文档 ID |
| `knowledge_chunk_vector` | Spring AI PGVector Store 管理的向量表 |

## 开发约定

- 后台接口统一走 `/api/admin/**`，并通过 `AdminGuard` 校验管理员。
- 前端请求错误依赖后端返回的 `message` 字段，所以业务错误优先抛 `BusinessException`。
- 数据库结构变更必须新增 Flyway 迁移脚本，不再使用 `schema.sql`。
- 上传文件大小默认限制为单文件 `50MB`，单请求 `100MB`。
- 原始文件只进 RustFS，不把大文件二进制塞进 PostgreSQL。
- 分块文本改动后必须重建向量，否则 pgvector 中仍是旧文本语义。
- RocketMQ 当前负责异步分块和异步重建向量，后续可以补重试、死信队列和任务监控页。
- 前端后台 UI 继续沿用当前灰色工程风格，改样式前先看 [docs/frontend-style-guide.md](docs/frontend-style-guide.md)。

## 下一步

1. 增加 RAG 检索接口：pgvector 召回 + Qwen3 Reranker 重排。
2. 给知识库和分块检索补权限过滤。
3. 给 RocketMQ ingestion 增加重试次数、死信队列和失败任务管理页。
4. 将后台页面组件化，拆出知识库表格、文档表格、分块表格和通用弹窗。
5. 给 ingestion 核心链路补单元测试和集成测试。
