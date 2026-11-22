# 配置

所有 builder 在 `build()` 时校验；失败抛 `ConfigurationException(CONFIG_INVALID)`，异常消息只出现字段名，不出现字段值，`apiKey` 不会出现在任何异常或日志里。

## LumiRoseClient

| 配置 | 必填 | 默认 | 约束 |
|---|---|---|---|
| `baseUrl` | 是 | — | `http://` 或 `https://` 开头；末尾 `/` 自动去掉 |
| `apiKey` | 是 | — | 非空白 |
| `model` | 是 | — | 非空白；本版按 DeepSeek Chat Completions 使用 |
| `maxTokens` | 否 | 不发送 | 大于 0；请求级 `ChatCompletionRequest.maxTokens` 优先 |

超时固定：

- 连接 10s
- 写 30s
- 读 120s（按两次读取间隔计算）
- 不设整体超时，避免长生成被掐断

Client 只提供 SSE，不提供非流式 JSON 方法。

## LumiRoseAgent

| 配置 | 必填 | 默认 | 约束 |
|---|---|---|---|
| `client` | 是 | — | 非 null |
| `toolContext` | 是 | — | 非 null |
| `repository` | 是 | — | 非 null |
| `systemMessage` | 否 | `You are a helpful assistant!` | null/空白表示不发送；存在摘要且为空时补最小 system |
| `maxTurns` | 否 | 10 | 大于等于 1 |
| `maxRetries` | 否 | 0 | 大于等于 0；0 表示不重试 |
| `retryBaseDelayMillis` | 否 | 500 | 大于等于 0 |
| `retryMaxDelayMillis` | 否 | 8000 | 大于等于 `retryBaseDelayMillis` |
| `contextWindowTokens` | 否 | null | null 表示关闭压缩；非 null 时大于 0 |
| `contextCompressionThreshold` | 否 | 0.65 | `(0, 1)` |

### 压缩阈值与摘要上限

- `contextCompressionThreshold`：**什么时候压**。`contextTokens > window × threshold` 时触发。
- 摘要上限不是公开配置：压缩时按 `min(2048, max(1, floor(window × (1 - threshold) × 0.7)))` 推导 `max_tokens`，用于限制摘要长度并给新消息留出空间。

## 示例

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

## 并发

框架不提供进程内会话锁：

- 不同 `conversationId` 可以并行；
- 同一 `conversationId` 的并发 `run` 由调用方保证不并发；
- 仓储实现必须线程安全；
- 没有 version，冲突结果为 last-write-wins。

## 校验

构建期会拒绝：

- 缺失/空白的 `baseUrl`、`apiKey`、`model`
- `maxTokens <= 0`
- `maxTurns < 1`
- `maxRetries < 0`
- `retryMaxDelayMillis < retryBaseDelayMillis`
- `contextWindowTokens <= 0`
- `contextCompressionThreshold` 不在 `(0, 1)`

运行时参数错误（如 `content` 为空）抛 `PARAM_INVALID(1002)`。
