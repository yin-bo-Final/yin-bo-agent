# Codex 协作规则与提示词库

这个文件放在项目根目录，方便 AI / Codex 开新对话时优先阅读。这里集中维护项目提示词、协作习惯、本地环境约定、日志约定、文档约定和 Git 提交习惯。
## 平台定位提示词

```text
你是“音波AI agent 智能助手平台”的智能助手。
你的目标是帮助用户完成学习、编程、资料整理和任务规划。
回答要清晰、直接、可执行。
当用户正在学习技术时，先解决问题，再用简洁语言解释背后的知识点。
```

## 默认对话系统提示词

```text
你是一个专业、耐心、简洁的 AI Agent 助手。
你需要根据用户当前问题给出可执行答案。
如果问题涉及代码，你应优先给出能运行的实现，并解释关键知识点。
如果缺少必要信息，先基于合理默认值继续推进，再标明哪些地方后续可以替换。
```

## 编程学习模式提示词

```text
用户希望通过纯 vibe coding 的方式边做边学。
每次完成一个功能后，你需要补充：
1. 本次改了什么。
2. 这次涉及哪些关键知识点。
3. 用户下一步可以怎么练习或扩展。
解释要短，不要把学习节奏打断。
```


## 新对话协作上下文

```text
主要技术栈是 Java 后端，熟悉 Spring Boot、MySQL、Redis、RocketMQ、Dubbo、Spring Cloud、Docker。
当前目标是学习并实践 Java Agent、Spring AI、Agent、RAG、MCP 等技术。

回答风格：
- 先解决问题，再解释关键知识点。
- 解释要偏后端工程视角，通俗但不敷衍。
- 如果用户理解有偏差，要直接指出并纠正。
```

## 本地环境约定

```text
项目路径：
C:\Users\35575\Desktop\SpringAI-Program

中间件部署位置：
PostgreSQL、Redis、RocketMQ、RustFS 都部署在 WSL 里的 Docker 中，并把端口映射到 Windows localhost。

常用地址：
Gateway: localhost:8081
后端业务服务: localhost:8080
AI 基础设施服务: localhost:8082
PostgreSQL: localhost:5432
Redis: localhost:6379
RocketMQ NameServer: localhost:9876
RocketMQ Dashboard: http://localhost:18082/
RustFS S3 Endpoint: http://localhost:9000
RustFS Dashboard: http://localhost:9001/rustfs/console/index.html

本地密钥配置：
local-secrets.yml 是本地私密配置，不提交。
local-secrets.example.yml 是模板，可以提交。
RustFS、RocketMQ、数据库、Redis、模型 API Key 都优先从 local-secrets.yml 或环境变量读取。
```

## 开发和命令习惯

```text
前端：
不要每次小改动都运行 npm run build 或 npm run dev。
只有在用户要求、改动风险较高、或确实需要浏览器验证时再运行。

后端：
涉及 Java / Spring 配置 / 依赖 / Flyway 迁移时，可以运行：
mvn -pl backend -am -DskipTests compile

AI 基础设施：
涉及模型路由、供应商客户端、Chat / Embedding / Rerank 契约时，可以运行：
mvn -pl ai-infra -am -DskipTests compile

网关：
涉及 gateway / Spring Cloud Gateway / 路由配置时，可以运行：
mvn -pl gateway -am -DskipTests compile

本地笔记：
.obsidian/ 是用户查阅 Markdown 文档用的本地目录，不要处理，不要提交。
```

## 代码注释约定

```text
以后新增或修改代码时，必须同步补充必要注释，不能只写业务代码。

注释风格：
- 类、接口、枚举、record 前使用一行中文注释，直接说明它的职责。
- 方法前使用一行中文注释，直接说明这个方法做什么。
- 注释不使用“功能：”“说明：”这类前缀。
- 注释要短、准、像标题一样说明职责，不要逐行翻译代码。
- 复杂逻辑内部可以补充关键步骤注释，但只解释为什么这样做，不解释显而易见的语法。

Java 示例：

// 频率限流响应全局过滤器。
public class RateLimitResponseGlobalFilter implements GlobalFilter, Ordered {

    // 拦截 RedisRateLimiter 产生的 429 响应并改写响应体。
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // no-store 避免浏览器或代理缓存限流响应。
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}

配置文件示例：

# 网关服务端口。
server:
  port: ${GATEWAY_PORT:8081}

每次实现功能时，需要优先检查这些位置是否需要注释：
1. 新增类、配置类、过滤器、Controller、Service、Consumer、工具类。
2. 新增入口方法、核心业务方法、回调方法、过滤器顺序方法。
3. 新增 Redis / MQ / AI / Gateway / 文件上传 / 限流 / 响应写入等关键链路代码。
4. 新增不容易一眼看懂的条件判断、异常兜底、资源释放、异步处理逻辑。
```

## 日志实现约定

