# v1 API 参考

本页是 v1 的公开 API 快照。v1 与 v1.1 的包名、返回类型都不同，不要混用。

## 包结构

```text
io.github.xxxyjade.lumirose
├── agent            LumiRoseAgent
├── client           LumiRoseClient
├── exception        LumiRoseException、子类、ErrorCode
├── model.chat.completion  ChatCompletion*Param
├── repository       MessageRepository、InMemoryMessageRepository
└── tool             Tool、ToolContext、ToolParameters、内置工具
```

## LumiRoseClient

```java
LumiRoseClient client = LumiRoseClient.builder()
        .baseUrl("https://api.deepseek.com")
        .apiKey(System.getenv("DEEPSEEK_API_KEY"))
        .model("deepseek-chat")
        .maxTokens(4096)      // 可选
        .build();
```

| Builder 方法 | 必填 | 默认 | 约束 |
|---|---|---|---|
| `baseUrl(String)` | 是 | — | `http://` 或 `https://` 开头 |
| `apiKey(String)` | 是 | — | 非空白 |
| `model(String)` | 是 | — | 非空白 |
| `maxTokens(Integer)` | 否 | 不发送 | > 0 |

```java
ChatCompletionResponseParam chatCompletion(ChatCompletionRequestParam request);
```

v1 只有非流式方法；请求头 `Accept: application/json`，body 不含 `stream`。超时固定，不开放配置。

## LumiRoseAgent

```java
LumiRoseAgent agent = LumiRoseAgent.builder()
        .client(client)
        .toolContext(tools)
        .repository(repository)
        .systemMessage("你是一个简洁的助手")
        .maxTurns(10)
        .build();
```

```java
String run(Long conversationId, String content);
```

- `run` 同步返回最终文本；没有 `LumiRoseResult`，也没有 `streamRun`。
- `conversationId` 为 null 时生成正数 id，但不返回。
- `content` 为 null/空白 → `PARAM_INVALID(1002)`。
- Agent 无可变状态，同一实例可跨线程复用。

## MessageRepository

```java
public interface MessageRepository {
    List<ChatCompletionMessageParam> load(Long conversationId);

    void appendAll(Long conversationId,
                   List<ChatCompletionMessageParam> messages);
}
```

| 方法 | 契约 |
|---|---|
| `load` | 返回不可变快照；空会话返回空列表，不返回 null |
| `appendAll` | 一次调用原子生效；空列表为空操作 |

内置 `InMemoryMessageRepository` 线程安全，`load` 返回 `List.copyOf` 结果。v1 没有 `compact`、`active`、`contextTokens`；这些是 v1.1 的概念。

## Tool 与 ToolContext

```java
public interface Tool {
    String name();
    String description();
    ToolParameters parameters();
    Class<? extends ToolParameters> parametersClass();
    String execute(ToolParameters parameters);
}
```

```java
ToolContext tools = ToolContext.builder()
        .register(tool)
        .registerAll(list)
        .build();
```

| 方法 | 说明 |
|---|---|
| `register(Tool)` | null 或重名 → 1001；名称唯一 |
| `registerAll(Collection<Tool>)` | 逐个注册；null → 1001 |
| `tools()` | 不可变列表，顺序等于注册顺序 |

工具参数类实现 `ToolParameters`；字段用 `@JsonProperty` / `@JsonPropertyDescription` 标注，Schema 在组装请求时反射生成。`Tool.of` 四参数都必须非 null。

内置工具：`read_file`、`write_file`、`edit_file`、`delete_file`、`get_current_time`。文件工具构造时传入工作区 `Path`。

## 消息 DTO

| 类型 | 用途 |
|---|---|
| `ChatCompletionRequestParam` | 请求：`model`、`messages`、`tools`、`max_tokens` |
| `ChatCompletionResponseParam` | 响应：`choices`、`usage` |
| `ChatCompletionMessageParam` | 消息基类，包含 system/user/assistant/tool 子类型 |
| `ChatCompletionAssistantMessageParam` | assistant，可带 `reasoning_content`、`tool_calls` |
| `ChatCompletionToolMessageParam` | tool，带 `tool_call_id` |

v1 的仓储直接存 DTO；v1.1 改成了领域对象 + DO，这是两版最大的结构差异。

## 错误码

| 码 | 枚举 | 场景 |
|---|---|---|
| 1001 | `CONFIG_INVALID` | 配置、工具定义非法 |
| 1002 | `PARAM_INVALID` | 运行参数、消息构造非法 |
| 2001 | `CLIENT_HTTP_ERROR` | 非 2xx |
| 2002 | `CLIENT_RESPONSE_INVALID` | 响应结构非法 |
| 2003 | `CLIENT_IO_ERROR` | 网络异常、超时、连接重置 |
| 3001 | `AGENT_TURN_LIMIT` | 轮次用尽 |
| 3002 | `AGENT_OUTPUT_TRUNCATED` | `finish_reason=length` |
| 3003 | `AGENT_RESPONSE_INVALID` | 响应语义非法 |
| 4001 | `TOOL_NOT_FOUND` | 未注册工具 |
| 4002 | `TOOL_ARGS_INVALID` | 参数 JSON 非法 |
| 4003 | `TOOL_EXECUTION_FAILED` | 工具执行异常 |
| 4004 | `TOOL_PATH_INVALID` | 路径越界、绝对路径、目录 |
| 4005 | `TOOL_FILE_NOT_FOUND` | 文件不存在 |
| 4006 | `TOOL_FILE_EXISTS` | 文件已存在且未覆盖 |
| 4007 | `TOOL_EDIT_NO_MATCH` | 编辑目标未匹配 |
| 4008 | `TOOL_EDIT_NOT_UNIQUE` | 编辑目标多处匹配 |
| 5001 | `STORAGE_FAILED` | 仓储失败 |
| 9001 | `INTERNAL_ERROR` | 未归类内部错误 |

v1 没有 3004（取消）和 3005（压缩失败）：这两个能力在 v1.1 才加入。
