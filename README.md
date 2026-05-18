# 音波AI agent 智能助手平台

这是一个用于边做边学的 AI Agent 项目，目前已经从“纯聊天骨架”推进到了“聊天骨架 + 基础用户系统”的阶段。

- 后端：Spring Boot 3.5.x + Java 17 + Maven + Spring AI 1.1.x + Spring AI Alibaba
- 前端：Vue 3 + Vite，生产环境使用 Nginx 部署
- 数据与中间件：PostgreSQL + Redis + RocketMQ
- 当前认证方案：MyBatis-Plus + Session + Redis + BCrypt

## 当前进度

当前已经完成这些能力：

- 前端聊天界面：模型选择、消息列表、输入区
- 后端聊天接口：模型列表接口、聊天接口占位实现
- 用户认证模块：
  - 登录
  - 注册
  - 退出登录
  - 注销账号
- 密码安全：
  - 数据库存储的是 BCrypt 哈希值
  - 注销账号时需要再次输入密码确认
- 用户状态设计：
  - `status = 1` 表示有效用户
  - `status = 0` 表示已注销用户
  - 注销后为逻辑删除，不做物理删除
  - 用户名仅对有效用户唯一，注销后原用户名可以重新注册

真实 LLM 调用暂未完全展开，当前聊天服务仍以现有 `ChatService` 骨架为主，后续可以继续替换成真实模型调用、流式响应和会话持久化能力。

## 当前目录重点

```text
SpringAI-Program/
├─ backend/
│  ├─ src/main/java/com/yinbo/agent/
│  │  ├─ auth/             # 登录、注册、注销、会话校验
│  │  ├─ chat/             # 聊天接口与占位服务
│  │  ├─ common/           # 统一异常与通用响应
│  │  └─ config/           # 模型配置、密码编码、跨域配置
│  └─ src/main/resources/
│     ├─ application.yml
│     └─ schema.sql        # 用户表结构与索引初始化
├─ frontend/
│  └─ src/
│     ├─ App.vue           # 登录/注册/聊天一体页面
│     ├─ api/authApi.js    # 认证接口封装
│     └─ api/chatApi.js    # 聊天接口封装
└─ docs/
   ├─ project-structure.md
   └─ prompts.md
```

更详细的结构说明见 [项目结构文档](docs/project-structure.md)。

## IDEA 打开方式

推荐直接打开仓库根目录：

```text
SpringAI-Program/
```

打开后确认：

1. `Project SDK` 为 Java 17
2. Maven 面板里能看到根项目和 `backend` 模块

如果 IDEA 没自动导入，可以在根目录的 [pom.xml](pom.xml) 上点击 `Load Maven Project`。

## 本地配置文件

项目根目录支持一个不入库的本地配置文件：

```text
local-secrets.yml
```

这个文件目前用于保存：

- PostgreSQL 用户名和密码
- Redis 密码
- 硅基流动 API Key
- 本地开发用种子管理员账号

仓库里提供了示例文件 [local-secrets.example.yml](local-secrets.example.yml)。

一个可参考的开发配置如下：

```yml
POSTGRES_USERNAME: postgres
POSTGRES_PASSWORD: postgres
REDIS_PASSWORD: your-redis-password
OPENAI_API_KEY: your-siliconflow-api-key
AUTH_SEED_ADMIN_USERNAME: admin
AUTH_SEED_ADMIN_PASSWORD: admin
```

## 快速启动

### 1. 准备中间件

确保本地这些服务可用：

```text
PostgreSQL: 5432
Redis: 6379
RocketMQ NameServer: 9876
```

其中登录、注册、会话依赖 PostgreSQL 和 Redis。

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认访问：

```text
http://localhost:5173
```

### 3. 启动后端

后端需要 Java 17。如果当前机器默认 `java` 不是 Java 17，可以在 PowerShell 临时切换：

```powershell
$env:JAVA_HOME="C:\Users\35575\.jdks\ms-17.0.17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd backend
mvn spring-boot:run
```

后端默认端口：

```text
http://localhost:8080
```

## 当前可用功能说明

### 登录

- 使用用户名 + 密码登录
- 登录成功后创建 Session
- Session 存入 Redis

### 注册

- 使用用户名 + 密码直接注册
- 用户名对有效用户唯一
- 注册成功后自动登录

### 注销账号

- 需要再次输入当前密码
- 注销后不是物理删除，而是将 `status` 从 `1` 改成 `0`
- 注销后当前用户名可以再次注册使用

### 测试账号

如果你在 `local-secrets.yml` 中配置了：

```yml
AUTH_SEED_ADMIN_USERNAME: admin
AUTH_SEED_ADMIN_PASSWORD: admin
```

那么应用启动时会自动补一个本地开发用管理员账号：

```text
用户名：admin
密码：admin
```

## 下一步建议

接下来比较自然的演进路线是：

1. 接入真实模型供应商并替换占位聊天实现
2. 支持流式输出
3. 引入聊天会话表和消息表
4. 把当前登录用户与聊天会话绑定
5. 引入 PGVector 做 RAG
6. 加入更细的权限控制和账号资料管理
7. 把工具能力逐步接入 MCP Tool Server
