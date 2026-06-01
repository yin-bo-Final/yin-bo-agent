# 项目结构总览

这份文档给新对话快速定位项目用。它只讲“整体怎么分层、该先读哪个文档”。后端和前端的详细结构已经拆开维护。

## 文档导航

| 文档                                                           | 用途                                    |
| ------------------------------------------------------------ | ------------------------------------- |
| [docs/gateway-structure.md](docs/gateway-structure.md)       | 网关模块、路由转发、真实 IP、限流、并发控制和统一错误响应          |
| [docs/backend-structure.md](docs/backend-structure.md)       | 后端包结构、RAG ingestion、Flyway、数据表和常见改动入口 |
| [docs/frontend-structure.md](docs/frontend-structure.md)     | 前端页面结构、API 封装、后台管理 UI 和路由状态           |
| [docs/frontend-style-guide.md](docs/frontend-style-guide.md) | 前端视觉风格、按钮、弹窗、下拉栏、tooltip 等样式约定        |
| [codex.md](codex.md)                                         | 项目提示词、协作习惯、本地中间件位置和 Git 提交习惯          |

## 顶层目录

```text
SpringAI-Program/
├─ backend/                         # Spring Boot 后端
├─ gateway/                         # Spring Cloud Gateway 网关
├─ frontend/                        # Vue 3 前端
├─ docs/                            # 模块细分文档
├─ codex.md                         # 给 AI / Codex 阅读的协作规则和提示词
├─ project-structure.md             # 项目结构总览和文档导航
├─ local-secrets.example.yml        # 本地私密配置模板
├─ local-secrets.yml                # 本地私密配置，不提交
├─ pom.xml                          # Maven 聚合工程，目前聚合 backend 和 gateway
└─ README.md                        # 项目入口文档
```

当前工程采用“前端单页 + 独立网关 + 后端模块化包 + 中间件外置”的结构。`gateway` 是统一入口，`backend` 是业务服务。后端目前按业务边界拆包，等功能继续变大后，再考虑继续拆出更多 Maven 模块或服务，例如 `auth`、`chat`、`rag`、`knowledge`、`ingestion`、`mcp`。

## 系统分层

```text
Vue 3 前端
  -> Spring Cloud Gateway 网关
    -> Spring Boot 业务服务
      -> PostgreSQL 保存业务表
      -> pgvector 保存知识库向量
      -> Redis 保存 Session 登录态、gateway 上传并发信号量和 service 兜底信号量
      -> RustFS 保存上传原始文件
      -> RocketMQ 承载异步 ingestion 任务
      -> Spring AI 调用聊天模型和 Embedding 模型
      -> Apache Tika 解析 PDF / Word / Markdown / TXT
```

## 主要链路

### 聊天

```text
ConversationPage
-> gateway /api/chat 或 /api/chat/stream
-> LoginInterceptor
-> ChatController
-> ChatService
-> Spring AI ChatModel
-> 保存消息、响应耗时和 token
```

### 文档入库

```text
上传文件 / 提交 URL
-> RustFS 保存原始文件
-> knowledge_document.status = UPLOADED
-> 管理员点击分块
-> RocketMQ 投递 CHUNK 任务
-> Tika 解析纯文本
-> 文本清洗和分块
-> Embedding 向量化
-> pgvector 保存向量
-> knowledge_chunk 保存分块元数据
-> knowledge_document.status = COMPLETED / FAILED
```

### 重建向量

```text
管理员点击重建向量
-> RocketMQ 投递 REBUILD_VECTORS 任务
-> Consumer 读取已有 knowledge_chunk
-> 重新生成向量并更新 vectorDocumentId
-> 事务成功后清理旧向量
```

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

数据库结构由 Flyway 接管，迁移脚本位于 `backend/src/main/resources/db/migration`。不要恢复旧的 `schema.sql`。

## 常见任务先读哪里

| 任务 | 先读 |
| --- | --- |
| 网关路由、统一入口、真实 IP、限流、鉴权前置 | [docs/gateway-structure.md](docs/gateway-structure.md) |
| 后端接口、数据库、RAG、RocketMQ、RustFS | [docs/backend-structure.md](docs/backend-structure.md) |
| 前端页面、后台管理、会话 UI | [docs/frontend-structure.md](docs/frontend-structure.md) |
| 只改样式 | [docs/frontend-style-guide.md](docs/frontend-style-guide.md) |
| 新对话交接、工作习惯、提交规范 | [codex.md](codex.md) |

## 工程约定

- 网关包名根路径是 `com.yinbo.gateway`，后端业务服务包名根路径是 `com.yinbo.agent`。
- 前端 `/api` 请求默认先进入 gateway，再由 gateway 转发到后端业务服务。
- 后台接口路径统一放在 `/api/admin/**`。
- 业务错误优先抛 `BusinessException`。
- 数据库结构变更必须新增 Flyway 迁移脚本。
- 原始文件进入 RustFS，数据库保存对象定位信息。
- 向量在 pgvector，由 `vectorDocumentId` 关联业务分块。
- RocketMQ 当前负责分块和重建向量异步化，消费者通过 Redis 信号量限制全系统处理并发。
- 前端暂时没有 Vue Router，使用路径解析和 `window.history` 维护页面状态。
- 新增后台 UI 要复用现有按钮、表格、弹窗、下拉栏和 tooltip 风格。
