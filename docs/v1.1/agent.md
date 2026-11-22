# Agent 与结果

## 入口

```java
LumiRoseResult run(long conversationId, String content);
LumiRoseResult streamRun(long conversationId, String content);
```

两者共用同一套执行逻辑：加载会话、判断压缩、SSE 请求、工具循环、原子提交。区别只有增量消费者和返回时机。

- `run`：同步返回已完成结果；SSE 在内部消费完；`deltas()` 为空。
- `streamRun`：返回未完成结果；第一次迭代 `deltas()` 时启动虚拟线程；消费完才填充 `content()` 和 usage。

`conversationId` 由调用方提供，必须非空；`content` 为 null 或空白 → `PARAM_INVALID(1002)`。

## 一次运行的步骤

```text
1. load(id) -> active messages + contextTokens
2. contextTokens > window * contextCompressionThreshold ? 压缩 : 不压缩
3. base = 压缩后的 [summary] 或 active；pending = [user(content)]
4. 循环：
   - request = [system?] + base + pending
   - SSE 请求
   - usage -> contextTokens（压缩后最后一次响应缺 usage → 3005）
   - tool_calls -> 顺序执行工具，assistant + tool 加入 pending，下一轮重建 request
   - stop -> assistant 加入 pending，结束
5. 成功：append 或 compact（pending 含本次 user）
6. 返回 LumiRoseResult
```

失败、取消、轮次用尽、压缩失败都不执行第 5 步。

`content()` 是所有 assistant 正文的拼接，包含工具调用轮的前言；`deltas()` 的拼接与它相等。

## LumiRoseResult 的状态

| 状态 | 来源 | `deltas()` | `content()` / usage |
|---|---|---|---|
| 已完成 | `run` 返回 | 空 | 立即可用 |
| 未完成 | `streamRun` 返回 | 可消费一次 | 抛 `IllegalStateException` |
| 已完成 | `streamRun` 的 `deltas()` 迭代结束 | 已消费完 | 可用 |

示例：

```java
LumiRoseResult result = agent.streamRun(1L, "读取 a.txt 并总结");

for (String delta : result.deltas()) {
    System.out.print(delta);
}

// 循环结束 = 运行完成
System.out.println(result.content());
System.out.println(result.promptTokens());
System.out.println(result.completionTokens());
```

`deltas()` 是唯一消费入口：

- 只产出正文增量，按到达顺序；拼接等于 `content()`；
- 包含工具调用轮的前言，因此 `content()` 可能不只是一轮 assistant 文本；
- 只允许消费一次；
- reasoning、工具事件、轮次事件只进日志，不进入 `deltas()`；
- 消费过慢会占队列内存；提前退出用 `cancel()`。

## 取消

```java
LumiRoseResult result = agent.streamRun(1L, "写一篇长文");

// 任意线程
result.cancel();

for (String delta : result.deltas()) {
    System.out.print(delta);
}
```

- `cancel()` 幂等；
- 取消后不再交付增量，抛 `AGENT_CANCELLED(3004)`；
- 本次不提交；
- 已执行工具的副作用不回滚；
- 取消不硬中断阻塞读或工具：最迟在下一次事件或 120s 读超时后生效。

`LumiRoseResult` 不继承 `AutoCloseable`；提前退出调用 `cancel()`，不要用 `close()`。

## 错误

所有失败抛 `LumiRoseException` 子类，携带错误码：

| 场景 | 错误码 |
|---|---|
| 参数非法 | 1002 |
| 轮次用尽 | 3001 |
| 输出被截断 | 3002 |
| 响应语义非法 | 3003 |
| 取消 | 3004 |
| 压缩失败 | 3005 |
| 模型请求失败 | 2001 |
| 网络失败 | 2003 |
| 仓储失败 | 5001 |

完整列表见 [错误码](reference/error-codes.md)。

## 轮次

`maxTurns` 只统计对话请求，不统计压缩调用，也不统计重试。

- `maxTurns=10`：最多 10 次模型请求；
- 用尽仍没有最终文本 → `AGENT_TURN_LIMIT(3001)`；
- 失败不提交。

## system 提示词

- `systemMessage` 非空白：作为唯一 system 消息放在请求最前；
- 为空且存在压缩摘要：补固定 `You are a helpful assistant.`，避免请求以 assistant 开头；
- 为空且无摘要：不发送 system。

system 提示词不持久化；每次请求时按配置重新加入。

## 并发

框架不做进程内串行化：

- 不同会话可以并行；
- 同一会话的并发运行由调用方保证；
- 仓储实现必须线程安全；
- 没有 version，最后写入者覆盖。

如果需要单写者语义，请在上层按 `conversationId` 串行调用。
