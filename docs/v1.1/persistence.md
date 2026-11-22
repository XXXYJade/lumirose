# 持久化

## SPI

```java
public interface ConversationRepository {
    Conversation load(long conversationId);

    // 普通追加：会话行不存在时先创建；只插入 pending，不改变已有 active
    void append(long conversationId, List<Message> pending, int contextTokens);

    // 压缩：会话行不存在时先创建；停用全部 active，插入摘要和新消息，一个事务
    void compact(long conversationId, Message summary,
                 List<Message> pending, int contextTokens);
}
```

- `load` 不存在返回 `Conversation.empty(id)`，不返回 null；不创建会话行；
- `append` 会话行不存在时先创建，只插入 pending（active=true），更新 `contextTokens`；pending 为空时直接返回；
- `compact` 会话行不存在时先创建，是唯一把 active 置 false 的操作；
- 两个写操作各自原子生效；
- 实现必须线程安全；同一会话的并发由调用方保证。

## SPI 是什么

SPI 全称 **Service Provider Interface**（服务提供者接口）。它描述“框架定义接口、调用方提供实现”的扩展点：

- **API**：调用方调用框架，例如 `agent.run(...)`、`client.chatCompletion(...)`；
- **SPI**：框架调用调用方，例如 Agent 调用 `repository.load/append/compact`。

本项目的 SPI 是 `ConversationRepository`。它是一个普通 Java 接口，通过 `LumiRoseAgent.builder().repository(...)` 注入，不依赖 `META-INF/services` 或 `ServiceLoader`。叫 SPI 是强调“这是给你实现的契约”，不是“框架内部接口”。

好处：Agent 不需要知道存储是内存、MySQL 还是文件；测试可以注入 `RecordingRepository`；换存储不改 Agent。代价：实现者要自己保证线程安全、`append`/`compact` 的原子性，以及 `load` 的过滤与排序。

## 标识：领域对象与数据库行

`Message` 没有 `id`，`Conversation` 有 `id`，`ToolCall` 有 `id`。这几个 `id` 不是一回事：

| 字段 | 属于 | 含义 | 谁生成 |
|---|---|---|---|
| `Conversation.id` | 领域 / 数据库 | 会话标识，`load/append/compact` 的入参 | 调用方，必须非空 |
| `Message.id` | 不存在 | 消息行标识只在 `MessageDO.id` | 数据库 |
| `ToolCall.id` | 协议 | 模型返回的 tool call id（如 `call_1`），用于和 tool 消息配对 | 模型 |
| `Message.toolCallId` | 协议 | `role=tool` 消息对应的 tool call id | Agent 从 `ToolCall.id` 复制 |
| `ToolCallDO.id` | 数据库 | `message_tool_calls` 的主键 | 数据库 |
| `ToolCallDO.callId` | 数据库 | 协议 `ToolCall.id` 的持久化列 | Agent 传入 |

`Message` 是值对象，不是表行：

- 请求组装只关心顺序和内容；`load` 按数据库 id 升序返回列表，顺序已经体现在 `List<Message>` 里，不需要把行 id 带进领域对象。
- 写操作以“整个 pending 列表”为单位；`append`/`compact` 由仓储插入并取得数据库 id，Agent 不需要回填。
- 单条消息没有更新/删除 API，消息行 id 对业务没有用处。
- 关系用嵌套表达：`Conversation` 拥有 `Message`，`Message` 拥有 `ToolCall`，所以领域对象不需要 `conversationId`/`messageId` 外键；这些外键只出现在 DO。
- `active` 也是持久化可见性，不是领域字段；`load` 只返回 active 消息，业务看不到已截断的行。

## toolCallId 的作用

`toolCallId` 只用于 `role=tool` 的消息，是 Chat Completions 的配对键：

```json
{"role":"assistant","content":null,"tool_calls":[
  {"id":"call_1","type":"function","function":{"name":"read_file","arguments":"{\"path\":\"a.txt\"}"}},
  {"id":"call_2","type":"function","function":{"name":"get_current_time","arguments":"{}"}}
]}

{"role":"tool","tool_call_id":"call_1","content":"文件内容"}
{"role":"tool","tool_call_id":"call_2","content":"2026-11-21T00:51:55+08:00"}
```

模型靠 `tool_call_id` 知道哪条结果属于哪次调用；有多个工具调用时尤其重要。Agent 执行完一个 `ToolCall` 后，用 `ToolCall.id` 作为 `Message.tool(toolCallId, content)` 的参数，再追加到历史。

它不是数据库外键：`messages.tool_call_id` 只是一列，真正的外键是 `message_tool_calls.message_id`。assistant 消息的 `toolCallId` 为 null；重新发送历史时必须原样保留这个值，否则模型无法把结果和调用配对。

## 为什么拆 append / compact

`active` 是逻辑截断标志。普通运行不应该碰它；只有压缩才需要“停用旧消息 + 插入摘要 + 插入新消息”。

如果拆成 `deactivateAll(id)` + `append(...)`：

1. `deactivateAll` 成功；
2. `append` 失败；
3. 摘要没落库，旧历史已经不可见——数据丢失。

