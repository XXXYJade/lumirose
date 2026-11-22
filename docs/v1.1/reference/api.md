# API 参考

本页只列公开类型与稳定方法。未列出的类（如 `SseAccumulator`）是实现细节，不构成契约。完整行为见 [v1.1 需求](../requirements/v1.1.md)。

## 包结构

```text
io.github.xxxyjade.lumirose
├── agent            LumiRoseAgent
├── client           LumiRoseClient
├── common.exception LumiRoseException、五个子类、LumiRoseErrorCode
├── common.result    LumiRoseResult
├── common.enums     MessageRole、FinishReason
├── model.entity     Conversation、Message、ToolCall
├── model.dto.chat   ChatCompletionRequest / Response / Message / Tool / ToolCall / Usage
├── model.data         ConversationDO、MessageDO、ToolCallDO（持久化支撑）
├── model.convert    Converter（持久化支撑）
├── repository       ConversationRepository、InMemoryConversationRepository
└── tool             Tool、ToolContext、ToolParameters、EmptyParameters、tool.impl.*
```

`model.data` 与 `model.convert` 是 public 的，但只服务仓储实现；业务代码不应使用。

## LumiRoseClient

```java
LumiRoseClient client = LumiRoseClient.builder()
        .baseUrl("https://api.deepseek.com")
        .apiKey(System.getenv("DEEPSEEK_API_KEY"))
        .model("deepseek-chat")
        .maxTokens(4096)      // 可选
        .build();
```

| Builder 方法 | 类型 | 必填 | 默认 |
|---|---|---|---|
| `baseUrl(String)` | String | 是 | — |
| `apiKey(String)` | String | 是 | — |
| `model(String)` | String | 是 | — |
| `maxTokens(Integer)` | Integer | 否 | 不发送 |

```java
ChatCompletionResponse chatCompletion(ChatCompletionRequest request);

ChatCompletionResponse chatCompletion(ChatCompletionRequest request,
                                      Consumer<String> onContent);
```

- 两个重载都发送 `stream: true`；`onContent` 只接收正文增量。
- 返回值是装配后的完整响应：`content`、`reasoningContent`、`toolCalls`、`finishReason`、`usage`。
- 无 consumer 的重载等价于传空实现。
- 请求级 `request.maxTokens` 优先于 client 级。
- 客户端只提供 SSE，没有非流式方法。
- 超时固定为连接 10s、写 30s、读 120s，无整体超时。

## LumiRoseAgent

```java
LumiRoseAgent agent = LumiRoseAgent.builder()
        .client(client)
        .toolContext(tools)
        .repository(repository)
        .systemMessage("你是一个简洁的智能体")
        .maxTurns(10)
        .maxRetries(2)
        .retryBaseDelayMillis(500)
        .retryMaxDelayMillis(8000)
        .contextWindowTokens(128_000)
        .contextCompressionThreshold(0.65)
        .build();
```

| Builder 方法 | 必填 | 默认 | 约束 |
|---|---|---|---|
| `client(LumiRoseClient)` | 是 | — | 非 null |
| `toolContext(ToolContext)` | 是 | — | 非 null |
| `repository(ConversationRepository)` | 是 | — | 非 null |
| `systemMessage(String)` | 否 | `You are a helpful assistant!` | null/空白表示不发送 |
| `maxTurns(int)` | 否 | 10 | ≥ 1 |
| `maxRetries(int)` | 否 | 0 | ≥ 0 |
| `retryBaseDelayMillis(long)` | 否 | 500 | ≥ 0 |
| `retryMaxDelayMillis(long)` | 否 | 8000 | ≥ base |
| `contextWindowTokens(Integer)` | 否 | null | null 关闭压缩；否则 > 0 |
| `contextCompressionThreshold(double)` | 否 | 0.65 | `(0, 1)` |

摘要 `max_tokens` 不在 builder 上；压缩时按 `min(2048, max(1, floor(window × (1 - threshold) × 0.7)))` 推导。

```java
LumiRoseResult run(long conversationId, String content);

LumiRoseResult streamRun(long conversationId, String content);
```

