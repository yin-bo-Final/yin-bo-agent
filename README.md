# 音波 AI Agent 智能助手平台

这是一个面向 Java 后端学习和 AI Agent 实战的前后端分离项目。项目目标不是只做一个“能聊天的页面”，而是逐步沉淀成一个可以继续扩展模型调用、会话记忆、RAG、MCP Tool Server 和 Agent 编排能力的学习型工程。

当前项目已经完成基础用户系统、会话持久化、模型选择和 Spring AI 模型调用骨架。后端会优先尝试通过 Spring AI `ChatModel` 调用真实模型；如果模型客户端不可用或上游调用失败，会返回可读的兜底提示，方便本地开发继续推进。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5.9、Maven、Spring Web、Validation、Actuator |
| AI | Spring AI 1.1.6、OpenAI Compatible ChatModel、Spring AI Alibaba Agent Framework |
| 数据层 | PostgreSQL、MyBatis-Plus 3.5.16、Spring JDBC |
| 登录态 | Session、Spring Session Data Redis、Redis、BCrypt |
| 预留能力 | PGVector、MCP Server WebMVC、RocketMQ |
| 前端 | Vue 3.5、Vite 6、marked、DOMPurify |
| 部署 | 前端 Docker + Nginx，后端 Spring Boot |

## 当前能力

### 用户认证

- 用户注册、登录、退出登录
- 获取当前登录用户
- 注销账号，需要再次输入密码确认
- 密码使用 BCrypt 哈希存储
- 登录态使用 Session，并由 Redis 承载
- 用户注销采用逻辑删除：`status = 0`
- 用户名只要求“有效用户”唯一，注销后原用户名可以重新注册

### AI 对话

- 前端模型选择
- 后端模型列表接口
- 通过 Spring AI `ChatModel` 调用 OpenAI 兼容模型供应商
- 当前默认配置面向硅基流动 API
- 模型不可用或调用失败时返回兜底说明
- 每次对话自动保存用户消息和 assistant 回复

### 会话管理

- 新消息自动创建会话
- 会话按登录用户隔离
- 查询当前用户历史会话列表
- 点击历史会话回放完整消息
- 根据首条用户消息生成会话标题
- 刷新页面后可通过 `/c/{conversationId}` 恢复会话
- 侧边栏支持历史会话搜索和折叠

### 前端体验

- 登录 / 注册一体页面
- 聊天主界面、模型下拉选择、历史会话列表
- `Ctrl + K` 聚焦会话搜索
- assistant 消息支持 Markdown 渲染
- Markdown HTML 使用 DOMPurify 清洗，避免直接渲染不可信内容
- 消息请求前端设置 45 秒超时，避免长时间卡死

## 项目结构

```text
SpringAI-Program/
├─ backend/                         # Spring Boot 后端模块
│  ├─ pom.xml                       # 后端依赖声明
│  └─ src/main/
│     ├─ java/com/yinbo/agent/
│     │  ├─ YinboAgentApplication.java
│     │  ├─ auth/                   # 注册、登录、Session、账号注销
│     │  │  ├─ dto/                 # 认证请求和响应对象
│     │  │  ├─ entity/              # 用户实体
│     │  │  ├─ mapper/              # 用户 Mapper
│     │  │  └─ session/             # Session 中保存的登录用户
│     │  ├─ chat/                   # 模型列表、聊天、会话持久化
│     │  │  ├─ dto/                 # 聊天请求、响应、会话 DTO
│     │  │  ├─ entity/              # 会话和消息实体
│     │  │  └─ mapper/              # 会话和消息 Mapper
│     │  ├─ common/                 # 业务异常、统一错误响应
│     │  └─ config/                 # Web、密码、模型、认证配置
│     └─ resources/
│        ├─ application.yml         # 应用配置和模型列表
│        └─ schema.sql              # 表结构与索引初始化
├─ frontend/                        # Vue 3 前端模块
│  ├─ src/
│  │  ├─ App.vue                    # 登录、聊天、侧边栏、会话回放主页面
│  │  ├─ main.js                    # Vue 入口
│  │  ├─ styles.css                 # 页面样式
│  │  └─ api/                       # 前端请求封装
│  ├─ public/                       # Logo 等静态资源
│  ├─ nginx/default.conf            # 生产环境 Nginx 配置
│  ├─ Dockerfile                    # 前端镜像构建
│  └─ vite.config.js                # 本地开发代理
├─ docs/
│  ├─ project-structure.md          # 更详细的项目结构说明
│  └─ prompts.md                    # 项目提示词记录
├─ local-secrets.example.yml        # 本地私密配置示例
├─ local-secrets.yml                # 本地私密配置，已被 gitignore 忽略
└─ pom.xml                          # Maven 聚合工程
```