```text
以后新增或修改后端 / gateway / MQ / AI / ingestion 等功能时，必须同步考虑日志实现，不能只完成业务代码。

日志要求：
- 所有 Java 服务的日志规范必须和 gateway / backend 保持一致，包括 `requestId` MDC、`event=access` 入口访问日志、`method/path/status/costMs/slow/clientIp/userAgent` 字段、console/file pattern、按日期和大小滚动、保留天数和总量上限。
- 耗时字段命名保持一致：HTTP access 层使用 `costMs`，业务阶段日志和流水线记录使用 `durationMs`，例如 query rewrite、intent resolve、LLM chat。
- 聊天流水线耗时拆分统一写入 `assistantTrace.durationStages`；新增阶段必须通过 `ChatExecutionContext.recordDurationStage(code, label, durationMs)` 记录，不要再为每个新阶段单独往 Trace 顶层加 `xxxDurationMs` 字段。`responseDurationMs` / `chat_message.response_duration_ms` 表示本轮端到端总耗时。
- 新增服务时必须补齐和 gateway / backend 同风格的请求过滤器或全局过滤器；例如 ai-infra 也要记录 `/internal/**` 的 access log，不能只依赖 Spring / Tomcat 默认日志。
- 系统间 HTTP 调用必须透传 `X-Request-Id`，例如 gateway -> backend、backend -> ai-infra。
- 关键业务动作必须有 event=... 日志，例如创建、删除、状态变更、异步任务投递、异步任务消费、外部模型调用完成或失败。
- 跨服务、跨线程、跨 MQ 的链路必须尽量透传 requestId；如果进入异步线程，需要手动复制 MDC。
- 正常关键节点使用 INFO。
- 可恢复失败、业务失败、慢请求使用 WARN。
- 系统异常、外部依赖不可用、MQ 投递或消费失败使用 ERROR。
- 日志必须使用 key-value 风格，方便后续 grep、ELK、Loki 或 OpenSearch 采集。
- 日志中不能打印密码、Cookie、Authorization、API Key、Token、完整请求体等敏感信息。
- 如果日志字段可能来自用户输入，例如 username、fileName、URL、异常 message，需要做换行、制表符和超长内容处理。

实现功能时，需要优先思考这些日志点：
1. 入口日志：这个功能从哪个 Controller / Gateway / Consumer 进入。
2. 状态日志：是否改变了数据库状态、任务状态、文档状态或会话状态。
3. 外部依赖日志：是否调用了 AI 模型、Embedding、RocketMQ、RustFS、Redis、数据库或 pgvector。
4. 完成日志：成功时记录必要 ID、耗时、数量、模型、状态等可排查字段。
5. 失败日志：失败时记录 event、requestId、业务 ID、异常类型、简短 message。

如果本次功能属于纯前端样式、纯文档说明、纯静态配置，且没有后端链路变化，可以不新增运行时日志，但要明确判断原因。
```

## 模块文档编写约定

```text
以后新增或大幅修改模块时，必须同步更新对应 docs 文档，不能只改代码不改说明。

文档颗粒度：
- 服务结构文档主要说明“有哪些模块、每个模块负责什么功能”，不要写成从零教程。
- 列出目录结构时只列业务模块和关键配置，不需要解释 pom 文件、启动类这类通用工程文件。
- 每个重要模块都要说明职责、包含的文件、核心功能和依赖关系。
- Service / Filter / Consumer / Config 这类文件如果有多个核心方法，可以用表格列出方法职责，但不要展开成过长流程。
- 涉及配置文件时，只说明和业务功能直接相关的配置项，例如路由、限流、日志、Redis、MQ、AI。
- 涉及错误响应、限流、并发、链路追踪、日志时，可以给出必要示例，但不要把常见排查、服务命名、启动方式写进结构文档，除非用户明确要求。
- 文档内容要贴近当前代码，不要写未来规划当成已经实现的功能。
- 更新 docs 目录下的模块文档后，如果文档导航、目录结构、启动方式、模块职责或对外入口发生变化，必须同步检查并按需更新根目录 README.md 和 project-structure.md。
- backend 不直接接模型供应商；模型路由、供应商客户端、熔断和故障转移放在 ai-infra，backend 通过 AiInfraClient 远程调用。
- backend 和 ai-infra 的共享契约放在 ai-api；不要让 ai-api 依赖 backend 实体或 ai-infra 实现类。

更新 gateway 文档时，优先使用 docs/gateway-structure.md 的结构：
1. 模块定位。
2. 目录总览。
3. 每个模块说明。
4. 每个模块包含的文件及功能。
5. 核心方法职责。
6. 功能清单。
```

## Git 提交习惯

```text
用户习惯直接通过 git 将项目推送到 GitHub。
提交时必须写提交说明，并且是中英文双语。

格式示例：
feat: rebuild the program/重构了整个项目
fix: resolve vector rebuild transaction/修复向量重建事务问题
docs: split project structure docs/拆分项目结构文档

提交前注意：
- 不要提交 local-secrets.yml。
- 不要提交 .obsidian/。
- 不要回滚用户已有改动。
- 如果用户明确要求 commit and push，提交信息也要保持中英文双语。
```
