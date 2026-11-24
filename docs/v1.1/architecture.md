# 架构总览

## 目标

LumiRose 的架构由两个约束共同决定：

- **学习导向**：核心循环只有一个，分层能顺着代码走完一次 run。
- **生产级目标**：失败原子、错误可预测、可取消、可重试、持久化可替换、文档完整。

## 分层

```text
io.github.xxxyjade.lumirose
├── agent/            LumiRoseAgent
├── client/           LumiRoseClient + SseAccumulator
├── common/
│   ├── exception/
│   ├── result/       LumiRoseResult
│   └── enums/        FinishReason、MessageRole
├── model/
│   ├── entity/       Conversation、Message、ToolCall
│   ├── data/         ConversationDO、MessageDO、ToolCallDO
│   ├── convert/      entity ↔ DO
│   └── dto/chat/     ChatCompletion*
├── repository/       ConversationRepository + impl
└── tool/             Tool、ToolContext、内置工具
```

## 依赖方向

```text
common.exception   ← 无
common.result      ← 无
common.enums       ← 无
model.entity       → common.enums
model.data         ← 无
model.dto.chat     → common.enums (+ Jackson)
model.convert      → model.entity + model.data
repository         → model.entity + model.data + model.convert
client             → model.dto.chat + common.exception
agent              → model.entity + model.dto.chat + repository + client + tool + common
tool               → common.exception
```

约束：

- `client` 不依赖 `agent` / `repository`；
- `model.entity` 不依赖 `model.data` / `model.dto`；
- `agent` 不直接使用 `model.data` / `model.convert`；
- `tool` 不依赖模型层。

## 三套模型

| 层 | 示例 | 用途 |
|---|---|---|
| entity | `model.entity.Conversation`、`Message`、`ToolCall` | 业务逻辑、仓储 SPI |
| DTO | `model.dto.chat.ChatCompletionRequest`、`Response`、`Message` | 序列化/反序列化 DeepSeek 协议 |
| DO | `model.data.ConversationDO`、`MessageDO`、`ToolCallDO` | 关系型数据库映射 |

DTO 不进入 repository，DO 不进入 agent；Converter 只做字段映射和角色校验。

## 一次运行的时序

```text
LumiRoseAgent.run/streamRun
  -> repository.load(id)                   // active + contextTokens
  -> 压缩判断（只在开始时一次）
  -> client.chatCompletion(request, sink)  // 始终 SSE
  -> 工具循环（tool_calls -> 执行 -> assistant/tool 加入 pending -> 重建 request -> 再请求）
  -> repository.append 或 compact
  -> LumiRoseResult
```

`run` 的 sink 是空实现（丢弃增量）；`streamRun` 的 sink 把增量推进队列，并由虚拟线程执行。

## 流式

- 只有 SSE，没有非流式 JSON 路径；
- 客户端负责 SSE 语法、装配、usage、tool_calls 分片；
- `LumiRoseResult.deltas()` 是唯一消费入口；
- `streamRun` 冷启动：第一次迭代才启动线程；
- 队列无界；调用方消费或取消。

详见 [流式输出](streaming.md)。

## 压缩

- `contextTokens > window × 0.65` 时触发；
- 压缩输入是 active 消息，不含 system、不含本次新 user；
- 摘要作为 assistant 消息保存；
- `compact` 原子地停用旧 active、插入摘要和新消息；
- 失败 3005，不写。

详见 [上下文压缩](context-compression.md)。

## 持久化

- SPI 只有 `load` / `append` / `compact`；
- `append` / `compact` 在会话行不存在时创建；
- 无进程内锁、无 version；
- 逻辑截断，不物理删除；
- 内存实现 + 未来 JDBC。

详见 [持久化](persistence.md)。

## 重试与取消

- 只对 2003、429、5xx 重试；
- 指数退避，无抖动；
- 增量交付之后不再重试；`run` 不交付增量，因此断流后可重试；
- 已交付后断流抛 `2004`，由调用方决定是否重开；
- 协作式取消，提交前检查；
- 取消/失败不写。

## 为什么没有额外的抽象

- 没有 `TurnTransport` / `DeltaSink` 策略层：只有一套 SSE，循环不需要模式分支。
- 没有独立的 `LumiRoseAgentLoop` 类：循环是 `LumiRoseAgent` 的私有方法，减少跨类跳转。
- 没有 `RunResult` / `MetricsRecorder` / `runResult()`：结果统一为 `LumiRoseResult`，日志用 SLF4J。
- 没有进程内会话锁：并发由调用方和仓储承担。

这些取舍见 [关键设计决策](design-decisions.md)。
