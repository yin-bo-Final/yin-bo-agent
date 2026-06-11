# 项目结构总览

这份文档给新对话快速定位项目用。它只讲“整体怎么分层、该先读哪个文档”。后端和前端的详细结构已经拆开维护。

## 文档导航

| 文档                                                           | 用途                                    |
| ------------------------------------------------------------ | ------------------------------------- |
| [docs/gateway-structure.md](docs/gateway-structure.md)       | 网关模块、路由转发、真实 IP、限流、并发控制和统一错误响应          |
| [docs/ai-infra-structure.md](docs/ai-infra-structure.md)     | AI 基础设施服务、模型路由、供应商客户端和 HTTP 契约       |
| [docs/backend-structure.md](docs/backend-structure.md)       | 后端包结构、RAG ingestion、Flyway、数据表和常见改动入口 |
| [docs/rag-conversation-pipeline-flow.md](docs/rag-conversation-pipeline-flow.md) | RAG 会话流水线、短路点、记忆压缩和后续检索接入点 |
| [docs/frontend-structure.md](docs/frontend-structure.md)     | 前端页面结构、API 封装、后台管理 UI 和路由状态           |
| [docs/frontend-style-guide.md](docs/frontend-style-guide.md) | 前端视觉风格、按钮、弹窗、下拉栏、tooltip 等样式约定        |
| [codex.md](codex.md)                                         | 项目提示词、协作习惯、本地中间件位置和 Git 提交习惯          |

## 顶层目录

```text
SpringAI-Program/
├─ ai-api/                          # AI 基础设施 HTTP 契约 DTO
├─ ai-infra/                        # 独立 AI 基础设施服务
├─ backend/                         # Spring Boot 后端
├─ gateway/                         # Spring Cloud Gateway 网关
├─ frontend/                        # Vue 3 前端
├─ docs/                            # 模块细分文档
├─ codex.md                         # 给 AI / Codex 阅读的协作规则和提示词
├─ project-structure.md             # 项目结构总览和文档导航
├─ local-secrets.example.yml        # 本地私密配置模板
├─ local-secrets.yml                # 本地私密配置，不提交
├─ pom.xml                          # Maven 聚合工程，目前聚合 ai-api、ai-infra、backend 和 gateway
└─ README.md                        # 项目入口文档
```

当前工程采用“前端单页 + 独立网关 + backend 业务服务 + ai-infra 模型基础设施服务 + 中间件外置”的结构。`gateway` 是统一入口，`backend` 负责业务和数据事务，`ai-infra` 负责模型供应商、路由、熔断和故障转移，`ai-api` 保存二者之间的 HTTP 契约。

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
      -> HTTP 调用 ai-infra
        -> 路由 Chat / Embedding / Rerank 模型
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
-> ConversationFlowExecutor 编排 chat/flow 子包阶段服务
-> 生命周期、加载记忆、保存用户消息、按预算压缩 Prompt 记忆、术语统一、查询改写和问题拆分、意图识别、歧义引导、检索占位和响应输出
-> AiInfraClient
-> ai-infra /internal/ai/chat 或 /internal/ai/chat/stream
-> ModelSelector / ModelRoutingExecutor / 供应商 ChatClient
-> 保存消息、端到端总耗时、assistant Trace 和 token
```

### 文档入库

```text
上传文件 / 提交 URL
-> RustFS 保存原始文件
-> knowledge_document.status = UPLOADING -> UPLOADED
-> 管理员点击分块
-> RocketMQ 投递 CHUNK 任务
-> Tika 解析纯文本
-> 文本清洗和分块
-> AiInfraClient 调用 ai-infra /internal/ai/embeddings
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
| `conversation_memory_summary` | `chat`               | 会话记忆摘要、覆盖消息水位线、压缩触发来源        |
| `chat_terminology_term` | `chat`               | 查询预处理标准术语                         |
| `chat_terminology_alias` | `chat`              | 查询预处理别名、关键词映射                    |
| `chat_query_rewrite_record` | `chat`          | 查询改写、拆分、降级和模型原始响应记录             |
| `chat_pipeline_config` | `chat`              | 查询预处理 Pipeline 开关和降级策略             |
| `chat_intent_node`     | `chat`              | 意图树节点、叶子路由目标、示例问题和节点级检索配置    |
| `chat_intent_rule`     | `chat`              | 可配置意图规则、关键词条件、目标节点和命中分数       |
| `chat_intent_resolve_record` | `chat`       | 意图识别输入、命中节点、最终意图、歧义状态、降级原因和耗时记录 |
| `knowledge_base`         | `knowledge`               | 知识库名称、collection、Embedding 模型 |
| `knowledge_document`     | `ingestion` / `knowledge` | 文档元数据、RustFS 对象信息、状态、耗时       |
| `knowledge_chunk`        | `ingestion` / `knowledge` | 分块内容、启用状态、token、字符数、向量 ID     |
| `knowledge_chunk_vector` | `ingestion`              | pgvector 向量存储表                 |
| `ingestion_task`         | `ingestion`               | 分块 / 重建向量任务状态、重试次数和失败原因       |

