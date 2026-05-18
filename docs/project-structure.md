# 项目结构

```text
SpringAI-Program/
├─ backend/                 # Spring Boot 后端工程
│  ├─ pom.xml               # Maven 依赖与版本管理
│  └─ src/
│     └─ main/
│        ├─ java/
│        │  └─ com/yinbo/agent/
│        │     ├─ YinboAgentApplication.java
│        │     ├─ chat/     # 对话 API、DTO、服务接口
│        │     └─ config/   # 模型配置、跨域配置
│        └─ resources/
│           └─ application.yml
├─ frontend/                # Vue 3 前端工程
│  ├─ src/
│  │  ├─ App.vue            # 对话主界面
│  │  ├─ main.js            # Vue 入口
│  │  ├─ styles.css         # 页面样式
│  │  └─ api/chatApi.js     # 后端接口封装
│  ├─ nginx/default.conf    # Nginx 静态部署与 API 代理配置
│  ├─ Dockerfile            # 前端生产镜像构建
│  └─ vite.config.js        # 本地开发代理配置
└─ docs/
   ├─ project-structure.md  # 当前文档
   └─ prompts.md            # 项目提示词集中维护
```

## 后端分层

- `chat/ChatController`：HTTP API 层，负责接收前端请求。
- `chat/ChatService`：对话服务抽象，后续真实 LLM 调用从这里接入。
- `chat/PlaceholderChatService`：当前占位实现，不调用真实模型。
- `config/AiModelProperties`：从 `application.yml` 读取可选模型列表。
- `config/WebConfig`：本地开发跨域配置，允许 Vue dev server 调用后端。

## 前端分层

- `App.vue`：当前只有一个主界面，包含侧边栏、模型选择、消息列表、输入框。
- `api/chatApi.js`：集中封装 `/api/models` 和 `/api/chat`。
- `styles.css`：所有视觉样式，方便你学习 Vue 时先少碰工程配置。

## 下一步建议

1. 接入真实模型供应商，比如 DashScope、OpenAI compatible API 或 DeepSeek。
2. 加入流式响应，让回复像 ChatGPT 一样逐字生成。
3. 引入 PostgreSQL 保存会话与消息。
4. 引入 PGVector 做 RAG。
5. 把可调用工具注册成 MCP Tool Server。
6. 加 Redis 做短期记忆或限流。
7. 加 RocketMQ 做异步任务，比如长文档解析、知识库索引。