- `conversationId` 由调用方提供，必须非空；框架不生成 id。
- `content` 为 null/空白 → `PARAM_INVALID(1002)`。
- `run` 返回已完成结果；`streamRun` 返回 pending 结果。
- Agent 无可变状态，同一实例可跨线程复用。

## LumiRoseResult

```java
public interface LumiRoseResult {
    String content();
    int promptTokens();
    int completionTokens();
    Iterable<String> deltas();
    void cancel();
}
```

`LumiRoseResult` 由框架实现，调用方不应自行实现。

| 方法 | 已完成 | 未完成 |
|---|---|---|
| `content()` | 所有 assistant 正文的拼接（含工具轮前言） | 抛 `IllegalStateException` |
| `promptTokens()` | 最后一次成功响应的输入 token 数 | 抛 `IllegalStateException` |
| `completionTokens()` | 最后一次成功响应的输出 token 数 | 抛 `IllegalStateException` |
| `deltas()` | `run` 为空；`streamRun` 消费结束后再次调用抛 `IllegalStateException` | 可消费一次，迭代结束即完成 |
| `cancel()` | 无效果 | 请求协作式取消；幂等 |

- `deltas()` 只产出正文增量，不产出 reasoning、工具或轮次事件；其拼接等于 `content()`。
- `content()` 是结果级拼接，不是单条历史消息；工具轮的 assistant 消息仍分别保存。
- 运行失败时，`deltas()` 迭代抛出 `LumiRoseException`。
- 不继承 `AutoCloseable`，没有 `close()`。

## Tool 与 ToolContext

```java
public interface Tool {
    String name();
    String description();
    ToolParameters parameters();
    Class<? extends ToolParameters> parametersClass();
    String execute(ToolParameters parameters);
}

Tool time = Tool.of("get_current_time", "返回当前时间",
        new EmptyParameters(), p -> "...");
```

`ToolParameters` 是标记接口。`EmptyParameters` 表示无参数。`Tool.of` 的四个参数都必须非 null；`parametersClass()` 由参数实例推导。

```java
ToolContext tools = ToolContext.builder()
        .register(new TimeTool())
        .registerAll(List.of(new ReadTool(workspace)))
        .build();

List<Tool> all = tools.tools();   // 不可变，按注册顺序
```

| 方法 | 说明 |
|---|---|
| `builder()` | 构造器 |
| `register(Tool)` | 名称唯一；null 或重名 → 1001 |
| `registerAll(Collection<Tool>)` | 等价于逐个注册；null → 1001 |
| `tools()` | 不可变列表，顺序等于注册顺序 |

内置工具在 `tool.impl`：

| 类 | 构造 | 说明 |
|---|---|---|
| `TimeTool` | `new TimeTool()` | 无参数，ISO_OFFSET_DATE_TIME，精确到秒 |
| `ReadTool` | `new ReadTool(Path workspace)` | UTF-8 全文 |
| `WriteTool` | `new WriteTool(Path workspace)` | 默认不覆盖，临时文件 + 原子移动 |
| `EditTool` | `new EditTool(Path workspace)` | `old_string` 必须恰好匹配一次 |
| `DeleteTool` | `new DeleteTool(Path workspace)` | 只删普通文件 |

文件工具只接受相对路径，且必须落在工作区内。

## ConversationRepository

```java
public interface ConversationRepository {
    Conversation load(long conversationId);

    void append(long conversationId, List<Message> pending, int contextTokens);

    void compact(long conversationId, Message summary,
                 List<Message> pending, int contextTokens);
}
```

- `load` 不返回 null；不存在时返回 `Conversation.empty(id)`，不创建会话行。
- `append` 只插入 pending，不改变已有 active；会话行不存在时先创建；pending 为空时直接返回。
- `compact` 原子地停用全部 active、插入 summary 与 pending、更新 `contextTokens`；会话行不存在时先创建。
- 实现必须线程安全。
- 本版提供 `InMemoryConversationRepository`；JDBC 实现不在本版。

## 领域对象

