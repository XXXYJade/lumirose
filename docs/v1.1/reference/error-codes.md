# 错误码

所有失败都以 `LumiRoseException` 的子类抛出；没有“返回 null 表示失败”的路径。异常携带稳定的错误码，调用方按码分支，不要解析消息文本。

## 异常层次

```text
LumiRoseException (RuntimeException)
├── ConfigurationException   1001
├── AgentException           1002, 3001–3005
├── ClientException          2001–2003
├── ToolException            4001–4008
└── StorageException         5001
```

`9001 INTERNAL_ERROR` 直接使用 `LumiRoseException` 基类。`ClientException.retriable()` 是重试判断的唯一入口。

## 错误码表

| 码 | 枚举 | 异常类 | 触发场景 | 可重试 |
|---|---|---|---|---|
| 1001 | `CONFIG_INVALID` | `ConfigurationException` | Client/Agent 配置非法、工具定义非法、压缩窗口/阈值非法、Schema 不支持的类型 | 否 |
| 1002 | `PARAM_INVALID` | `AgentException` | `content` 为 null/空白、消息构造非法 | 否 |
| 2001 | `CLIENT_HTTP_ERROR` | `ClientException` | 模型请求返回非 2xx，包括 400 上下文超限 | 仅 429、5xx |
| 2002 | `CLIENT_RESPONSE_INVALID` | `ClientException` | 2xx 但 SSE 语法/结构非法、空响应、缺 `finish_reason`、tool_calls 冲突 | 否 |
| 2003 | `CLIENT_IO_ERROR` | `ClientException` | 连接失败、读写超时、连接重置 | 是 |
| 3001 | `AGENT_TURN_LIMIT` | `AgentException` | `maxTurns` 次请求后仍无最终文本 | 否 |
| 3002 | `AGENT_OUTPUT_TRUNCATED` | `AgentException` | `finish_reason=length` | 否 |
| 3003 | `AGENT_RESPONSE_INVALID` | `AgentException` | `finish_reason` 非 `stop`、最终 `content` 空白 | 否 |
| 3004 | `AGENT_CANCELLED` | `AgentException` | 调用 `cancel()`；退避等待被中断 | 否 |
| 3005 | `AGENT_COMPRESSION_FAILED` | `AgentException` | 压缩请求失败、摘要为空、摘要被截断、压缩后缺 usage、重试耗尽 | 否 |
| 4001 | `TOOL_NOT_FOUND` | `ToolException` | 模型调用了未注册的工具 | 否 |
| 4002 | `TOOL_ARGS_INVALID` | `ToolException` | 参数 JSON 非法，或工具自检失败 | 否 |
| 4003 | `TOOL_EXECUTION_FAILED` | `ToolException` | 工具执行抛非 `LumiRoseException` 异常，或文件读写失败 | 否 |
| 4004 | `TOOL_PATH_INVALID` | `ToolException` | 路径越界、绝对路径、目标是目录 | 否 |
| 4005 | `TOOL_FILE_NOT_FOUND` | `ToolException` | 目标文件不存在 | 否 |
| 4006 | `TOOL_FILE_EXISTS` | `ToolException` | 目标已存在且未授权覆盖 | 否 |
| 4007 | `TOOL_EDIT_NO_MATCH` | `ToolException` | `edit_file` 的 `old_string` 未匹配 | 否 |
| 4008 | `TOOL_EDIT_NOT_UNIQUE` | `ToolException` | `edit_file` 的 `old_string` 匹配多处 | 否 |
| 5001 | `STORAGE_FAILED` | `StorageException` | 仓储读写失败、DO/entity 转换失败 | 否 |
| 9001 | `INTERNAL_ERROR` | `LumiRoseException` | 未归类的内部错误 | 否 |

## HTTP 状态映射

| 状态 | 错误码 | 是否重试 |
|---|---|---|
| 400（含上下文超限） | 2001 | 否 |
| 401 / 403 / 404 | 2001 | 否 |
| 408 | 2001 | 否 |
| 429 | 2001 | 是 |
| 5xx | 2001 | 是 |
| 连接失败 / 超时 / 重置 | 2003 | 是 |

**400 上下文超限不单独设码。** 它仍然是 `CLIENT_HTTP_ERROR`，但厂商 `error.message` 可能回显提示词或对话内容，因此不进入异常；调用方通过 HTTP 400 识别。新增专用码需要框架判断超限语义，而不同厂商文案不一致。

## 异常消息

消息只描述“哪一步失败”，不复述数据：

| 码 | 消息允许包含 | 消息禁止包含 |
|---|---|---|
| 1001 | 字段名、工具名 | 字段值、`apiKey`（任何形式） |
| 1002 | 参数名 | 消息内容 |
| 2001 | HTTP 状态、`error.code`、`error.type` | 厂商 `error.message`、响应体原文、请求头、API Key |
| 2002 | 结构问题的类型（空响应、非法 JSON、缺少 finish_reason 等） | 响应体原文、增量内容 |
| 2003 | 操作与异常类型 | 请求体、API Key |
| 3001 | `maxTurns` | 对话内容 |
| 3002 / 3003 | `finish_reason` | 对话内容 |
| 3004 | 取消阶段 | 对话内容 |
| 3005 | 压缩阶段（请求、空摘要、截断、缺 usage） | 对话内容、摘要原文 |
| 4001 | 工具名 | 参数 |
| 4002 | 工具名、JSON 位置 | 参数值 |
| 4003 | 工具名、底层异常类型 | 参数、工具结果 |
| 4004–4008 | 工具名、失败原因 | 文件内容 |
| 5001 | 仓储操作（load/append/compact） | 消息内容、SQL、连接串 |
| 9001 | 概要 | 任意敏感数据 |

`getCause()` 规则：2003、4002、4003 保留底层 cause；2002 明确不保留；其余错误码不承诺 cause 内容。

## 调用方处理建议

| 场景 | 建议 |
|---|---|
| 1001 | 启动期配置错误，直接失败，不要重试 |
| 1002 | 调用方代码错误，修正入参 |
| 2001（429/5xx） | 框架已按 `maxRetries` 重试；耗尽后由调用方决定是否整体重开 |
| 2001（400 超限） | 缩短输入、开启压缩或换更大窗口模型；不要重试同一请求 |
| 2001（401/403） | 检查 Key 与权限，不重试 |
| 2002 | 按协议不兼容处理，记录日志；可重试一次新请求 |
| 2003 | 网络问题；框架已重试，耗尽后由调用方决定 |
| 3001 | 提高 `maxTurns` 或拆小任务 |
| 3002 | 提高 `maxTokens` 或缩短输出要求 |
| 3003 | 模型返回不符合预期；检查提示词或换模型 |
| 3004 | 用户主动停止；同一会话可再次运行 |
| 3005 | 检查压缩模型可用性、窗口/阈值与 usage；旧历史未变 |
| 4001–4008 | 工具层问题；修正工具注册、参数或路径 |
| 5001 | 仓储故障；检查数据库/实现，本次未提交 |
| 9001 | 记录日志并上报，视为缺陷 |

## 安全约束

- `apiKey` 不得出现在异常消息、cause、日志或 `toString()` 中。
- 异常消息不包含消息内容、工具参数/结果、请求/响应体原文。
- 2001 只提取 HTTP 状态与厂商 `error` 的 `code`/`type`，不包含厂商 `error.message`。
- 错误码是公开契约；新增或改码需要同步更新本页与 [v1.1 需求](../requirements/v1.1.md)。
