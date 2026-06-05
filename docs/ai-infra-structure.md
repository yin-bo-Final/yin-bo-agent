# AI 基础设施模块结构

这份文档说明 `ai-api` 和 `ai-infra` 两个模块的职责。`ai-api` 是 backend 和 ai-infra 共享的 HTTP 契约；`ai-infra` 是独立运行的模型基础设施服务。

## 模块总览

```text
SpringAI-Program/
├─ ai-api/
│  └─ src/main/java/com/yinbo/ai/api/
│     ├─ chat/       # LLMRequest、LLMResponse、流式 chunk 和接口定义
│     ├─ embedding/  # Embedding 请求响应和接口定义
│     ├─ model/      # 前端模型展示项
│     └─ rerank/     # Rerank 请求响应和接口定义
└─ ai-infra/
   └─ src/main/
      ├─ java/com/yinbo/ai/infra/
      │  ├─ chat/       # Chat 路由服务和供应商客户端
      │  ├─ config/     # AI 模型路由配置
      │  ├─ controller/ # /internal/ai/** HTTP 入口
      │  ├─ embedding/  # Embedding 路由服务和供应商客户端
      │  ├─ enums/      # 模型能力枚举
      │  ├─ filter/     # requestId 链路追踪
      │  ├─ http/       # 模型客户端异常
      │  ├─ model/      # 模型选择、熔断、路由执行器和目标上下文
      │  ├─ rerank/     # Rerank 路由服务和 noop 兜底客户端
      │  └─ YinboAiInfraApplication.java
      └─ resources/
         └─ application.yml
```

## `ai-api` 模块

只放跨模块契约，不放供应商实现，也不读取配置。

| 包 | 主要文件 | 功能 |
| --- | --- | --- |
| `chat` | `LLMRequest`、`LLMResponse`、`ChatStreamChunk`、`LLMService`、`StreamCallback` | Chat 请求响应、流式 NDJSON chunk 和业务接口 |
| `embedding` | `EmbeddingRequest`、`EmbeddingResponse`、`EmbeddingService` | Embedding HTTP 契约和业务接口 |
| `rerank` | `RerankRequest`、`RerankResponse`、`RerankService` | Rerank HTTP 契约和业务接口 |
| `model` | `ModelOption` | 前端模型下拉展示项 |

约定：

| 约定 | 说明 |
| --- | --- |
| 契约稳定 | backend 和 ai-infra 都依赖 `ai-api`，所以字段变更要兼容 |
| 不放实现 | 不能在 `ai-api` 里写 HTTP 客户端、供应商客户端或 Spring Bean |
| 不放业务实体 | 不引用 backend 的数据库实体、会话实体或 ingestion 实体 |

## `ai-infra` 模块

独立 Spring Boot 服务，默认端口 `8082`。它负责屏蔽供应商差异、模型选择、故障转移和熔断。

### HTTP 接口

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `GET` | `/internal/ai/models` | 返回前端可选 Chat 模型列表 |
| `POST` | `/internal/ai/chat` | 非流式 Chat 调用 |
| `POST` | `/internal/ai/chat/stream` | 流式 Chat 调用，返回 `application/x-ndjson` |
| `POST` | `/internal/ai/embeddings` | 批量生成 Embedding |
| `POST` | `/internal/ai/rerank` | 执行 Rerank，当前默认路由到 noop |

### 配置

模型供应商、候选模型、优先级和熔断策略都在 `ai-infra/src/main/resources/application.yml` 的 `app.ai` 下。

| 配置项 | 功能 |
| --- | --- |
| `app.ai.providers` | 配置硅基流动、百炼、Ollama、noop 等供应商连接信息 |
| `app.ai.chat.candidates` | Chat 候选模型、供应商、优先级、启用状态 |
| `app.ai.embedding.candidates` | Embedding 候选模型、维度、供应商和优先级 |
| `app.ai.rerank.candidates` | Rerank 候选模型，当前默认 `noop` |
| `app.ai.selection` | 连续失败阈值和熔断窗口 |
| `app.ai.models` | 前端模型下拉列表 |

