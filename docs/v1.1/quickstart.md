# 快速开始

## 前置条件

- JDK 21
- Maven 3.9+
- DeepSeek API Key：`DEEPSEEK_API_KEY`

LumiRose 当前不发布 Maven artifact，从源码构建：

```bash
git clone https://github.com/xxxyjade/lumirose.git
cd lumirose
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

Client 只走 SSE；所有请求都带 `stream: true`。

## 注册工具

```java
import io.github.xxxyjade.lumirose.tool.ToolContext;
import io.github.xxxyjade.lumirose.tool.impl.TimeTool;

ToolContext tools = ToolContext.builder()
        .register(new TimeTool())
        .build();
```

内置工具见 [工具](tools.md)。也可以注册自定义 `Tool`。

## 选择仓储

```java
import io.github.xxxyjade.lumirose.repository.InMemoryConversationRepository;

var repository = new InMemoryConversationRepository();
```

内存仓储只用于示例和测试；生产环境实现 `ConversationRepository`，见 [持久化](persistence.md)。

## 创建 Agent

```java
import io.github.xxxyjade.lumirose.agent.LumiRoseAgent;

LumiRoseAgent agent = LumiRoseAgent.builder()
        .client(client)
        .toolContext(tools)
        .repository(repository)
        .systemMessage("你是一个简洁的助手")
        .maxTurns(10)
        .maxRetries(2)
        .contextWindowTokens(128_000)
        .contextCompressionThreshold(0.65)
        .build();
```

`contextWindowTokens` 为 null 时关闭上下文压缩。其余配置见 [配置](configuration.md)。

## 同步运行

```java
import io.github.xxxyjade.lumirose.common.result.LumiRoseResult;

LumiRoseResult result = agent.run(1L, "读取 a.txt 并总结");
System.out.println(result.content());
```

`run` 在内部消费完 SSE 才返回；`deltas()` 为空，`content()` 和 usage 立即可用。`content()` 是所有 assistant 正文的拼接，工具轮前言也计入。

## 流式运行

```java
LumiRoseResult result = agent.streamRun(1L, "读取 a.txt 并总结");

for (String delta : result.deltas()) {
    System.out.print(delta);
}

// 消费完 deltas() 之后才可读
System.out.println();
System.out.println(result.content());
```

`streamRun` 第一次迭代 `deltas()` 时启动虚拟线程；未消费完时 `content()` 抛 `IllegalStateException`，不会隐式阻塞。

## 取消

```java
LumiRoseResult result = agent.streamRun(1L, "写一篇长文");

new Thread(() -> {
    // 例如 UI 的停止按钮
    result.cancel();
}).start();

for (String delta : result.deltas()) {
    System.out.print(delta);
}
```

取消后抛 `AGENT_CANCELLED(3004)`，本次不提交。取消是协作式的：阻塞读最多延迟到下一次事件或 120s 读超时。

## 离线验证

```bash
mvn -o test
```

测试使用 `FakeServer`，不读真实 Key，不访问外网。

下一步：[配置](configuration.md) · [Agent 与结果](agent.md) · [流式输出](streaming.md)
