# 音波AI agent 智能助手平台

这是一个用于边做边学的 AI Agent 项目骨架。

- 后端：Spring Boot 3.5.x + Java 17 + Maven + Spring AI 1.1.x + Spring AI Alibaba
- 前端：Vue 3 + Vite，生产环境使用 Nginx 部署
- 基础设施预留：PostgreSQL + PGVector、Redis、RocketMQ、MCP Tool Server

## 当前阶段

已完成最小可运行骨架：

- 前端页面：模型选择 + 对话界面，风格尽量贴近 ChatGPT 网页版的简洁体验
- 后端接口：模型列表接口、聊天接口占位实现
- 文档：项目结构说明、提示词集中维护文档

真实 LLM 调用暂未接入。等你确定 API key、供应商和模型列表后，只需要替换后端 `ChatService` 的实现。

## IDEA 打开方式

为了让 IntelliJ IDEA 自动识别 Maven 项目结构，现在仓库根目录已经提供聚合 `pom.xml`。

推荐直接打开仓库根目录：

```text
SpringAI-Program/
```

打开后确认两件事：

1. `Project SDK` 设为 Java 17
2. Maven 面板里能看到根项目和 `backend` 模块

如果 IDEA 仍然没有自动导入，可以在根目录的 [pom.xml](pom.xml) 上点击 `Load Maven Project`。

如果你之前已经用错误结构打开过项目，建议再补一步：

1. 删除 IDEA 已生成的临时模块配置
2. 在 IDEA 中重新从根目录 [pom.xml](pom.xml) 导入

这样通常就能把 `backend/src/main/java` 正确识别为 Maven 源码目录，并把 `jakarta.*` 依赖同步进来。

## 快速启动

### 前端

```bash
cd frontend
npm install
npm run dev
```

默认访问：

```text
http://localhost:5173
```

### 后端

后端需要 Java 17。当前机器默认 `java` 指向 JDK 8，如果本机已有 `C:\Users\35575\.jdks\ms-17.0.17`，可以在 PowerShell 临时切换：

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

更多目录说明见 [项目结构文档](docs/project-structure.md)。
