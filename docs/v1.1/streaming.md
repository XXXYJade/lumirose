# 流式输出

## 只有一套传输

LumiRose 不保留非流式 JSON 请求路径。`run` 和 `streamRun` 都走 SSE：

- `run` 在内部消费 SSE，丢弃增量，返回已完成结果；
- `streamRun` 把正文增量放进队列，交给 `deltas()`。

这样请求体、错误处理、usage 解析、工具分片装配只有一套代码。

## 调用方只消费

调用方不需要实现 Listener，也不需要注册回调：

```java
LumiRoseResult result = agent.streamRun(1L, "读取 a.txt 并总结");

for (String delta : result.deltas()) {
    System.out.print(delta);
}

System.out.println(result.content());
```

`deltas()` 是 `Iterable<String>`：

- 只包含正文增量；
- 按到达顺序，拼接等于最终 `content()`；
- 包含工具调用轮的前言；工具结果本身不进入增量；
- 只能消费一次；
- 消费完迭代结束，结果变为已完成。

## 什么时候启动

`streamRun` 返回时还没开始跑。第一次迭代 `deltas()` 时：

1. 启动一个虚拟线程；
2. 在虚拟线程里执行与 `run` 相同的 Agent 循环；
3. 每收到一个正文增量，放入无界队列；
4. 调用方线程从队列消费。

所以不会出现“还没开始消费就丢事件”的竞态；如果调用方一直不消费，任务也不会启动。

## 队列与内存

- 队列无界；调用方消费过慢会占内存。
- 调用方应消费完，或调用 `cancel()` 停止。
- 不要用 `close()`：`LumiRoseResult` 不是资源，不继承 `AutoCloseable`。

## 运行完成

迭代结束代表运行完成：

```java
for (String delta : result.deltas()) {
    System.out.print(delta);
}

// 现在可读
String content = result.content();
int prompt = result.promptTokens();
int completion = result.completionTokens();
```

未完成时 `content()` / usage 抛 `IllegalStateException`，不会隐式阻塞。

## 取消

```java
LumiRoseResult result = agent.streamRun(1L, "写一篇长文");

// 任意线程
result.cancel();
```

取消后：

- 不再交付增量；
- 抛 `AGENT_CANCELLED(3004)`；
- 本次不提交；
- 已执行工具副作用不回滚。

取消是协作式的：阻塞读最多延迟到下一次事件或 120s 读超时；工具执行不可中断。

## 重试边界

只能在该轮尚未收到任何正文增量、且尚未收到 `finish_reason` 前重试。

- 首个增量之前失败：按 `maxRetries` 退避重试；
- 已经收到增量：不再重试，直接抛错；
- 重试不重复执行工具，不计入 `maxTurns`；
- `run` 与 `streamRun` 同口径。

## reasoning 与工具事件

`deltas()` 只推正文。以下内容只进日志，不进入流：

- `reasoning_content`
- `tool_calls` 分片
- 工具开始/结束
- 每轮 usage

这是刻意的取舍：调用方只需要一个最简单的消费入口；需要更细的事件时，再加事件流。

## 错误

运行失败时，`deltas()` 迭代抛 `LumiRoseException`：

```java
try {
    for (String delta : result.deltas()) {
        System.out.print(delta);
    }
} catch (LumiRoseException e) {
    log.error("run failed: {}", e.getErrorCode().code(), e);
}
```

此时 `content()` 不可用。
