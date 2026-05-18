# 项目结构

```text
SpringAI-Program/
├─ backend/                               # Spring Boot 后端工程
│  ├─ pom.xml                             # Maven 依赖与版本管理
│  └─ src/
│     └─ main/
│        ├─ java/
│        │  └─ com/yinbo/agent/
│        │     ├─ YinboAgentApplication.java
│        │     ├─ auth/                   # 登录、注册、注销、会话校验
│        │     │  ├─ dto/                # 认证请求与响应对象
│        │     │  ├─ entity/             # 用户实体
│        │     │  ├─ mapper/             # MyBatis-Plus Mapper
│        │     │  ├─ session/            # Session 中保存的登录用户对象
│        │     │  ├─ AuthController.java
│        │     │  ├─ AuthService.java
│        │     │  ├─ SessionAuthService.java
│        │     │  ├─ LoginInterceptor.java
│        │     │  └─ AuthBootstrapRunner.java
│        │     ├─ chat/                  # 对话 API、DTO、服务接口、会话持久化
│        │     ├─ common/                # 通用异常、错误响应
│        │     └─ config/                # 配置类
│        │        ├─ AiModelProperties.java
│        │        ├─ AuthProperties.java
│        │        ├─ MybatisPlusAutoFillConfig.java
│        │        ├─ PasswordConfig.java
│        │        └─ WebConfig.java
│        └─ resources/
│           ├─ application.yml
│           └─ schema.sql                # 用户/会话/消息表与索引初始化脚本
├─ frontend/                              # Vue 3 前端工程
│  ├─ src/
│  │  ├─ App.vue                         # 登录、注册、聊天一体页面
│  │  ├─ main.js                         # Vue 入口
│  │  ├─ styles.css                      # 页面样式
│  │  └─ api/
│  │     ├─ authApi.js                   # 认证接口封装
│  │     └─ chatApi.js                   # 聊天接口封装
│  ├─ nginx/default.conf                 # Nginx 静态部署与 API 代理配置
│  ├─ Dockerfile                         # 前端生产镜像构建
│  └─ vite.config.js                     # 本地开发代理配置
├─ docs/
│  ├─ project-structure.md               # 当前文档
│  └─ prompts.md                         # 项目提示词集中维护
├─ local-secrets.example.yml             # 本地开发配置示例
└─ pom.xml                               # 聚合工程根 POM
```

## 后端分层

### 1. `auth/`

认证模块，当前已经支持：

- 注册
- 登录
- 获取当前登录用户
- 退出登录
- 逻辑注销账号

核心类说明：

- `AuthController`：认证接口入口，暴露 `/api/auth/*`
- `AuthService`：认证服务抽象
- `SessionAuthService`：基于 Session + Redis 的认证实现
- `LoginInterceptor`：校验受保护接口的登录状态与账号状态
- `AuthBootstrapRunner`：启动时按配置注入本地开发种子账号

子目录说明：

- `dto/`：认证模块的请求和响应对象
- `entity/`：当前用户实体 `AuthUser`
- `mapper/`：MyBatis-Plus 数据访问层
- `session/`：Session 中保存的轻量登录用户对象

### 2. `chat/`

聊天模块，当前已经包含会话持久化基础能力：

- `ChatController`：聊天接口入口
- `ChatService`：聊天服务抽象
- `PlaceholderChatService`：当前占位实现
- `entity/`：聊天会话、聊天消息实体
- `mapper/`：聊天会话、消息数据访问层
- `dto/`：聊天请求与响应对象

当前已支持：

- 发送消息时自动创建会话
- 将用户消息和 assistant 回复写入数据库
- 查询当前用户的会话列表
- 查询指定会话的消息历史

后续接入真实模型时，主要还是从这里继续扩展。

### 3. `common/`

通用支撑模块：

- `BusinessException`：业务异常
- `ApiErrorResponse`：统一错误响应
- `GlobalExceptionHandler`：全局异常处理

### 4. `config/`

配置相关模块：

- `AiModelProperties`：读取模型列表配置
- `AuthProperties`：读取种子管理员账号配置
- `PasswordConfig`：注册 `BCryptPasswordEncoder`
- `MybatisPlusAutoFillConfig`：自动填充创建和更新时间
- `WebConfig`：跨域配置、登录拦截器注册

## 数据设计

当前用户表是 `auth_user`，核心字段语义如下：

- `id`：主键，雪花算法生成
- `username`：用户名
- `password_hash`：BCrypt 哈希密码
- `display_name`：显示名称
- `status`：用户状态
  - `1`：有效
  - `0`：已注销
- `last_login_at`：最近登录时间
- `created_at` / `updated_at`：创建与更新时间

当前唯一性规则不是“全表用户名唯一”，而是：

- **仅对 `status = 1` 的有效用户要求用户名唯一**
- 注销后原用户名可以重新注册

这依赖 `schema.sql` 中的部分唯一索引实现。

当前聊天相关还新增了两张表：

- `chat_conversation`
  - `conversation_no`：对外暴露的会话号，前后端通过它关联
  - `user_id`：会话所属用户
  - `title`：会话标题，首次从用户消息截取
  - `model_id`：最近一次使用的模型
  - `last_message_at`：最近一条消息时间
- `chat_message`
  - `conversation_id`：所属会话主键
  - `user_id`：所属用户
  - `role`：消息角色，当前主要是 `user` / `assistant`
  - `content`：消息正文
  - `model_id`：本条消息对应模型

## 前端分层

### 1. `App.vue`

当前前端主界面已经不只是聊天页，而是一个一体化页面，包含：

- 登录 / 注册切换
- 当前登录状态展示
- 注销账号确认
- 模型选择
- 历史会话列表
- 点击回放历史消息
- 聊天消息列表
- 输入与发送区域

### 2. `api/authApi.js`

封装认证接口：

- `/api/auth/register`
- `/api/auth/login`
- `/api/auth/me`
- `/api/auth/logout`
- `/api/auth/cancel`

### 3. `api/chatApi.js`

封装聊天接口：

- `/api/models`
- `/api/chat`
- `/api/conversations`
- `/api/conversations/{conversationId}`

### 4. `styles.css`

集中维护页面样式，包括：

- 登录/注册区域样式
- 聊天区域样式
- 侧边栏样式
- 历史会话列表样式
- 注销确认按钮与输入框样式

## 当前开发链路

本地推荐开发方式：

- 前端：`5173`
- 后端：`8080`
- 前端通过 Vite 代理访问后端 `/api`

这样开发时不需要额外处理复杂跨域问题。

## 下一步建议

结合当前结构，后续最自然的迭代方向是：

1. 接入真实模型调用
2. 支持流式响应
3. 增加会话重命名、删除和分页查询
4. 支持会话搜索与最近访问排序
5. 引入 PGVector 做 RAG
6. 增加用户资料、密码修改、权限控制
7. 把工具能力逐步接入 MCP Tool Server