所以 `compact` 是一个显式的原子操作，而不是 `save` 内部的隐式行为。

## 领域对象

```java
Conversation {
    long id;
    List<Message> messages;   // 仅 active，按 id 升序
    int contextTokens;
}

Message {
    MessageRole role;
    String content;
    String reasoningContent;
    String toolCallId;
    List<ToolCall> toolCalls;
}

ToolCall {
    String id;
    String type;
    String name;
    String arguments;         // 原始 JSON 字符串，不解析
}
```

`Message` 用静态工厂按角色校验：

- `system(content)`
- `user(content)`
- `assistant(content, reasoningContent, toolCalls)`
- `tool(toolCallId, content)`

system 提示词不持久化；`Conversation.messages` 只含 user/assistant/tool（包括作为摘要的 assistant）。

## 逻辑截断

`messages.active`：

- 默认 true；
- `compact` 把当前 active 全部置 false；
- 摘要与新消息以 active=true 插入；
- `load` 只查 `active = true`，按 `id` 升序；
- 旧行保留，可审计/回滚；
- `id` 单调递增，不复用。

## ER / DO

| 表 | DO | 关键字段 |
|---|---|---|
| `conversations` | `ConversationDO` | `id` PK、`context_tokens`、`created_at`、`updated_at` |
| `messages` | `MessageDO` | `id` PK、`conversation_id` FK、`role`、`content`、`reasoning_content`、`tool_call_id`、`active`、`created_at`、`updated_at` |
| `message_tool_calls` | `ToolCallDO` | `id` PK、`message_id` FK、`call_index`、`call_id`、`call_type`、`function_name`、`arguments`、`created_at` |

规则：

- 单一 `id` 主键，不用复合主键；
- 不设 `version`；冲突由调用方/仓储决定；
- `ToolCallDO` 不存 `conversationId`，会话通过 `message_id` 推导；
- `messages.role` 在 MySQL 8 是保留字，实际建表/SQL 需引用；不想转义可用 `message_role`，DO 字段仍叫 `role`；
- `arguments` 存文本，不存 JSON 类型：模型生成的 arguments 不保证合法 JSON；
- `created_at` / `updated_at` 由数据库维护。

## load 的查询

```sql
select id, context_tokens
from conversations
where id = ?;

select id, conversation_id, role, content, reasoning_content,
       tool_call_id, active, created_at, updated_at
from messages
where conversation_id = ? and active = true
order by id;

select tc.*
from message_tool_calls tc
join messages m on m.id = tc.message_id
where m.conversation_id = ? and m.active = true
order by tc.message_id, tc.call_index;
```

## append / compact 的 SQL 骨架

```sql
-- append（一个事务）
-- 会话行不存在时先创建；按数据库语法用 upsert / insert-if-absent
insert into conversations (id, context_tokens, created_at, updated_at)
values (?, ?, ?, ?)
on conflict (id) do update set context_tokens = excluded.context_tokens,
                               updated_at = excluded.updated_at;

insert into messages (conversation_id, role, content, reasoning_content,
                      tool_call_id, active, created_at, updated_at)
values (?, ?, ?, ?, ?, true, ?, ?);
-- 以及工具调用行

-- compact（一个事务）
-- 会话行不存在时同样先创建
insert into conversations (id, context_tokens, created_at, updated_at)
values (?, ?, ?, ?)
on conflict (id) do nothing;

update messages set active = false, updated_at = ?
where conversation_id = ? and active = true;

insert into messages (...) values (...);   -- 摘要 + pending
insert into message_tool_calls (...) values (...);
update conversations set context_tokens = ?, updated_at = ? where id = ?;
```

上面的 `on conflict` 是 PostgreSQL 写法；MySQL 用 `on duplicate key update` 或先 `insert ... select ... where not exists`。关键是写路径必须保证 `conversations` 行存在，否则 `update conversations` 会影响 0 行。

`compact` 中先停用旧 active，再插入摘要和新消息；摘要的 `id` 会大于旧行，`load` 按 id 排序时自然排在最前。

## 内存实现

`InMemoryConversationRepository`：

- 存储 `conversationId -> StoredConversation`；
- `load` 过滤 active、按 id 排序、返回不可变快照；
- `append` 在会话不存在时创建存储，再插入；`compact` 先创建（如需要），标记旧 active 为 false，再插入；
- 线程安全（按会话同步）；
- 只用于示例/测试，进程重启丢失。

## 接入 MySQL / PostgreSQL

1. 按上面的 ER 建表；
2. 实现 `ConversationRepository`；
3. `append` / `compact` 各自放在一个事务里；
4. 在 `model.convert` 的 Converter 中把 DO ↔ 领域对象映射；
5. 不要把 DO 暴露给 `agent`；
6. 同一 `conversationId` 的并发由调用方保证；如果要多实例安全，再在仓储层加 version 或外部锁（本版不提供）。

## 与其他方案的区别

- 不保存 system 提示词：它属于 Agent 配置，每次重新加入；
- 不保存 `context` 摘要字段：摘要就是一条 assistant 消息；
- 不做物理删除：`active=false` 保留审计；
- 不在框架层加会话锁：并发边界由调用方和仓储承担。
