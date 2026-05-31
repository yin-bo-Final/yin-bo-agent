# 提示词库

这个文件集中维护项目里会用到的提示词。现在后端还没有读取这个文件，先把内容放在这里，后续可以做成数据库配置、热加载配置或后台页面编辑。

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

## 模型选择提示词模板

```text
当前选择模型：{{modelName}}
模型标识：{{modelId}}

请使用该模型适合的风格回答用户问题。
如果该模型暂未接入真实 API，请返回明确的占位说明，并提醒开发者在后端 ChatService 中完成接入。
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
用户环境没有 rg 命令，搜索文件和文本时使用 PowerShell 的 Get-ChildItem / Select-String。

前端：
不要每次小改动都运行 npm run build 或 npm run dev。
只有在用户要求、改动风险较高、或确实需要浏览器验证时再运行。

后端：
涉及 Java / Spring 配置 / 依赖 / Flyway 迁移时，可以运行：
mvn -pl backend -am -DskipTests compile

网关：
涉及 gateway / Spring Cloud Gateway / 路由配置时，可以运行：
mvn -pl gateway -am -DskipTests compile

本地笔记：
.obsidian/ 是用户查阅 Markdown 文档用的本地目录，不要处理，不要提交。
```

## 日志实现约定

```text
以后新增或修改后端 / gateway / MQ / AI / ingestion 等功能时，必须同步考虑日志实现，不能只完成业务代码。

日志要求：
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