```java
Conversation {
    long id;
    List<Message> messages;   // 仅 active，按 id 升序
    int contextTokens;
}

Message {
    MessageRole role;
    String content;
    String reasoningContent;
    String toolCallId;
    List<ToolCall> toolCalls;
}

ToolCall {
    String id;
    String type;
    String name;
    String arguments;         // 原始 JSON 字符串
}
```

| 工厂 / 方法 | 说明 |
|---|---|
| `Conversation.empty(long id)` | 空会话，用于“不存在” |
| `Message.system(String content)` | system 消息 |
| `Message.user(String content)` | user 消息 |
| `Message.assistant(String content, String reasoningContent, List<ToolCall> toolCalls)` | content 可为 null，但 content 与 toolCalls 不能同时为空 |
| `Message.tool(String toolCallId, String content)` | tool 消息，`toolCallId` 必填 |

所有对象不可变；`messages` 与 `toolCalls` 返回不可变列表。

## 枚举

```java
MessageRole.SYSTEM / USER / ASSISTANT / TOOL   // wire 值 system/user/assistant/tool
FinishReason.STOP / LENGTH / TOOL_CALLS        // wire 值 stop/length/tool_calls
```

未知 `finish_reason` 不映射为枚举值，按 `AGENT_RESPONSE_INVALID(3003)` 处理。

## 异常与错误码

```java
try {
    agent.run(1L, "你好");
} catch (LumiRoseException e) {
    LumiRoseErrorCode code = e.getErrorCode();
    int value = code.code();          // 1001、2001……
}
```

| 类型 | 码 |
|---|---|
| `ConfigurationException` | 1001 |
| `AgentException` | 1002、3001–3005 |
| `ClientException` | 2001–2003 |
| `ToolException` | 4001–4008 |
| `StorageException` | 5001 |
| `LumiRoseException` | 9001（基类也用于未归类错误） |

`ClientException.retriable()` 判断是否可重试。完整列表与消息规则见 [错误码](error-codes.md)。

## DTO

`model.dto.chat` 对应 DeepSeek Chat Completions：

| 类型 | 关键字段 |
|---|---|
| `ChatCompletionRequest` | `model`、`messages`、`tools`、`stream`、`maxTokens` |
| `ChatCompletionMessage` | `role`、`content`、`reasoningContent`、`toolCallId`、`toolCalls` |
| `ChatCompletionTool` | `type`、`function(name, description, parameters)` |
| `ChatCompletionToolCall` | `id`、`type`、`function(name, arguments)` |
| `ChatCompletionResponse` | `choices`、`usage` |
| `ChatCompletionChoice` | `index`、`message`、`finishReason` |
| `ChatCompletionUsage` | `promptTokens`、`completionTokens`、`totalTokens` |

DTO 只在 client 与 agent 边界使用，不进入 repository。工具 `arguments` 始终是字符串，不反序列化为对象。DTO 都是不可变 `final class`，不使用 `record`；Jackson 通过构造器创建实例。

## 持久化支撑类型

| 类型 | 用途 |
|---|---|
| `ConversationDO` | `id`、`contextTokens`、`createdAt`、`updatedAt` |
| `MessageDO` | `id`、`conversationId`、`role`、`content`、`reasoningContent`、`toolCallId`、`active`、`createdAt`、`updatedAt` |
| `ToolCallDO` | `id`、`messageId`、`callIndex`、`callId`、`callType`、`functionName`、`arguments`、`createdAt` |
| `model.convert` 中的 Converter | entity ↔ DO；只做字段映射与角色校验 |

这些类型不进入 `agent`。ER 与 SQL 骨架见 [持久化](../persistence.md)。

## 已移除的 API

以下名字在 v1.1 中不存在，出现即为旧代码：

- `RunResult`、`ToolCallRecord`
- `MetricsRecorder`、`metricsRecorder(...)`
- `runResult()`
- 公共 `StreamListener`、`StreamControl`、`runStream(...)`
- `MessageRepository`、`InMemoryMessageRepository`、`appendAll(...)`
- `ContextTrimmer`、`contextBudgetChars(...)`
- `compressionSummaryMaxTokens(...)`（摘要上限改为内部推导）
- `ConversationLocks`、`RetryPolicy`（重试在 Agent 内部）
