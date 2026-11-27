# v1 快速开始

## 前置条件

- JDK 21
- Maven 3.9+
- DeepSeek API Key：`DEEPSEEK_API_KEY`

v1 不发布 Maven artifact，从源码构建：

```bash
git clone https://github.com/xxxyjade/lumirose.git
cd lumirose
git checkout v1.0
mvn -o test
```

## 创建 Client

```java
import io.github.xxxyjade.lumirose.client.LumiRoseClient;

LumiRoseClient client = LumiRoseClient.builder()
        .baseUrl("https://api.deepseek.com")
        .apiKey(System.getenv("DEEPSEEK_API_KEY"))
        .model("deepseek-chat")
        .maxTokens(4096)          // 可选
        .build();
```

v1 的 Client 使用普通 JSON 请求，不发送 `stream`。超时固定为连接 10s、写 30s、读 120s、整体 180s。

## 注册工具

```java
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.args.EmptyParameters;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;

ToolContext tools = ToolContext.builder()
        .register(Tool.of("get_current_time", "返回当前时间",
                new EmptyParameters(), p -> "2026-11-21T00:51:55+08:00"))
        .build();
```

工具参数类实现 `ToolParameters`，字段用 `@JsonProperty` 标注，Schema 由框架反射生成。文件工具构造时传入工作区：

```java
import java.nio.file.Path;
import io.github.xxxyjade.lumirose.tool.ReadTool;
import io.github.xxxyjade.lumirose.tool.WriteTool;

Path workspace = Path.of("workspace");
ToolContext fileTools = ToolContext.builder()
        .register(new ReadTool(workspace))
        .register(new WriteTool(workspace))
        .build();
```

路径必须是相对路径，且不能逃出工作区。

## 选择仓储

v1 的 SPI 是 `MessageRepository`：

```java
public interface MessageRepository {
    List<ChatCompletionMessageParam> load(Long conversationId);
    void appendAll(Long conversationId, List<ChatCompletionMessageParam> messages);
}
```

内置内存实现：

```java
import io.github.xxxyjade.lumirose.repository.InMemoryMessageRepository;

var repository = new InMemoryMessageRepository();
```

`load` 返回不可变快照；`appendAll` 一次调用原子生效；同一会话的读写在内存实现内部串行。生产环境需要自己实现 `MessageRepository`。

## 创建 Agent

```java
import io.github.xxxyjade.lumirose.agent.LumiRoseAgent;

LumiRoseAgent agent = LumiRoseAgent.builder()
        .client(client)
        .toolContext(tools)
        .repository(repository)
        .systemMessage("你是一个简洁的助手")
        .maxTurns(10)
        .build();
```

| 配置 | 必填 | 默认 | 约束 |
|---|---|---|---|
| `client` | 是 | — | 非 null |
| `toolContext` | 是 | — | 非 null |
| `repository` | 是 | — | 非 null |
| `systemMessage` | 否 | `You are a helpful assistant!` | null/空白表示不发送 |
| `maxTurns` | 否 | 10 | ≥ 1 |

## 运行

```java
String answer = agent.run(1L, "读取 a.txt 并总结");
System.out.println(answer);
```

- `conversationId` 为 null 时框架生成正数 id，但不返回；
- `content` 为 null/空白 → `PARAM_INVALID(1002)`；
- 成功后本轮 system/user/assistant/tool 消息一次写入仓储；
- 失败、轮次用尽、工具异常都不写。

## 错误处理

```java
import io.github.xxxyjade.lumirose.exception.LumiRoseException;

try {
    String answer = agent.run(1L, "你好");
} catch (LumiRoseException e) {
    System.err.println(e.getErrorCode().code() + ": " + e.getMessage());
}
```

| 码 | 含义 |
|---|---|
| 1001 | 配置或工具定义非法 |
| 1002 | 运行参数或消息构造非法 |
| 2001 | 模型请求失败（非 2xx） |
| 2002 | 响应结构非法 |
| 2003 | 网络异常、超时、连接重置 |
| 3001 | 轮次用尽 |
| 3002 | 输出被截断 |
| 3003 | 响应语义非法 |
| 4001–4008 | 工具未注册、参数非法、执行失败、路径/文件/编辑错误 |
| 5001 | 仓储失败 |

## 已知限制

- 没有流式输出；`run` 会等完整响应。
- 没有重试；网络失败直接抛错。
- 没有上下文压缩；长对话可能撞上下文上限。
- 没有内置数据库实现；内存仓储重启即丢。
- 同一会话并发 `run` 可能互相覆盖，调用方需保证单写者。

下一步：[API 参考](api.md) · 切换到 [v1.1](https://xxxyjade.github.io/lumirose/v1.1/)
