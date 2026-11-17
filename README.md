# LumiRose

把 DeepSeek 对话或受限文件操作嵌入 Java 应用的单 Agent 框架：同步 / 流式调用、类型化工具、原子提交、可插拔仓储。最小闭环，不引入 Spring，也不发布 artifact。

- Java 21、OkHttp、Jackson、SLF4J；测试离线运行，不访问外网。
- 一次 `run` 的所有新消息在成功结束时一次性写入仓储，失败或取消不写。

## 快速开始

```java
import io.github.xxxyjade.lumirose.agent.LumiRoseAgent;
import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.repository.InMemoryMessageRepository;
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.args.EmptyParameters;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;

LumiRoseClient client = LumiRoseClient.builder()
        .baseUrl("https://api.deepseek.com")
        .apiKey(System.getenv("DEEPSEEK_API_KEY"))
        .model("deepseek-chat")
        .build();

ToolContext tools = ToolContext.builder()
        .register(Tool.of("get_current_time", "返回当前时间", new EmptyParameters(), p -> "12:00"))
        .build();

LumiRoseAgent agent = LumiRoseAgent.builder()
        .client(client)
        .toolContext(tools)
        .repository(new InMemoryMessageRepository())
        .systemMessage("你是一个简洁的助手")
        .maxTurns(10)
        .maxRetries(2)
        .build();

String answer = agent.run(1L, "现在几点？");
```

流式与取消：

```java
import io.github.xxxyjade.lumirose.agent.RunResult;
import io.github.xxxyjade.lumirose.agent.StreamControl;
import io.github.xxxyjade.lumirose.agent.StreamListener;

final StreamControl[] holder = new StreamControl[1];
RunResult result = agent.runStream(1L, "读取 a.txt 并总结", new StreamListener() {
    @Override
    public void onStart(StreamControl control) {
        holder[0] = control;                 // 任意线程调用 holder[0].cancel()
    }

    @Override
    public void onContentDelta(String delta) {
        System.out.print(delta);
    }

    @Override
    public void onToolStart(String toolCallId, String toolName) {
        System.out.println("[调用 " + toolName + "]");
    }
});
```

## 配置

`LumiRoseClient`：

| 配置 | 必填 | 默认 | 约束 |
|---|---|---|---|
| `baseUrl` | 是 | — | `http://` 或 `https://` 开头 |
| `apiKey` | 是 | — | 非空白 |
| `model` | 是 | — | 非空白 |
| `maxTokens` | 否 | 不发送 | 大于 0 |

`LumiRoseAgent`：

| 配置 | 默认 | 说明 |
|---|---|---|
| `systemMessage` | `You are a helpful assistant!` | null 或空白表示不发送 |
| `maxTurns` | 10 | 每次运行最多的模型请求次数 |
| `maxRetries` | 0 | 只对 2003、429、5xx 重试 |
| `retryBaseDelayMillis` / `retryMaxDelayMillis` | 500 / 8000 | 指数退避，无抖动 |
| `contextBudgetChars` | null | 请求字符预算；null 表示不裁剪 |
| `metricsRecorder` | no-op | 请求、重试、用量、工具四类回调 |

## 错误码

| 码 | 含义 |
|---|---|
| 1001 | 配置或工具定义非法 |
| 1002 | 运行参数非法 |
| 2001 | 模型请求失败（非 2xx） |
| 2002 | 响应结构非法 |
| 2003 | 网络异常、超时、连接重置 |
| 3001 | 轮次用尽 |
| 3002 | 模型输出被截断 |
| 3003 | 响应语义非法 |
| 3004 | 运行已取消 |
| 4001–4008 | 工具未注册、参数非法、执行失败、路径非法、文件不存在、已存在、编辑未匹配、多处匹配 |
| 5001 | 仓储读写失败 |
| 9001 | 未归类内部错误 |

## 接入自己的仓储

框架不内置持久化。实现 `MessageRepository` 即可（`load` 返回不可变快照，`appendAll` 一次调用原子生效）：

```java
public final class MySqlMessageRepository implements MessageRepository {
    @Override
    public List<ChatCompletionMessageParam> load(Long conversationId) { /* ... */ }

    @Override
    public void appendAll(Long conversationId, List<ChatCompletionMessageParam> messages) { /* ... */ }
}
```

把 `src/test/java/io/github/xxxyjade/lumirose/repository/RepositoryContract.java` 复制到你的测试目录，继承并实现 `newRepository()`，三条契约用例会自动运行。同会话串行由 Agent 的进程内锁保证；跨进程部署需要你自己在存储层协调。

## 已知限制

- 没有内置持久化；进程重启后内存仓储的历史会丢失。
- 会话锁只在单进程内有效，多实例共享同一存储仍可能互相覆盖。
- 上下文裁剪按字符估算，不是精确 token；单条超长消息不会被截断。
- 取消是协作式的：没有增量到达时，最迟要等下一次事件或 120s 读超时。
- 重试默认关闭；流式一旦向回调交付过内容就不再重试。
- 工具结果与文件读取不截断，超大结果由接入方自行控制。

## 构建

```bash
mvn -o test
```
