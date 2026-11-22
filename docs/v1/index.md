# LumiRose v1

> 同步、非流式、最小闭环的冻结版本。

!!! warning "v1 已冻结"
    v1 不再更新，也不接收新特性。新项目请使用 [LumiRose v1.1](https://xxxyjade.github.io/lumirose/v1.1/)；两份文档描述的是不同 API，不要混用。

v1 只解决一件事：把「模型调用 → 工具循环 → 原子提交」跑通，并且失败可预测。没有 SSE、没有上下文压缩、没有重试、没有 JDBC 实现。

## 30 秒示例

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
        .register(Tool.of("get_current_time", "返回当前时间",
                new EmptyParameters(), p -> "2026-11-21T00:51:55+08:00"))
        .build();

LumiRoseAgent agent = LumiRoseAgent.builder()
        .client(client)
        .toolContext(tools)
        .repository(new InMemoryMessageRepository())
        .systemMessage("你是一个简洁的助手")
        .maxTurns(10)
        .build();

String answer = agent.run(1L, "现在几点？");
System.out.println(answer);
```

`run` 返回 `String`，没有 `LumiRoseResult`；请求是普通 JSON，不是 SSE。

## v1 有什么

| 能力 | 说明 |
|---|---|
| 同步运行 | `String run(Long conversationId, String content)` |
| 工具循环 | 按 `tool_calls` 顺序执行，失败即终止 |
| 类型化工具 | `Tool` + `ToolParameters` + 反射生成 JSON Schema |
| 内置工具 | 读写编辑删除文件、当前时间 |
| 原子提交 | 成功后一次 `appendAll`；失败不写 |
| 可插拔仓储 | `MessageRepository` SPI + 内存实现 |
| 错误码 | 1001–5001，全部抛 `LumiRoseException` 子类 |
| 离线测试 | `mvn -o test`，不访问外网 |

## v1 不做什么

- 流式与 SSE、`streamRun`、增量回调。
- 上下文压缩、摘要、`active` 逻辑截断。
- 重试、退避、取消。
- JDBC/Redis/文件持久化实现。
- 多 Agent、RAG、多模态、Responses/Anthropic 协议。
- 进程内会话锁；同一会话并发由调用方保证。

## 文档

- [快速开始](quickstart.md)
- [API 参考](api.md)
- [v1 需求（冻结）](requirements.md)

当前主线：[LumiRose v1.1](https://xxxyjade.github.io/lumirose/v1.1/)
