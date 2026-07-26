# MCP 工具服务

`mcp-server` 是独立的 Spring Boot 工具服务，当前先承载物流轨迹查询工具。backend 不直接在业务进程里写工具逻辑，而是通过 HTTP 远程调用 MCP 服务，后续可以继续把订单查询、天气、日程等工具迁移到这个服务里。

当前版本是项目内部的 HTTP 工具服务形态，用来先跑通“意图节点 -> 远程工具 -> 会话回复”的工程链路；如果后续要兼容标准 MCP 的 JSON-RPC / SSE 协议，可以在该服务上继续增加协议适配层。

## 模块边界

```text
backend
  -> McpToolClient
  -> http://localhost:8083/internal/mcp/tools/{toolId}/call
  -> mcp-server
     -> LogisticsTrackingToolService
     -> LogisticsProvider
     -> Kuaidi100LogisticsProvider
     -> 快递100实时查询 API
```

当前版本重点先跑通 Agent 工具链路，并通过快递100查询真实物流轨迹；服务仍然只在项目内部调用，不暴露给公网。

## 启动方式

```powershell
cd mcp-server
mvn spring-boot:run
```

默认地址：

```text
http://localhost:8083
```

backend 默认通过下面配置调用：

```yml
YINBO_MCP_SERVER_URI: http://localhost:8083
MCP_REQUEST_TIMEOUT: 10s
```

物流供应商默认使用快递100，需要在 `local-secrets.yml` 配置授权信息：

```yml
LOGISTICS_PROVIDER: kuaidi100
LOGISTICS_REQUEST_TIMEOUT: 10s
KUAIDI100_KEY: your-kuaidi100-key
KUAIDI100_CUSTOMER: your-kuaidi100-customer
KUAIDI100_QUERY_URL: https://poll.kuaidi100.com/poll/query.do
KUAIDI100_AUTO_NUMBER_URL: http://www.kuaidi100.com/autonumber/auto
```

如果没有配置 `KUAIDI100_KEY` 或 `KUAIDI100_CUSTOMER`，服务可以启动，但查询时会返回“物流查询服务还没有配置快递100授权信息”。

## 接口

### 查询工具列表

```http
GET /internal/mcp/tools
```

返回示例：

```json
[
  {
    "toolId": "logistics-tracking-tool",
    "name": "物流轨迹查询",
    "description": "根据快递单号查询包裹当前状态、最新位置和轨迹事件。",
    "requiredArguments": ["trackingNo"]
  }
]
```

### 调用物流轨迹工具

```http
POST /internal/mcp/tools/logistics-tracking-tool/call
Content-Type: application/json
```

请求：

```json
{
  "query": "帮我查一下 SF1234567890 的快递",
  "conversationId": "conv-1",
  "userId": 1,
  "arguments": {}
}
```

有单号时会调用快递100查询真实轨迹；缺少单号时返回追问：

```json
{
  "toolId": "logistics-tracking-tool",
  "success": true,
  "needClarification": true,
  "message": "请提供快递单号，我才能帮你查询当前物流轨迹。",
  "data": {},
  "errorMessage": null,
  "durationMs": 0
}
```

如果快递公司需要手机号校验，例如顺丰或中通，缺少手机号时会返回追问：

```json
{
  "toolId": "logistics-tracking-tool",
  "success": true,
  "needClarification": true,
  "message": "该快递公司查询需要收/寄件手机号或后四位，请补充手机号后再查。",
  "data": {},
  "errorMessage": null,
  "durationMs": 0
}
```

工具也支持从 `arguments` 显式传参：

```json
{
  "query": "查一下快递",
  "conversationId": "conv-1",
  "userId": 1,
  "arguments": {
    "trackingNo": "YT1234567890",
    "carrierCode": "yuantong",
    "phone": "13800138000"
  }
}
```

## backend 接入点

| 文件 | 作用 |
| --- | --- |
| `config/McpProperties.java` | 配置 MCP 服务地址和超时 |
| `infra/mcp/McpToolClient.java` | 远程调用 MCP 工具 |
| `infra/mcp/dto/*` | backend 侧请求/响应 DTO |
| `chat/flow/retrieval/RetrievalExecuteService.java` | 根据 MCP 意图节点调用工具 |

调用条件来自意图树节点：

```text
node.kind = MCP
node.mcpToolId = logistics-tracking-tool
```

用户问 `快递到哪了` 时，如果没有单号，工具结果会直接让用户补单号；用户问 `查一下 YT1234567890 的快递` 时，会调用快递100实时查询接口。

## 快递100适配器

| 类 | 作用 |
| --- | --- |
| `config/LogisticsProperties.java` | 读取物流供应商配置和快递100授权信息 |
| `logistics/LogisticsProvider.java` | 物流供应商统一接口 |
| `logistics/kuaidi100/Kuaidi100LogisticsProvider.java` | 快递100真实查询适配器 |
| `logistics/LogisticsQueryRequest.java` | 统一查询入参 |
| `logistics/LogisticsQueryResult.java` | 统一查询结果 |

快递100实时查询接口要求提交 `com`、`num`、`customer`、`sign` 和 `param`，其中 `sign` 按 `param + key + customer` 做 MD5 并转大写。智能单号识别接口使用 `num + key` 自动识别快递公司编码。

当前工具会优先使用 `arguments.carrierCode` / `arguments.com`，其次根据 query 中的“顺丰、圆通、中通”等词或单号前缀推断，最后调用快递100智能单号识别。

参考文档：

- [快递100实时快递查询接口](https://api.kuaidi100.com/document/5f0ffb5ebc8da837cbd8aefc)
- [快递100智能单号识别接口](https://api.kuaidi100.com/document/5f1106542977d50a94e10241)

## 后续演进

1. 将 `arguments.trackingNo`、`carrierCode`、`phone` 的参数抽取独立成一个参数抽取阶段。
2. 工具结果不要直接返回，改为进入 `PromptAssemblyService.buildGroundedRequest(...)`，由 LLM 汇总成自然语言。
3. 给 Trace 增加工具调用明细：toolId、入参、出参、耗时、成功状态。
4. 增加快递鸟或内部订单物流接口实现，通过 `LogisticsProvider` 切换供应商。
