# 会话记忆压缩流程

这份文档说明会话上下文压缩的触发时机、数据水位线、自动压缩、手动压缩和 Prompt 组装规则。

## 设计目标

- `chat_message` 永远保存完整原始消息，不删除、不覆盖。
- `conversation_memory_summary` 只保存压缩摘要和已覆盖消息范围。
- Prompt 组装时使用“头部原文消息 + 历史摘要 + 最近窗口原文消息”。
- 自动压缩防止上下文超过模型预算，手动压缩允许用户主动整理会话。

## 核心数据结构

`conversation_memory_summary` 使用雪花 ID 作为消息水位线：

| 字段 | 说明 |
| --- | --- |
| `conversation_id` | 所属会话主键 |
| `user_id` | 所属用户 |
| `summary_content` | 压缩后的会话摘要 |
| `covered_start_message_id` | 摘要覆盖的第一条消息 ID |
| `covered_end_message_id` | 摘要覆盖的最后一条消息 ID |
| `source_message_count` | 当前摘要累计覆盖的原始消息数 |
| `summary_tokens` | 摘要 token 粗略估算 |
| `compression_model_id` | 执行压缩的模型 |
| `compression_version` | 压缩提示词版本 |
| `trigger_type` | `AUTO` 或 `MANUAL` |
| `status` | `ACTIVE` 或 `ARCHIVED` |

雪花 ID 只做水位线判断：

```sql
id > covered_end_message_id
```

不要用：

```sql
id - covered_end_message_id
```

因为雪花 ID 不是连续自增数量。

## 自动压缩触发

自动压缩发生在会话流水线中：

```text
prepare conversation
-> load full messages
-> persist current user message
-> prepare prompt memory
   -> 判断是否需要自动压缩
   -> 生成 promptConversationMessages
-> rewrite query
-> resolve intents
-> LLM / RAG / tools
```

触发阈值不是直接使用模型最大上下文，而是先扣掉输出、RAG、工具和安全余量：

```text
memoryBudget =
  contextMaxTokens
  - outputReserveTokens
  - ragReserveTokens
  - toolReserveTokens
  - safetyMarginTokens

memoryTokens >= memoryBudget * autoCompressThresholdRatio
```

当前默认值：

| 配置 | 默认值 |
| --- | ---: |
| `CHAT_MEMORY_CONTEXT_MAX_TOKENS` | `100000` |
| `CHAT_MEMORY_OUTPUT_RESERVE_TOKENS` | `8000` |
| `CHAT_MEMORY_RAG_RESERVE_TOKENS` | `12000` |
| `CHAT_MEMORY_TOOL_RESERVE_TOKENS` | `4000` |
| `CHAT_MEMORY_SAFETY_MARGIN_TOKENS` | `4000` |
| `CHAT_MEMORY_AUTO_COMPRESS_THRESHOLD_RATIO` | `0.9` |

默认记忆预算是 `72000`，硬触发点是 `64800`。

## 压缩范围

第一次压缩：

```text
头部 N 条原文消息
+ 中间可压缩消息
+ 最近窗口消息
```

实际只压缩中间段：

```text
可压缩消息 = 全量消息 - 头部保留 - 最近窗口
```

再次压缩：

```text
旧 summary
+ id > coveredEndMessageId
+ 不属于最近窗口的 message
=> 新 summary
=> coveredEndMessageId 往后推进
```

当前默认：

| 配置 | 默认值 | 说明 |
| --- | ---: | --- |
| `CHAT_MEMORY_HEAD_MESSAGE_COUNT` | `4` | 首轮对话锚点，保留原文 |
| `CHAT_MEMORY_RECENT_WINDOW_MESSAGE_COUNT` | `20` | 最近窗口，保留原文 |
| `CHAT_MEMORY_MIN_COMPRESS_MESSAGE_COUNT` | `8` | 少于这个数量不压缩 |
| `CHAT_MEMORY_COMPRESSION_WINDOW_TOKENS` | `24000` | 单次压缩窗口预算 |
| `CHAT_MEMORY_MAX_SUMMARY_TOKENS` | `4000` | 期望摘要上限 |

## Prompt 组装

如果没有活跃摘要：

```text
system prompt
+ 全量 chat_message
+ current user message
```

如果有活跃摘要：

```text
system prompt
+ id < coveredStartMessageId 的头部原文消息
+ system message: 历史会话摘要
+ 分割线: ----- 上下文已压缩，以下为最近未压缩对话 -----
+ id > coveredEndMessageId 的原文消息
+ current user message
```

模型看到的是一个轻量上下文，详情页看到的仍然是完整 `chat_message`。

## 手动压缩

接口：

```text
POST /api/conversations/{conversationId}/memory/compress
```

流程：

```text
校验登录态
-> 校验会话属于当前用户
-> 如果当前 service 实例正在流式输出该会话，返回 409
-> 如果当前 service 实例正在压缩该会话，返回 409
-> 读取完整 chat_message
-> 查询 ACTIVE summary
-> 计算可压缩范围
-> 调用 LLM 生成新 summary
-> 插入新的 ACTIVE summary
-> 归档旧 summary
-> 返回 coveredEndMessageId 等信息
```

返回字段：

| 字段 | 说明 |
| --- | --- |
| `conversationId` | 会话业务 ID |
| `compressed` | 是否真的执行了压缩 |
| `triggerType` | `MANUAL` |
| `coveredStartMessageId` | 摘要覆盖起点 |
| `coveredEndMessageId` | 摘要覆盖终点 |
| `sourceMessageCount` | 摘要累计覆盖消息数 |
| `summaryTokens` | 摘要 token 粗略估算 |
| `message` | 结果说明 |

## 当前实现边界

- 自动压缩是硬阈值同步压缩，暂未做响应结束后的软阈值异步压缩。
- SSE `start` 事件和会话详情会返回当前 ACTIVE summary，前端只按这个真实水位线恢复 token 圆环和压缩分割线。
- 流式输出状态登记器和压缩会话级锁是当前 service 实例内存状态；多实例部署时需要换成 Redis 锁或数据库锁。
- token 计算是粗略估算，后续可以接模型 tokenizer 或 ai-infra token 计数接口。
- 压缩模型复用当前会话模型，后续可以单独配置低成本压缩模型。
