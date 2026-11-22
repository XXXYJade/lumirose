# LumiRose

> 一个学习导向、同时以生产级为目标的 Java Agent 框架。

!!! note "版本"
    当前是 **v1.1**（最新）。v1 已冻结，可在右上角版本选择器切换，或直接访问 [v1 文档](https://xxxyjade.github.io/lumirose/v1/)。两版 API 不同，请勿混用。

LumiRose 把“模型调用 → 工具循环 → 流式输出 → 上下文压缩 → 会话持久化”压缩成一条能读完的链路。它不隐藏 Agent 循环，也不要求你先学一套框架抽象；你可以顺着代码走完一次 `run`。

## 30 秒示例

```java
LumiRoseClient client = LumiRoseClient.builder()
        .baseUrl("https://api.deepseek.com")
        .apiKey(System.getenv("DEEPSEEK_API_KEY"))
        .model("deepseek-chat")
        .build();

ToolContext tools = ToolContext.builder()
        .register(new TimeTool())
        .build();

LumiRoseAgent agent = LumiRoseAgent.builder()
        .client(client)
        .toolContext(tools)
        .repository(new InMemoryConversationRepository())
        .systemMessage("你是一个简洁的助手")
        .maxTurns(10)
        .contextWindowTokens(128_000)
        .build();

LumiRoseResult result = agent.run(1L, "现在几点？");
System.out.println(result.content());
```

流式消费：

```java
LumiRoseResult result = agent.streamRun(1L, "读取 a.txt 并总结");

for (String delta : result.deltas()) {
    System.out.print(delta);
}

System.out.println(result.content());
System.out.println(result.promptTokens() + " / " + result.completionTokens());
```

## 设计目标

| 目标 | 做法 |
|---|---|
| 循环可读 | 只有一个 Agent 循环；`run` 与 `streamRun` 共用同一套请求、工具和提交逻辑 |
| 流式统一 | 只走 SSE；调用方通过 `LumiRoseResult.deltas()` 消费正文增量，不需要实现 Listener |
| 上下文可控 | 超过窗口 65% 时调用模型生成摘要；摘要作为 assistant 消息保留，旧消息逻辑截断 |
| 持久化可替换 | 只依赖 `ConversationRepository` SPI；内存实现可替换为 MySQL/PostgreSQL |
| 生产可用 | 失败原子、错误码明确、可取消、可重试、离线测试、文档完整 |

## 文档

- [快速开始](quickstart.md)
- [配置](configuration.md)
- [Agent 与结果](agent.md)
- [流式输出](streaming.md)
- [工具](tools.md)
- [上下文压缩](context-compression.md)
- [持久化](persistence.md)
- [架构总览](architecture.md)
- [关键设计决策](design-decisions.md)
- [错误码](reference/error-codes.md)
- [API 参考](reference/api.md)
- [v1.1 需求](requirements/v1.1.md)
- [v1.1 测试计划](testing/v1.1.md)

## 非目标

- 多 provider 适配（Responses、Anthropic）、多 Agent、RAG、多模态。
- Web UI、独立 HTTP 服务端、分布式调度。
- 内置 JDBC/Redis 实现；本版只给 SPI、内存实现和 ER 设计。
- 结构化输出、JSON mode、采样参数。

## 状态

当前文档描述 **v1.1 目标规格**；代码实现正在从旧 v1.1 迁移到该规格。文档先于代码，是为了让需求和边界在实现前可审查。