更完整的分层说明见 [docs/project-structure.md](docs/project-structure.md)。

## 本地配置

项目通过 `application.yml` 引入本地私密配置：

```yml
spring:
  config:
    import: optional:file:./local-secrets.yml,optional:file:../local-secrets.yml
```

根目录提供了示例文件 [local-secrets.example.yml](local-secrets.example.yml)。本地开发时创建 `local-secrets.yml`，至少配置：

```yml
POSTGRES_USERNAME: your-postgres-username
POSTGRES_PASSWORD: your-postgres-password
REDIS_PASSWORD: your-redis-password
OPENAI_API_KEY: your-siliconflow-api-key
AUTH_SEED_ADMIN_USERNAME: admin
AUTH_SEED_ADMIN_PASSWORD: replace-with-a-dev-only-password
```

可选配置：

```yml
POSTGRES_URL: jdbc:postgresql://localhost:5432/yinbo_agent
REDIS_HOST: localhost
REDIS_PORT: 6379
OPENAI_BASE_URL: https://api.siliconflow.cn
OPENAI_CHAT_MODEL: deepseek-ai/DeepSeek-V4-Flash
ROCKETMQ_NAME_SERVER: localhost:9876
```

注意：`local-secrets.yml` 不应该提交到仓库。

## 快速启动

### 1. 准备中间件

本地至少需要：

```text
PostgreSQL: 5432
Redis: 6379
```

数据库默认连接到：

```text
jdbc:postgresql://localhost:5432/yinbo_agent
```

RocketMQ 当前已引入配置和依赖，但核心聊天链路暂未强依赖它；如果没有启动 RocketMQ，后续接入消息队列能力前需要再检查启动行为。

### 2. 启动后端

后端需要 Java 17。

```powershell
cd backend
mvn spring-boot:run
```

如果本机默认 Java 不是 17，可以临时指定：

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

### 3. 启动前端

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

## 接口概览

### 认证接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/register` | 注册，成功后自动登录 |
| `POST` | `/api/auth/login` | 登录 |
| `GET` | `/api/auth/me` | 获取当前登录用户 |
| `POST` | `/api/auth/logout` | 退出登录 |
| `POST` | `/api/auth/cancel` | 注销账号 |

### 聊天接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/models` | 查询可选模型列表 |
| `POST` | `/api/chat` | 发送聊天消息 |
| `GET` | `/api/conversations` | 查询当前用户会话列表 |
| `GET` | `/api/conversations/{conversationId}` | 查询指定会话详情 |

## 数据表

当前由 [backend/src/main/resources/schema.sql](backend/src/main/resources/schema.sql) 初始化三张核心表：

| 表 | 说明 |
| --- | --- |
| `auth_user` | 用户表，保存用户名、BCrypt 密码哈希、用户状态 |
| `chat_conversation` | 会话表，保存会话号、所属用户、标题、最近模型和最近消息时间 |
| `chat_message` | 消息表，保存会话内的 user / assistant 消息 |

`auth_user` 对用户名的唯一约束是部分唯一索引：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uk_auth_user_username_active
ON auth_user (username)
WHERE status = 1;
```

这意味着只有有效用户不能重名，注销后的用户名可以再次注册。

## 下一步建议

比较适合继续推进的路线：

1. 把 `PlaceholderChatService` 重命名为更准确的 `SpringAiChatService`
2. 接入流式响应，让前端逐字输出
3. 增加会话删除、重命名、分页和置顶
4. 引入 PGVector，完成第一版 RAG 知识库
5. 把工具调用能力接到 MCP Tool Server
6. 给 RocketMQ 找一个真实业务场景，比如异步记录模型调用日志
7. 增加测试用例，尤其是认证、会话归属校验和注销后重注册逻辑