数据库结构由 Flyway 接管，迁移脚本位于 `backend/src/main/resources/db/migration`。不要恢复旧的 `schema.sql`。已经执行过的迁移文件不要再改内容；表结构继续演进时新增下一个版本脚本，例如意图规则 `priority` 字段通过 `V10__drop_chat_intent_rule_priority.sql` 删除。

## 常见任务先读哪里

| 任务 | 先读 |
| --- | --- |
| 网关路由、统一入口、真实 IP、限流、鉴权前置 | [docs/gateway-structure.md](docs/gateway-structure.md) |
| 模型路由、供应商接入、AI HTTP 契约 | [docs/ai-infra-structure.md](docs/ai-infra-structure.md) |
| 后端接口、数据库、RAG、RocketMQ、RustFS | [docs/backend-structure.md](docs/backend-structure.md) |
| RAG 会话流水线、记忆压缩、summary 水位线、Prompt 记忆视图 | [docs/rag-conversation-pipeline-flow.md](docs/rag-conversation-pipeline-flow.md) |
| 前端页面、后台管理、会话 UI | [docs/frontend-structure.md](docs/frontend-structure.md) |
| 只改样式 | [docs/frontend-style-guide.md](docs/frontend-style-guide.md) |
| 新对话交接、工作习惯、提交规范 | [codex.md](codex.md) |

## 工程约定

- 网关包名根路径是 `com.yinbo.gateway`，后端业务服务包名根路径是 `com.yinbo.agent`，AI 基础设施服务包名根路径是 `com.yinbo.ai.infra`。
- 前端 `/api` 请求默认先进入 gateway，再由 gateway 转发到后端业务服务。
- backend 通过 `AiInfraClient` 远程调用 ai-infra，HTTP 契约放在 `ai-api`，不要让 backend 反向依赖 ai-infra 实现类。
- 会话生成入口由 `ChatService` 接收，阶段化处理放在 `chat/flow`；`ConversationFlowExecutor` 只负责编排，生命周期、记忆加载、记忆压缩、消息持久化、LLM 调用、查询改写、意图识别树、歧义引导、RAG 检索和工具调用分别扩展对应子包服务。
- 意图识别的多子问题分类走 `intentClassifyExecutor` 专用线程池；结果会写入 `chat_intent_resolve_record` 并打印带 `outcome`、`fallbackReason`、`durationMs` 的 `event=intent_resolved` 日志；意图节点被规则引用时不要直接改 `nodeCode` 或删除节点，先调整规则。
- 意图强规则回归样例在 `backend/src/test/resources/intent-rule-bad-cases.csv`，新增规则或修 bad case 后同步补样例。
- 后台接口路径统一放在 `/api/admin/**`。
- 业务错误优先抛 `BusinessException`。
- 数据库结构变更必须新增 Flyway 迁移脚本，已经执行过的迁移文件不能直接改内容。
- 原始文件进入 RustFS，数据库保存对象定位信息。
- 向量在 pgvector，由 `vectorDocumentId` 关联业务分块。
- 模型调用统一走独立 `ai-infra` 服务，Chat / Embedding / Rerank 供应商配置只放在 ai-infra。
- 上传完成后保持 `UPLOADED`，管理员点击分块才投递 RocketMQ 并进入 `PROCESSING`。
- RocketMQ 当前负责分块和重建向量异步化，消费者通过 Redis 信号量限制全系统处理并发。
- 入库任务状态记录在 `ingestion_task`，后台通过 `/admin/tasks/failed` 查看失败任务并手动重试。
- 任务重试耗尽后标记 `DEAD`，忙碌文档禁止删除和修改分块，减少异步处理期间的数据覆盖。
- 前端暂时没有 Vue Router，使用路径解析和 `window.history` 维护页面状态。
- 新增后台 UI 要复用现有按钮、表格、弹窗、下拉栏和 tooltip 风格。
