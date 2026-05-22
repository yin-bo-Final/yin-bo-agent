# 项目结构

这份文档用于快速定位代码。README 更偏向“怎么启动、现在做到哪了”，这里更偏向“每个目录负责什么、后续改功能应该去哪里动”。

## 总览

```text
SpringAI-Program/
├─ backend/                         # Spring Boot 后端模块
├─ frontend/                        # Vue 3 前端模块
├─ docs/                            # 项目文档
├─ local-secrets.example.yml        # 本地配置模板
├─ local-secrets.yml                # 本地私密配置，不提交
├─ pom.xml                          # Maven 聚合工程，只聚合 backend
└─ README.md                        # 项目入口文档
```

## 后端结构

```text
backend/
├─ pom.xml
└─ src/main/
   ├─ java/com/yinbo/agent/
   │  ├─ YinboAgentApplication.java
   │  ├─ auth/
   │  │  ├─ AuthBootstrapRunner.java
   │  │  ├─ AuthConstants.java
   │  │  ├─ AuthController.java
   │  │  ├─ AuthService.java
   │  │  ├─ LoginInterceptor.java
   │  │  ├─ SessionAuthService.java
   │  │  ├─ dto/
   │  │  ├─ entity/
   │  │  ├─ mapper/
   │  │  └─ session/
   │  ├─ chat/
   │  │  ├─ ChatController.java
   │  │  ├─ ChatService.java
   │  │  ├─ PlaceholderChatService.java
   │  │  ├─ dto/
   │  │  ├─ entity/
   │  │  └─ mapper/
   │  ├─ common/
   │  │  ├─ ApiErrorResponse.java
   │  │  ├─ BusinessException.java
   │  │  └─ GlobalExceptionHandler.java
   │  └─ config/
   │     ├─ AiModelProperties.java
   │     ├─ AuthProperties.java
   │     ├─ MybatisPlusAutoFillConfig.java
   │     ├─ PasswordConfig.java
   │     └─ WebConfig.java
   └─ resources/
      ├─ application.yml
      └─ schema.sql
```

### `auth/` 认证模块

`auth` 负责用户生命周期和登录态。

| 文件或目录 | 作用 |
| --- | --- |
| `AuthController` | 暴露 `/api/auth/*` 认证接口 |
| `AuthService` | 认证服务抽象，方便后续替换实现 |
| `SessionAuthService` | 当前实现，基于 Session + Redis |
| `LoginInterceptor` | 拦截需要登录的接口，校验用户是否有效 |
| `AuthBootstrapRunner` | 启动时根据配置创建本地开发种子账号 |
| `AuthConstants` | 认证模块常量 |
| `dto/` | 登录、注册、当前用户、注销账号等请求响应对象 |
| `entity/AuthUser` | `auth_user` 表实体 |
| `mapper/AuthUserMapper` | MyBatis-Plus 用户 Mapper |
| `session/LoginUser` | Session 中保存的轻量用户信息 |

当前受登录拦截器保护的接口包括：

```text
/api/auth/me
/api/auth/logout
/api/chat
/api/conversations
/api/conversations/**
```

### `chat/` 聊天模块

`chat` 负责模型列表、消息发送、会话创建、会话列表和历史消息回放。

| 文件或目录 | 作用 |
| --- | --- |
| `ChatController` | 暴露 `/api/models`、`/api/chat`、`/api/conversations` |
| `ChatService` | 聊天服务抽象 |
| `PlaceholderChatService` | 当前聊天实现，名字仍是 Placeholder，但已经会尝试调用 Spring AI `ChatModel` |
| `dto/ChatRequest` | 前端发送聊天消息的请求体 |
| `dto/ChatResponse` | assistant 回复响应 |
| `dto/ConversationSummaryResponse` | 会话列表项 |
| `dto/ConversationDetailResponse` | 会话详情和消息列表 |
| `entity/ChatConversation` | `chat_conversation` 表实体 |
| `entity/ChatMessageEntity` | `chat_message` 表实体 |
| `mapper/ChatConversationMapper` | 会话 Mapper |
| `mapper/ChatMessageMapper` | 消息 Mapper |

聊天请求的大致链路：

```text
前端 submitMessage
-> POST /api/chat
-> LoginInterceptor 校验登录
-> ChatController.chat
-> SessionAuthService.requireActiveUser
-> PlaceholderChatService.chat
-> 保存 user 消息
-> 调用 Spring AI ChatModel 或返回兜底内容
-> 保存 assistant 消息
-> 更新会话最近消息时间和模型
-> 返回 ChatResponse
```

### `common/` 通用模块

`common` 负责统一错误表达。

| 文件 | 作用 |
| --- | --- |
| `BusinessException` | 带 HTTP 状态码的业务异常 |
| `ApiErrorResponse` | 统一错误响应体 |
| `GlobalExceptionHandler` | 全局异常处理，避免异常直接裸奔给前端 |