### 核心组件

| 文件 | 功能 |
| --- | --- |
| `AiModelProperties.java` | 绑定 `app.ai` 配置，保存供应商、模型组、熔断策略和展示列表 |
| `ModelSelector.java` | 根据能力、用户请求模型、默认模型、优先级筛选候选 |
| `ModelHealthStore.java` | 记录模型连续失败次数，达到阈值后短暂熔断 |
| `ModelRoutingExecutor.java` | 按候选顺序调用供应商客户端，失败后切换下一个候选 |
| `ModelTarget.java` | 封装一次模型调用的候选模型、供应商和请求 URL |
| `AiInfraController.java` | 暴露 `/internal/ai/**` HTTP 接口给 backend 或 gateway |
| `ModelClientStreamClosedException.java` | 标识 backend 或客户端主动中断流式连接，不计入模型失败 |
| `RequestIdFilter.java` | 接收或生成 `X-Request-Id`，写入 MDC、响应头和 `event=access` 访问日志 |

### 能力子系统

| 能力 | 业务接口 | 路由实现 | 供应商接口 | 当前客户端 |
| --- | --- | --- | --- | --- |
| Chat | `LLMService` | `RoutingLLMService` | `ChatClient` | `SiliconFlowChatClient`、`BaiLianChatClient`、`OllamaChatClient` |
| Embedding | `EmbeddingService` | `RoutingEmbeddingService` | `EmbeddingClient` | `SiliconFlowEmbeddingClient` |
| Rerank | `RerankService` | `RoutingRerankService` | `RerankClient` | `NoopRerankClient` |

## 调用链路

gateway 也配置了 `/internal/ai/** -> YINBO_AI_INFRA_URI` 的内部路由，主要用于统一入口和调试；这条路由要求请求携带 `X-Internal-Token`，token 来自 `GATEWAY_INTERNAL_TOKEN`。backend 默认通过 `YINBO_AI_INFRA_URI` 直接调用 ai-infra。

### 普通聊天

```text
frontend /api/chat
-> gateway
-> backend ChatController
-> ChatService
-> AiInfraClient
-> ai-infra /internal/ai/chat
-> RoutingLLMService
-> ModelSelector / ModelRoutingExecutor
-> 供应商 ChatClient
```

### 流式聊天

```text
frontend /api/chat/stream
-> gateway
-> backend SseEmitter
-> AiInfraClient POST /internal/ai/chat/stream
-> ai-infra 返回 NDJSON delta / done
-> backend 转成前端 SSE delta / done
```

用户点击中断时，前端 SSE 会断开，backend 会关闭到 ai-infra 的流式 HTTP 连接。ai-infra 将这种情况记录为 `event=ai_stream_client_disconnected`，不会按模型调用失败处理，也不会触发模型熔断。

### 文档向量化

```text
RocketMQ consumer
-> DocumentIngestionService
-> AiInfraClient POST /internal/ai/embeddings
-> ai-infra RoutingEmbeddingService
-> EmbeddingClient
-> backend 短事务写入 knowledge_chunk_vector 和 knowledge_chunk
```

## 边界约定

| 约定 | 说明 |
| --- | --- |
| backend 不接供应商 | backend 不直接调硅基流动、百炼或 Ollama，只调 `AiInfraClient` |
| ai-infra 不接业务库 | ai-infra 不读写 PostgreSQL 业务表、不处理会话、不处理 ingestion_task |
| ai-api 不接实现 | ai-api 只保存 DTO 和接口，不放 Spring Bean 和 HTTP 实现 |
| 配置归属清晰 | 模型供应商配置放 ai-infra，RAG 文档入库参数放 backend |
| 流式协议稳定 | ai-infra 对 backend 使用 NDJSON，backend 对前端使用 SSE |
| 内部接口受保护 | gateway 暴露的 `/internal/**` 必须校验 `X-Internal-Token`，不能作为公网调试接口裸露 |
