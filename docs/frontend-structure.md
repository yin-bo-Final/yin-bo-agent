# 前端项目结构

这份文档专门给前端页面、后台管理 UI 和样式改动使用。样式细则另见 [frontend-style-guide.md](frontend-style-guide.md)。

## 目录总览

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
   ├─ api/
   │  ├─ adminApi.js
   │  ├─ authApi.js
   │  ├─ chatApi.js
   │  └─ http.js
   ├─ components/
   │  └─ GlobalErrorToasts.vue
   └─ pages/
      ├─ AdminPage.vue
      ├─ AuthPage.vue
      └─ ConversationPage.vue
```

## `App.vue`

顶层状态分发。

- 启动时请求 `/api/auth/me`
- 判断当前路径是认证页、会话页还是后台页
- 非管理员访问 `/admin` 时回到会话页
- 维护当前用户状态

当前前端暂时没有 Vue Router，使用路径解析和 `window.history` 维护页面状态。

## `pages/AuthPage.vue`

登录和注册页。

- 登录表单
- 注册表单
- 表单错误展示
- 成功后通知 `App.vue`

相关接口：

```text
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/me
```

## `pages/ConversationPage.vue`

会话主界面。

- 模型选择
- 新建会话
- 普通聊天和流式聊天
- 流式聊天会识别 SSE 的 `done` / `error` 事件，断流但没有完成事件时会显示中断提示
- 会话列表、搜索、回放
- 会话置顶、取消置顶、删除
- 用户菜单
- 管理员显示“后台管理”入口
- 侧边栏展开和折叠

主要接口：

```text
GET    /api/models
POST   /api/chat
POST   /api/chat/stream
GET    /api/conversations
GET    /api/conversations/{conversationId}
DELETE /api/conversations/{conversationId}
```

## `pages/AdminPage.vue`

后台管理页，目前包含 Dashboard、知识库管理和失败任务管理。

- 后台侧边栏和折叠状态
- Dashboard 指标
- 知识库列表、新建、编辑、删除
- 文档列表、上传、URL 录入、分块、重新分块、重建向量、详情、删除
- 分块列表、查看、编辑、删除、启用、禁用、批量操作
- 失败任务列表、失败原因查看、重试次数展示和手动重试
- 失败任务支持删除，便于清理后台噪声数据
- 自定义弹窗、下拉栏、tooltip
- 根据 `/admin/knowledge/...` 和 `/admin/tasks/failed` 解析内部视图
- 文档处于 `UPLOADING` 或 `PROCESSING` 时轮询刷新

后台路由：

```text
/admin
/admin/knowledge
/admin/knowledge/{knowledgeBaseId}
/admin/knowledge/{knowledgeBaseId}/docs/{documentId}
/admin/tasks/failed
```

后台主要接口：

```text
GET    /api/admin/dashboard
GET    /api/admin/knowledge/overview
GET    /api/admin/knowledge/bases
POST   /api/admin/knowledge/bases
PATCH  /api/admin/knowledge/bases/{knowledgeBaseId}
DELETE /api/admin/knowledge/bases/{knowledgeBaseId}
POST   /api/admin/knowledge/bases/{knowledgeBaseId}/documents/upload
POST   /api/admin/knowledge/bases/{knowledgeBaseId}/documents/url
POST   /api/admin/knowledge/documents/{documentId}/rechunk
POST   /api/admin/knowledge/documents/{documentId}/vectors/rebuild
GET    /api/admin/knowledge/documents/{documentId}/chunks
PATCH  /api/admin/knowledge/chunks/{chunkId}
PATCH  /api/admin/knowledge/chunks/{chunkId}/enabled
DELETE /api/admin/knowledge/chunks/{chunkId}
GET    /api/admin/ingestion/tasks/failed
POST   /api/admin/ingestion/tasks/{taskId}/retry
DELETE /api/admin/ingestion/tasks/{taskId}
```

## `api/`

| 文件 | 说明 |
| --- | --- |
| `authApi.js` | 注册、登录、当前用户、退出、注销 |
| `chatApi.js` | 模型列表、聊天、流式聊天、会话管理 |
| `adminApi.js` | Dashboard、知识库、文档上传、分块、失败任务管理 |
| `http.js` | 统一解析响应和错误，429 会触发全局错误弹窗 |

所有需要登录态的请求都带 `credentials: 'include'`。

请求错误处理要优先读取后端或 gateway 返回的 `message` 字段。后端业务失败通常由 `BusinessException` 统一返回；gateway 限流失败返回 `429` 时，`GlobalErrorToasts` 会展示居中的全局错误弹窗。

开发环境中，`vite.config.js` 会把 `/api` 代理到 gateway 默认地址 `http://localhost:8081`。部署时，`nginx/default.conf` 也把 `/api/` 转发给 `gateway:8081`，再由 gateway 转发到后端业务服务。Nginx 的 `client_max_body_size` 默认设置为 `220m`，避免 200MB 附近的上传在进入 gateway 前被 Nginx 默认 1MB 限制拦截。

## `styles.css`

当前前端样式集中在一个文件里。继续改 UI 前先读 [frontend-style-guide.md](frontend-style-guide.md)。

大致分区：

```text
全局字体和背景
认证页
聊天布局
会话侧边栏和折叠 rail
消息列表和 Markdown
模型选择浮层
用户菜单和确认弹窗
后台布局
知识库 / 文档 / 分块表格
通用按钮、弹窗、状态、下拉栏、tooltip
响应式规则
动画 keyframes
```

## UI 约定

- 后台界面保持项目自己的灰色工程风格，不照搬外部截图。
- 按钮、弹窗、下拉栏、tooltip 优先复用现有类名和交互。
- tooltip 只在必要场景使用，例如导航栏折叠后图标悬停，或表格内容被省略时展示完整内容。
- 文档管理、知识库管理、分块管理都要保持统一的按钮高度、边框、hover 动效。
- 不要为了验证每个小改动都跑 `npm run build` 或 `npm run dev`，除非本次改动确实需要前端运行验证。

## 常见改动入口

| 要改的内容 | 优先看这里 |
| --- | --- |
| 登录注册页面 | `pages/AuthPage.vue`、`api/authApi.js` |
| 会话页面 | `pages/ConversationPage.vue`、`api/chatApi.js` |
| 后台管理页面 | `pages/AdminPage.vue`、`api/adminApi.js` |
| API 代理 | `vite.config.js` |
| 前端部署 | `Dockerfile`、`nginx/default.conf` |
| 通用样式 | `src/styles.css` |
