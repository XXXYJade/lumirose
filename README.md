# LumiRose

一个**学习导向、面向生产接入**的 Java Agent 框架。把「模型调用 → 工具循环 → 流式输出 → 上下文压缩 → 会话持久化」收敛成一条能读完的链路。

> 文档按版本独立维护：**[v1.1（最新）](https://xxxyjade.github.io/lumirose/v1.1/)** ｜ **[v1（冻结）](https://xxxyjade.github.io/lumirose/v1/)**。两版 API 不同，不要混用。

- 单一 SSE 传输：`run` 与 `streamRun` 共用同一套请求、工具循环和提交逻辑。
- 单一结果类型：`LumiRoseResult`；流式增量通过 `deltas()` 消费，不需要实现 Listener。
- 原子提交：成功结束才写仓储；失败、取消、压缩失败都不写。
- 可替换持久化：只依赖 `ConversationRepository` SPI；内置内存实现，JDBC 由接入方实现。
- Java 21、OkHttp、Jackson、SLF4J；`mvn -o test` 离线运行。

## 最小示例

```java
import io.github.xxxyjade.lumirose.agent.LumiRoseAgent;
import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.common.result.LumiRoseResult;
import io.github.xxxyjade.lumirose.repository.InMemoryConversationRepository;
import io.github.xxxyjade.lumirose.tool.ToolContext;
import io.github.xxxyjade.lumirose.tool.impl.TimeTool;

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

// 循环结束即完成
System.out.println(result.content());
System.out.println(result.promptTokens() + " / " + result.completionTokens());
```

`deltas()` 只产出正文增量，且只能消费一次；需要提前停止时调用 `result.cancel()`。`LumiRoseResult` 不是资源，没有 `close()`。

## 文档

| 文档 | 内容 |
|---|---|
| [快速开始](docs/v1.1/quickstart.md) | 从零跑通一次 run |
| [配置](docs/v1.1/configuration.md) | Client / Agent 参数与校验 |
| [Agent 与结果](docs/v1.1/agent.md) | 运行流程、结果状态、取消 |
| [流式输出](docs/v1.1/streaming.md) | SSE、`deltas()`、队列与重试边界 |
| [工具](docs/v1.1/tools.md) | Tool 接口、Schema、内置文件工具 |
| [上下文压缩](docs/v1.1/context-compression.md) | 0.65 触发、摘要、逻辑截断 |
| [持久化](docs/v1.1/persistence.md) | SPI、ER、SQL 骨架 |
| [架构总览](docs/v1.1/architecture.md) | 分层、依赖方向、时序 |
| [API 参考](docs/v1.1/reference/api.md) | 公开类型与方法 |
| [错误码](docs/v1.1/reference/error-codes.md) | 错误语义与处理建议 |
| [术语表](GLOSSARY.md) | 会话、压缩、交付等概念的唯一说法（仓库根，不进文档站） |
| [v1 文档（冻结）](https://xxxyjade.github.io/lumirose/v1/) | 同步、非流式、DTO 仓储的旧版 |

工程记录（需求、测试账本、ADR、决策索引）只保存在本地，不入仓库、也不进公开文档站；仓库与站点只保留接入文档。

## 状态

v1.1 实现已完成，验收核对进行中：`mvn -o test` 161 个用例：160 通过、1 跳过（符号链接用例需创建链接权限，管理员下已验证；明细见本地测试账本）；压缩请求「保留角色结构、不声明 `tools`」这一形状已在 `deepseek-chat` 上冒烟验证。

v1.1 明确不做：多 provider、多 Agent、RAG、多模态、Web UI、独立服务端、内置 JDBC/Redis、进程内会话锁、遥测 SPI、结构化输出。

## 构建

```bash
mvn -o test
```

文档站（需要 Python、Material 与 mike）：

```bash
pip install mkdocs-material mike
mkdocs serve
```