### `config/` 配置模块

| 文件 | 作用 |
| --- | --- |
| `AiModelProperties` | 读取 `app.ai.models` 模型列表，并提供按 ID 查找模型 |
| `AuthProperties` | 读取本地种子管理员账号配置 |
| `MybatisPlusAutoFillConfig` | 自动填充 `created_at`、`updated_at` |
| `PasswordConfig` | 注册 `BCryptPasswordEncoder` |
| `WebConfig` | CORS 配置和登录拦截器注册 |

### `resources/`

| 文件 | 作用 |
| --- | --- |
| `application.yml` | 端口、数据源、Redis、Session、OpenAI Compatible、MCP、RocketMQ、模型列表 |
| `schema.sql` | 初始化用户表、会话表、消息表和索引 |

## 前端结构

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
   └─ api/
      ├─ authApi.js
      └─ chatApi.js
```

### `App.vue`

当前前端主要逻辑集中在 `App.vue`，它承担了这些职责：

- 登录 / 注册切换
- 登录态恢复
- 退出登录和注销账号
- 模型列表加载和模型选择
- 新建会话
- 发送聊天消息
- 会话列表加载
- 会话搜索
- 点击历史会话回放消息
- `/c/{conversationId}` 路由同步
- assistant Markdown 渲染
- 侧边栏展开 / 折叠

后续如果页面继续变复杂，建议把它拆成：

```text
src/components/
├─ AuthPanel.vue
├─ Sidebar.vue
├─ ChatHeader.vue
├─ MessageList.vue
├─ MessageComposer.vue
└─ ModelPicker.vue
```

### `src/api/`

| 文件 | 作用 |
| --- | --- |
| `authApi.js` | 封装注册、登录、当前用户、退出、注销接口 |
| `chatApi.js` | 封装模型列表、发送消息、会话列表、会话详情接口 |

两个 API 文件都使用 `credentials: 'include'`，这是为了让浏览器在请求后端时带上 Session Cookie。

### `styles.css`

集中维护所有页面样式，包括：

- 登录注册页
- 蜂窝背景和指针光效
- 聊天主布局
- 侧边栏和折叠 rail
- 会话列表和搜索
- 模型选择浮层
- Markdown 消息样式
- 用户菜单和注销确认面板

### `vite.config.js`

本地开发代理：

```js
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  }
}
```

这意味着前端代码只请求 `/api`，开发环境由 Vite 转发到后端。

### `nginx/default.conf`

生产部署时：

- `/` 走 Vue 单页应用入口
- `/api/` 反向代理到后端容器 `backend:8080`

## 数据结构

### `auth_user`

用户表。

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `username` | 用户名 |
| `password_hash` | BCrypt 密码哈希 |
| `display_name` | 展示名称 |
| `status` | 用户状态，`1` 有效，`0` 已注销 |
| `last_login_at` | 最近登录时间 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

用户名唯一规则是：

```text
只有 status = 1 的有效用户要求 username 唯一。
```

这由 PostgreSQL 部分唯一索引实现。

### `chat_conversation`

会话表。

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `conversation_no` | 对外暴露的会话号 |
| `user_id` | 所属用户 |
| `title` | 会话标题 |
| `model_id` | 最近一次使用的模型 |
| `last_message_at` | 最近消息时间 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

### `chat_message`

消息表。

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `conversation_id` | 所属会话主键 |
| `user_id` | 所属用户 |
| `role` | 消息角色，当前主要是 `user` 和 `assistant` |
| `content` | 消息正文 |
| `model_id` | 本条消息对应模型 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

## 配置流向

```text
local-secrets.yml
-> application.yml
-> Spring Boot Environment
-> @ConfigurationProperties / 自动配置
-> 业务代码使用
```

当前比较重要的配置前缀：

| 前缀 | 说明 |
| --- | --- |
| `spring.datasource` | PostgreSQL 连接 |
| `spring.data.redis` | Redis 连接 |
| `spring.session.redis` | Session 存储 |
| `spring.ai.openai` | OpenAI Compatible 模型调用 |
| `spring.ai.mcp.server` | MCP Server 基础配置 |
| `rocketmq` | RocketMQ 基础配置 |
| `app.ai.models` | 前端可选模型列表 |
| `app.auth` | 本地开发种子账号 |

## 适合继续拆分的点

当前项目还在学习和快速迭代阶段，所以 `App.vue` 和 `PlaceholderChatService` 承担了较多职责。后续更工程化时，建议优先拆：

1. `PlaceholderChatService` 改名为 `SpringAiChatService`
2. 将模型调用、消息持久化、会话标题生成拆成独立组件
3. 前端把认证、侧边栏、消息列表、输入框拆成组件
4. 引入流式响应后，单独建立 `streamChat` API 和前端增量渲染状态
5. RAG 落地时新增 `knowledge/` 或 `rag/` 模块，避免塞进 `chat/`
