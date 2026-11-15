# LumiRose v1 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 `docs/requirements/v1.md` 把现有 demo 收敛为可嵌入的最小单 Agent 框架：同步循环、类型化工具、四个文件工具、内存仓储、错误码与日志，全部用离线测试覆盖。

**Architecture:** 自底向上重写。先落异常与消息模型，再改仓储与工具层，最后重写 Client 与 Agent。Client 只管 HTTP 与响应映射，Agent 只管轮次与工具编排，工具通过 `Workspace` 限制在工作区内。全部测试走 JDK `HttpServer` 假服务，不依赖网络与真实 Key。

**Tech Stack:** Java 21、Maven 3.9、OkHttp 4.12.0、Jackson 2.18.3、SLF4J 2.0.17、JUnit 5.12.2、Logback 1.5.38（仅测试）。

**Spec:** `docs/requirements/v1.md`（R01–R31、§11 测试案例 TC-*）

## Global Constraints

- Java 21：`maven.compiler.release=21`，禁止 preview 特性。
- 构建命令固定 `mvn -o test`，必须离线通过；不得新增 §R29 以外的依赖。
- 运行时依赖：okhttp 4.12.0、jackson-databind 2.18.3、slf4j-api 2.0.17。
- 测试依赖：junit-jupiter 5.12.2、logback-classic 1.5.38。插件：maven-compiler-plugin 3.13.0、surefire 3.5.6。
- 包前缀 `io.github.xxxyjade.lumirose`；4 空格缩进；源文件 UTF-8。
- `Tool.execute` 不声明受检异常。
- 异常消息与日志不得包含 API Key、消息内容、工具参数、工具结果、响应体原文。
- 测试类放在 `src/test/java/io/github/xxxyjade/lumirose/<package>/`，方法名以 `tc_` 加用例编号小写开头。
- 每个任务结束时 `mvn -o test` 必须全绿；一个任务一次提交。

## 参考实现与取舍

参照 [LangChain4j](https://github.com/langchain4j/langchain4j)、[Spring AI](https://github.com/spring-projects/spring-ai) 的公开源码，以及两者工具执行相关的 issue（[#3023](https://github.com/spring-projects/spring-ai/issues/3023)、[#2857](https://github.com/spring-projects/spring-ai/issues/2857)）。

| 参考 | 借鉴 | 不借鉴 |
|---|---|---|
| LangChain4j `dev.langchain4j.exception` | 统一 `LangChain4jException` 基类、message/cause 构造器；`HttpException` 携带 `statusCode`；`ToolExecutionException` 携带出错上下文 | 按 HTTP 状态拆 6 个异常子类（单 provider、无重试，18 个错误码够用）；`RetriableException` 独立标记类（改为 `ClientException.retriable()`） |
| LangChain4j `ChatMessage` / `AiMessage` / `FinishReason` | 接口声明消息角色；assistant 文本可空；`FinishReason` 枚举承载判定 | parts/content 列表模型（v1 只有纯文本） |
| Spring AI `AssistantMessage` / `ToolExecutionException` | 构造期校验；工具异常携带出错工具 | 工具异常回传给模型（与 R10 冲突）；parts 消息模型 |

本计划在原 16 任务骨架上只做内部设计升级：异常补 `httpStatus`/`retriable`/`toolName`，新增 `FinishReason` 枚举，schema 生成抽成 `ToolSchemaFactory`，`role()` 上移到消息接口。需求文档的公开 API 与错误码不变。

## Review Focus

- `tool_calls[]` 元素缺 `function` 或 `function.name` 为空：应抛 `AGENT_RESPONSE_INVALID`，不能 NPE —— 测试加到 Task 13（TC-A24）。
- Windows 风格绝对路径 `C:\x`、UNC 路径 `\\server\share`：应 `TOOL_PATH_INVALID` —— 测试加到 Task 6（TC-F20）。
- 工具参数含转义引号、中文、换行：应按 JSON 语义正确解析 —— 测试加到 Task 4（TC-T12）。
- 响应含多个 `choices`：只取第一个，不拼接、不报错 —— 测试加到 Task 12（TC-A25）。
- 工具返回 100KB 文本：作为 tool 消息内容不截断、不崩溃 —— 测试加到 Task 13（TC-A26）。
- `finish_reason` 为未知值或 null：应归 `UNKNOWN` 并按 R09 抛 3003，不能 NPE —— 测试加到 Task 2（TC-M07）与 Task 13（TC-A27）。

## 文件结构

```text
src/main/java/io/github/xxxyjade/lumirose/
  client/LumiRoseClient.java                 请求组装、HTTP、错误映射
  agent/LumiRoseAgent.java                   轮次编排、工具执行、原子提交
  exception/                                 ErrorCode、18 个码、1 基类 + 5 子类
  model/chat/completion/                     消息与响应 DTO
  model/chat/completion/FinishReason.java    判定枚举
  repository/MessageRepository.java          load / appendAll
  repository/InMemoryMessageRepository.java  线程安全内存实现
  tool/Tool.java                             工具接口与 of 工厂
  tool/context/ToolContext.java              注册、解析、执行
  tool/args/ToolParameters.java              schema 入口（委托）
  tool/args/ToolSchemaFactory.java           反射生成 schema
  tool/args/{Read,Write,Edit,Delete}ToolParameters.java
  tool/fs/Workspace.java                     工作区路径守卫
  tool/fs/AtomicWrite.java                   临时文件 + 原子替换
  tool/{Read,Write,Edit,Delete}Tool.java
src/test/java/io/github/xxxyjade/lumirose/
  testing/{FakeServer,RecordingTool,TestLogs}.java
  exception/ExceptionMapTest.java
  model/MessageModelTest.java
  repository/RepositoryTest.java
  tool/ToolContextTest.java
  tool/TimeToolTest.java
  tool/FileToolTest.java
  tool/args/SchemaTest.java
  client/{ClientConfigTest,RequestShapeTest,HttpResponseTest}.java
  agent/{AgentRunTest,AgentDecisionTest,AgentToolLoopTest,AgentTurnTest}.java
  logging/LoggingTest.java
```

## 任务总览

| 任务 | 交付物 | 用例 |
|---|---|---|
| 1 | 构建基线 + 异常体系 | TC-E01–E03 |
| 2 | 消息模型 + FinishReason | TC-M01–M07 |
| 3 | 仓储契约 | TC-D01–D05 |
| 4 | Tool 接口 + ToolContext | TC-T01–T12、TC-N01 |
| 5 | 工具 schema | TC-S01–S10 |
| 6 | Workspace + ReadTool | TC-F01–F07、F19、F20 |
| 7 | WriteTool + 原子写 | TC-F08–F11、F18 |
| 8 | EditTool | TC-F12–F14 |
| 9 | DeleteTool | TC-F15–F17 |
| 10 | Client 配置与请求 | TC-C01–C08、TC-Q01–Q06 |
| 11 | Client 错误映射 | TC-H01–H08 |
| 12 | Agent 主流程与提交 | TC-A01–A06、A09、A25 |
| 13 | Agent 决策与工具循环 | TC-A07、A10–A19、A24、A26、A27 |
| 14 | Agent 轮次上限 | TC-A08、A20–A23 |
| 15 | 日志 | TC-L01–L02 |
| 16 | 清理与全量验收 | 全部 |

---

### Task 1: 构建基线 + 异常体系

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/io/github/xxxyjade/lumirose/exception/{ConfigurationException,ClientException,AgentException,ToolException,StorageException}.java`
- Rewrite: `src/main/java/io/github/xxxyjade/lumirose/exception/{LumiRoseException,LumiRoseErrorCode}.java`
- Delete: `src/test/java/{AgentTest,LumiRoseClientTest,SerializerTest,Test001,Test002,Test003,Test004,Test005}.java`、`src/test/java/test.txt`
- Test: `src/test/java/io/github/xxxyjade/lumirose/exception/ExceptionMapTest.java`

**Interfaces:**
- Produces：`LumiRoseException(ErrorCode)`、`(ErrorCode, String)`、`(ErrorCode, String, Throwable)`，public；`ErrorCode getErrorCode()`。
- Produces：`ClientException(ErrorCode, String)`、`(ErrorCode, int httpStatus, String)`、`(ErrorCode, String, Throwable)`、`(ErrorCode, Integer httpStatus, String, Throwable)`；`Integer httpStatus()`；`boolean retriable()`（429、5xx、`CLIENT_IO_ERROR` 为 true）。
- Produces：`ToolException(ErrorCode, String)`、`(ErrorCode, String toolName, String message)`、`(ErrorCode, String toolName, String message, Throwable cause)`；`String toolName()`。
- Produces：`LumiRoseErrorCode` 18 个常量，码值见需求 §8。

- [ ] **Step 1: 写失败测试（TC-E01–E03）**

```java
class ExceptionMapTest {
    @Test
    void tc_e01_everyCodeMapsToItsExceptionClass() {
        assertMapping(LumiRoseErrorCode.CONFIG_INVALID, new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID));
        assertMapping(LumiRoseErrorCode.PARAM_INVALID, new AgentException(LumiRoseErrorCode.PARAM_INVALID));
        // 其余 16 条按需求 §8 的表补全
    }

    private void assertMapping(ErrorCode code, LumiRoseException ex) {
        assertEquals(code, ex.getErrorCode());
        assertEquals(code.message(), ex.getMessage());
    }

    @Test
    void tc_e02_clientExceptionCarriesStatusAndRetriable() {
        var throttled = new ClientException(LumiRoseErrorCode.CLIENT_HTTP_ERROR, 429, "模型请求失败");
        assertEquals(429, throttled.httpStatus());
        assertTrue(throttled.retriable());

        var invalid = new ClientException(LumiRoseErrorCode.CLIENT_HTTP_ERROR, 401, "模型请求失败");
        assertFalse(invalid.retriable());

        var io = new ClientException(LumiRoseErrorCode.CLIENT_IO_ERROR, "网络异常", new IOException("refused"));
        assertTrue(io.retriable());
    }

    @Test
    void tc_e03_toolExceptionCarriesToolName() {
        var ex = new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, "read_file", "boom");
        assertEquals("read_file", ex.toolName());
        assertEquals(4003, ex.getErrorCode().code());
    }
}
```

- [ ] **Step 2: 改 pom 并删旧测试**

依赖块：

```xml
<dependencies>
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>
        <version>4.12.0</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.18.3</version>
    </dependency>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.17</version>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.12.2</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.5.38</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

`properties` 改为 `<maven.compiler.release>21</maven.compiler.release>`，删除 `maven.compiler.source`/`target`；`build` 里删掉旧的 compiler 配置，声明 compiler 3.13.0 与 surefire 3.5.6。删除列出的 7 个旧测试类与 `test.txt`。

- [ ] **Step 3: 跑测试确认失败**

Run: `mvn -o test`
Expected: 编译失败，`cannot find symbol: class ConfigurationException`。

- [ ] **Step 4: 实现异常**

`LumiRoseErrorCode` 换成 18 个常量；`LumiRoseException` 三构造器 + `getErrorCode()`；五个子类按 Interfaces 提供构造器；`ClientException.retriable()` 按状态码与错误码计算；`ToolException` 保存 `toolName`。

- [ ] **Step 5: 跑测试确认通过**

Run: `mvn -o test`
Expected: BUILD SUCCESS。

- [ ] **Step 6: 提交**

```bash
git add pom.xml src/main/java/io/github/xxxyjade/lumirose/exception src/test/java
git commit -m "build: adopt Java 21 and add exception hierarchy with context"
```

---

### Task 2: 消息模型 + FinishReason

**Files:**
- Modify: `model/chat/completion/ChatCompletionAssistantMessageParam.java`、`ChatCompletionToolMessageParam.java`、`ChatCompletionMessage.java`、`ChatCompletionMessageParam.java`
- Create: `model/chat/completion/FinishReason.java`
- Test: `src/test/java/io/github/xxxyjade/lumirose/model/MessageModelTest.java`

**Interfaces:**
- Produces：`ChatCompletionMessageParam` 增加 `String role();`（四个实现类各自返回常量）。
- Produces：`FinishReason.from(String)` → `STOP`、`LENGTH`、`TOOL_CALLS`、`CONTENT_FILTER`、`INSUFFICIENT_SYSTEM_RESOURCE`、`UNKNOWN`；null 与未知值归 `UNKNOWN`。
- Produces：`ChatCompletionAssistantMessageParam.Builder.build()` 接受 `content=null`；`ChatCompletionToolMessageParam.Builder.build()` 对空白 `toolCallId` 抛 `AgentException(PARAM_INVALID)`；`ChatCompletionMessage.Builder.build()` 只要求 `role` 非空。

- [ ] **Step 1: 写失败测试（TC-M01–M07）**

```java
@Test
void tc_m01_assistantNullContentKeepsToolCalls() {
    var msg = ChatCompletionAssistantMessageParam.builder()
            .content(null).toolCalls(List.of(toolCall())).build();
    JsonNode json = mapper.valueToTree(msg);
    assertTrue(json.get("content").isNull());
    assertEquals(1, json.get("tool_calls").size());
}

@Test
void tc_m07_finishReasonMapping() {
    assertEquals(FinishReason.TOOL_CALLS, FinishReason.from("tool_calls"));
    assertEquals(FinishReason.LENGTH, FinishReason.from("length"));
    assertEquals(FinishReason.STOP, FinishReason.from("stop"));
    assertEquals(FinishReason.CONTENT_FILTER, FinishReason.from("content_filter"));
    assertEquals(FinishReason.INSUFFICIENT_SYSTEM_RESOURCE, FinishReason.from("insufficient_system_resource"));
    assertEquals(FinishReason.UNKNOWN, FinishReason.from("weird"));
    assertEquals(FinishReason.UNKNOWN, FinishReason.from(null));
}

@Test
void tc_m04_toolMessageRejectsBlankId() {
    var ex = assertThrows(AgentException.class,
            () -> ChatCompletionToolMessageParam.builder().content("x").build());
    assertEquals(1002, ex.getErrorCode().code());
}
```

M02 `tool_calls` 为空不输出字段；M03 `reasoning_content` 为空不输出；M05 反序列化需求 §7.2 响应成功；M06 tool 消息序列化与 §7.4 一致。

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest=MessageModelTest`
Expected: 编译失败（`FinishReason` 不存在）。

- [ ] **Step 3: 实现**

接口加 `role()`；`FinishReason` 用 `switch` 映射（null 安全）；删除 assistant builder 的 content 非空校验；tool builder 加 `toolCallId` 空白校验；response message builder 删除 content 校验。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest=MessageModelTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/model src/test/java/io/github/xxxyjade/lumirose/model
git commit -m "feat(model): nullable assistant content and FinishReason enum"
```

---

### Task 3: 仓储契约

**Files:**
- Modify: `repository/MessageRepository.java`、`repository/InMemoryMessageRepository.java`、`agent/LumiRoseAgent.java`（仅调用点）
- Test: `src/test/java/io/github/xxxyjade/lumirose/repository/RepositoryTest.java`

**Interfaces:**
- Produces：`List<ChatCompletionMessageParam> load(Long)`；`void appendAll(Long, List<ChatCompletionMessageParam>)`。
- 兼容：过渡期把 Agent 的 `append(id, x)` 改为 `appendAll(id, List.of(x))`。

- [ ] **Step 1: 写失败测试（TC-D01–D05）**

```java
@Test
void tc_d01_emptyConversationReturnsEmptyImmutableList() {
    var list = new InMemoryMessageRepository().load(1L);
    assertTrue(list.isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> list.add(null));
}

@Test
void tc_d04_concurrentAppendsKeepAllMessages() throws Exception {
    var repo = new InMemoryMessageRepository();
    var pool = Executors.newFixedThreadPool(8);
    for (int i = 0; i < 8; i++) {
        pool.submit(() -> { for (int j = 0; j < 100; j++) repo.appendAll(1L, List.of(user("m"))); });
    }
    pool.shutdown();
    assertTrue(pool.awaitTermination(10, SECONDS));
    assertEquals(800, repo.load(1L).size());
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest=RepositoryTest`
Expected: 编译失败（`appendAll` 不存在）。

- [ ] **Step 3: 实现**

接口只留 `load`/`appendAll`；实现用 `ConcurrentHashMap` + 会话级 `synchronized`，`load` 返回 `List.copyOf`，空列表 no-op；改 Agent 调用点。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest=RepositoryTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/repository src/main/java/io/github/xxxyjade/lumirose/agent src/test/java/io/github/xxxyjade/lumirose/repository
git commit -m "refactor(repository): atomic appendAll with immutable snapshots"
```

---

### Task 4: Tool 接口 + ToolContext

**Files:**
- Modify: `tool/Tool.java`、`tool/TimeTool.java`、`tool/context/ToolContext.java`
- Delete: `tool/ReatTool.java`
- Test: `src/test/java/io/github/xxxyjade/lumirose/tool/ToolContextTest.java`、`src/test/java/io/github/xxxyjade/lumirose/tool/TimeToolTest.java`

**Interfaces:**
- Produces：`Tool.of(String, String, ToolParameters, Function<ToolParameters,String>)`；`Tool.execute(ToolParameters)` 无 `throws`。
- Produces：`ToolContext.builder().register(Tool).registerAll(List<Tool>).build()`；`List<Tool> tools()`；`String execute(String, String)`；错误码 4001–4003，`ToolException.toolName()` 为出错工具。
- 保留：`TimeTool` 无参构造，`name()` 为 `get_current_time`，返回系统默认时区的 `ISO_OFFSET_DATE_TIME`（精确到秒）。

- [ ] **Step 1: 写测试（TC-T01–T12 为红，TC-N01 是保留 TimeTool 的回归用例）**

```java
@Test
void tc_t10_toolThrowWrappedWithName() {
    var ctx = ToolContext.builder().register(exploding("boom_tool")).build();
    var ex = assertThrows(ToolException.class, () -> ctx.execute("boom_tool", "{}"));
    assertEquals(4003, ex.getErrorCode().code());
    assertEquals("boom_tool", ex.toolName());
    assertInstanceOf(IllegalStateException.class, ex.getCause());
}

@Test
void tc_t12_escapedArgumentsParse() {
    // 参数 {"path":"a\"b.txt","text":"第一行\n第二行"} 应正确解析，不被截断
}
```

其余：T01 `Tool.of` 四参数 null；T02/T03/T11 注册校验；T05 未注册；T06 非法 JSON 保 cause；T07 未知字段忽略；T08 null/空串按 `{}`；T09 异常透传。

`TimeToolTest`（TC-N01）：调用 `new TimeTool().execute(new EmptyParameters())`，断言返回值匹配 `yyyy-MM-ddTHH:mm:ss±HH:mm`、`ZonedDateTime.parse` 可解析、纳秒为 0；`parametersClass()` 为 `EmptyParameters.class`。

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest='ToolContextTest,TimeToolTest'`
Expected: 编译失败（`Tool.of` 参数数量不符）。

- [ ] **Step 3: 实现**

`Tool.execute` 去受检异常；`of` 四参数并用 `parameters.getClass()` 推导；`ToolContext` 用 `LinkedHashMap` + `List.copyOf`；错误映射见 R16，`ToolException` 带上工具名；`ObjectMapper` 关闭 `FAIL_ON_UNKNOWN_PROPERTIES`；`TimeTool` 去掉 `throws IOException`（行为不变，保留为正式内置工具）；删除 `ReatTool`。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest='ToolContextTest,TimeToolTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/tool src/test/java/io/github/xxxyjade/lumirose/tool
git commit -m "feat(tool): typed tool context with contextual errors"
```

---

### Task 5: 工具 schema

**Files:**
- Create: `tool/args/ToolSchemaFactory.java`
- Modify: `tool/args/ToolParameters.java`（只留委托）
- Test: `src/test/java/io/github/xxxyjade/lumirose/tool/args/SchemaTest.java`

**Interfaces:**
- Produces：`ToolSchemaFactory.generate(Class<? extends ToolParameters>) -> JsonNode`；`ToolParameters.toSchema()` 默认实现转调它。规则见需求 R18。

- [ ] **Step 1: 写失败测试（TC-S01–S10）**

测试包为 `io.github.xxxyjade.lumirose.tool.args`，直接调 `ToolSchemaFactory.generate(Xxx.class)`；参数类定义在测试内：标量、枚举、`List<String>`、嵌套、required、无注解、不支持类型、自引用。断言用 `assertEquals(expectedJson, actual)` 整树比较。

```java
@Test
void tc_s02_enumBecomesStringWithValues() {
    JsonNode schema = ToolSchemaFactory.generate(EnumParams.class);
    assertEquals("string", schema.at("/properties/unit/type").asText());
    assertEquals("[\"A\",\"B\"]", schema.at("/properties/unit/enum").toString());
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest=SchemaTest`
Expected: 编译失败（`ToolSchemaFactory` 不存在）。

- [ ] **Step 3: 实现**

按 R18 生成：字段过滤、类型映射、枚举、`List<标量>`/标量数组、嵌套递归、自引用环检测、不支持类型抛 1001、required 只认注解；`required` 为空不输出。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest=SchemaTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/tool/args src/test/java/io/github/xxxyjade/lumirose/tool/args
git commit -m "feat(tool): dedicated schema factory"
```

---

### Task 6: Workspace + ReadTool

**Files:**
- Create: `tool/fs/Workspace.java`、`tool/ReadTool.java`
- Rewrite: `tool/args/ReadToolParameters.java`
- Test: `src/test/java/io/github/xxxyjade/lumirose/tool/FileToolTest.java`

**Interfaces:**
- Produces：`Workspace(Path root)`（不存在/非目录抛 1001）；`Path existingFile(String)`；`Path writeTarget(String)`。
- Produces：`ReadTool(Path workspace)`；`ReadToolParameters.builder().path(String).build()`。

- [ ] **Step 1: 写失败测试（TC-F01–F07、F19、F20）**

```java
@TempDir Path tmp;

@Test
void tc_f20_windowsAbsoluteAndUncRejected() {
    var tool = new ReadTool(tmp);
    for (String p : List.of("C:\\x.txt", "\\\\server\\share\\x.txt")) {
        var ex = assertThrows(ToolException.class,
                () -> tool.execute(ReadToolParameters.builder().path(p).build()));
        assertEquals(4004, ex.getErrorCode().code());
    }
}
```

F01 workspace 指向文件 → 1001；F02 中文读取；F03 不存在 → 4005；F04 目录 → 4004；F05 `../` → 4004；F06 绝对路径 → 4004；F07 符号链接越界 → 4004（无权限时退回目录联接）；F19 缺 `path` → 4002。

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest=FileToolTest`
Expected: 编译失败（`ReadTool` 不存在）。

- [ ] **Step 3: 实现**

`Workspace` 构造时 `toRealPath` 并校验目录；`existingFile` 做相对路径、归一化、存在性、普通文件、真实路径包含性；`writeTarget` 校验父目录真实路径。`ReadTool` 读取后原样返回，IO → 4003，参数缺失 → 4002。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest=FileToolTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/tool src/test/java/io/github/xxxyjade/lumirose/tool/FileToolTest.java
git commit -m "feat(tool): workspace-scoped read_file"
```

---

### Task 7: WriteTool + 原子写

**Files:**
- Create: `tool/fs/AtomicWrite.java`、`tool/WriteTool.java`、`tool/args/WriteToolParameters.java`
- Test: `src/test/java/io/github/xxxyjade/lumirose/tool/FileToolTest.java`（追加）

**Interfaces:**
- Produces：`AtomicWrite.write(Path target, String content)`（同目录临时文件 + `ATOMIC_MOVE`，失败清理）。
- Produces：`WriteTool(Path workspace)`；参数 `path`、`content`、`overwrite`（默认 false）。

- [ ] **Step 1: 写失败测试（TC-F08–F11、F18）**

```java
@Test
void tc_f18_noTempFileLeftBehind() throws Exception {
    new WriteTool(tmp).execute(WriteToolParameters.builder()
            .path("b.txt").content("x").overwrite(true).build());
    try (var s = Files.list(tmp)) {
        assertEquals(List.of("b.txt"), s.map(p -> p.getFileName().toString()).sorted().toList());
    }
}
```

F08 新建；F09 已存在且未授权 → 4006 且原内容不变；F10 `overwrite=true` 覆盖；F11 父目录缺失 → 4004。

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest=FileToolTest`
Expected: 编译失败（`WriteTool` 不存在）。

- [ ] **Step 3: 实现**

`writeTarget` 拿路径；已存在且 `overwrite=false` → 4006；父目录不存在 → 4004；临时文件 `.{name}.lumirose-tmp-{uuid}`，`ATOMIC_MOVE` 覆盖，`AtomicMoveNotSupportedException` 回退普通 move，finally 清理。返回 `Wrote {bytes} bytes to {path}.`。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest=FileToolTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/tool src/test/java/io/github/xxxyjade/lumirose/tool/FileToolTest.java
git commit -m "feat(tool): atomic write_file"
```

---

### Task 8: EditTool

**Files:**
- Create: `tool/EditTool.java`、`tool/args/EditToolParameters.java`
- Test: `src/test/java/io/github/xxxyjade/lumirose/tool/FileToolTest.java`（追加）

**Interfaces:**
- Produces：`EditTool(Path workspace)`；参数 `path`、`old_string`、`new_string`；成功返回 `Replaced 1 occurrence in {path}.`。

- [ ] **Step 1: 写失败测试（TC-F12–F14）**

```java
@Test
void tc_f13_multipleMatchesRejected() throws Exception {
    Files.writeString(tmp.resolve("a.txt"), "foo foo");
    var ex = assertThrows(ToolException.class, () -> edit("a.txt", "foo", "bar"));
    assertEquals(4008, ex.getErrorCode().code());
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest=FileToolTest`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`existingFile` 读取；`indexOf` 字面量计数：0 → 4007，>1 → 4008；替换一次后 `AtomicWrite.write`；返回固定文案。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest=FileToolTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/tool src/test/java/io/github/xxxyjade/lumirose/tool/FileToolTest.java
git commit -m "feat(tool): strict edit_file"
```

---

### Task 9: DeleteTool

**Files:**
- Create: `tool/DeleteTool.java`、`tool/args/DeleteToolParameters.java`
- Test: `src/test/java/io/github/xxxyjade/lumirose/tool/FileToolTest.java`（追加）

**Interfaces:**
- Produces：`DeleteTool(Path workspace)`；成功返回 `Deleted {path}.`。

- [ ] **Step 1: 写失败测试（TC-F15–F17）**

```java
@Test
void tc_f16_directoryRejected() throws Exception {
    Files.createDirectory(tmp.resolve("d"));
    var ex = assertThrows(ToolException.class,
            () -> new DeleteTool(tmp).execute(DeleteToolParameters.builder().path("d").build()));
    assertEquals(4004, ex.getErrorCode().code());
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest=FileToolTest`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`existingFile` 后 `Files.delete`；目录/越界 4004；不存在 4005；返回固定文案。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest=FileToolTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/tool src/test/java/io/github/xxxyjade/lumirose/tool/FileToolTest.java
git commit -m "feat(tool): delete_file"
```

---

### Task 10: Client 配置与请求

**Files:**
- Create: `src/test/java/io/github/xxxyjade/lumirose/testing/FakeServer.java`
- Rewrite: `client/LumiRoseClient.java`
- Test: `client/ClientConfigTest.java`、`client/RequestShapeTest.java`

**Interfaces:**
- Produces：`FakeServer()`、`baseUrl()`、`enqueue(int, String)`、`requests()`、`lastRequest()`、`close()`；`RecordedRequest` 含 `method()`、`path()`、`header(String)`、`body()`。
- Produces：`LumiRoseClient.builder().baseUrl(...).apiKey(...).model(...).maxTokens(Integer).build()`；`ChatCompletionResponseParam chatCompletion(ChatCompletionRequestParam)`。

- [ ] **Step 1: 写失败测试（TC-C01–C08、TC-Q01–Q06）**

```java
@Test
void tc_q01_noToolsNoMaxTokens() {
    server.enqueue(200, """
        {"choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
        """);
    client.chatCompletion(ChatCompletionRequestParam.builder().messages(List.of(user("hi"))).build());
    JsonNode body = mapper.readTree(server.lastRequest().body());
    assertEquals(Set.of("model", "messages"), fieldNames(body));
    assertEquals("application/json; charset=utf-8", server.lastRequest().header("Content-Type"));
}
```

C08 用 `apiKey="sk-test-secret"` 触发 1001，断言消息不含该串。

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest='ClientConfigTest,RequestShapeTest'`
Expected: 编译失败（`FakeServer` 不存在）。

- [ ] **Step 3: 实现**

`FakeServer` 用 `HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)`，`/chat/completions` 读取并记录 body。Client 校验配置（1001）、URL 去尾斜杠、三个头、body 只放 `model`/`messages`/`tools`（非空）/`max_tokens`（非 null）；解析响应并校验 `choices` 非空、首个 `message` 非空（2002，`httpStatus=200`）。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest='ClientConfigTest,RequestShapeTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/client src/test/java/io/github/xxxyjade/lumirose/client src/test/java/io/github/xxxyjade/lumirose/testing
git commit -m "feat(client): request building and config validation"
```

---

### Task 11: Client 错误映射

**Files:**
- Modify: `client/LumiRoseClient.java`
- Test: `client/HttpResponseTest.java`

**Interfaces:**
- Produces：非 2xx → `ClientException(CLIENT_HTTP_ERROR, status, message)`（`httpStatus()`、`retriable()` 可用）；解析失败/空 body/结构非法 → `ClientException(CLIENT_RESPONSE_INVALID, 200, message)` 不带 cause；IO → `ClientException(CLIENT_IO_ERROR, message, cause)` 带 cause 且 `retriable()==true`。

- [ ] **Step 1: 写失败测试（TC-H01–H08）**

```java
@Test
void tc_h01_httpErrorKeepsCodeTypeAndStatus() {
    server.enqueue(401, "{\"error\":{\"message\":\"Authentication Fails\",\"type\":\"authentication_error\",\"code\":\"invalid_api_key\"}}");
    var ex = assertThrows(ClientException.class, () -> client.chatCompletion(request("hi")));
    assertEquals(2001, ex.getErrorCode().code());
    assertEquals(401, ex.httpStatus());
    assertFalse(ex.retriable());
    assertTrue(ex.getMessage().contains("HTTP 401"));
    assertTrue(ex.getMessage().contains("code=invalid_api_key"));
    assertFalse(ex.getMessage().contains("Authentication Fails"));
}

@Test
void tc_h03_rateLimitIsRetriableButNotRetried() {
    server.enqueue(429, "{}");
    var ex = assertThrows(ClientException.class, () -> client.chatCompletion(request("hi")));
    assertTrue(ex.retriable());
    assertEquals(1, server.requests().size());
}
```

H02 500 非 JSON；H04 空 body；H05 非法 JSON 且 cause 为 null；H06/H07 结构非法；H08 关闭端口 → 2003 + IOException cause + `retriable()`。

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest=HttpResponseTest`
Expected: 断言失败（错误被吞或状态缺失）。

- [ ] **Step 3: 实现**

非 2xx 安全解析 `error.code`/`error.type`（失败则忽略），消息按 R03 拼接，带上状态码；2xx 的 body 为空/解析失败/结构非法统一 2002、不挂 cause；`IOException` 统一 2003。每次 `Response` try-with-resources 关闭，body 只读一次（R06）。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest=HttpResponseTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/client src/test/java/io/github/xxxyjade/lumirose/client
git commit -m "feat(client): map HTTP and IO failures with status and retriable flag"
```

---

### Task 12: Agent 主流程与提交

**Files:**
- Rewrite: `agent/LumiRoseAgent.java`
- Create: `src/test/java/io/github/xxxyjade/lumirose/testing/RecordingTool.java`
- Test: `agent/AgentRunTest.java`

**Interfaces:**
- Produces：`LumiRoseAgent.builder().client(...).toolContext(...).repository(...).systemMessage(String).maxTurns(int).build()`；`String run(Long, String)`；`conversationId` 为 null 时生成正数随机 id，不返回。
- Produces：`RecordingTool(String name, String description)`，`willReturn(String)`、`willThrow(RuntimeException)`、`List<String> calls()`。

- [ ] **Step 1: 写失败测试（TC-A01–A06、A09、A25）**

```java
@Test
void tc_a03_secondRunLoadsCommittedHistory() {
    server.enqueue(200, stopResponse("hi"));
    assertEquals("hi", agent.run(1L, "你好"));
    assertEquals(3, repo.load(1L).size());

    server.enqueue(200, stopResponse("again"));
    agent.run(1L, "再来");
    assertEquals(4, json(server.lastRequest().body()).get("messages").size());
}

@Test
void tc_a09_storageFailureWrapped() {
    var failing = new MessageRepository() {
        public List<ChatCompletionMessageParam> load(Long id) { return List.of(); }
        public void appendAll(Long id, List<ChatCompletionMessageParam> m) { throw new IllegalStateException("boom"); }
    };
    // 断言 StorageException、码 5001、cause 保留
}
```

A25：响应 `choices` 两条，返回第一条 content。

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest=AgentRunTest`
Expected: 编译失败或行为不符。

- [ ] **Step 3: 实现**

按 §4.3、R07–R08、R11 重写：`content` 校验（1002）、`conversationId` 为 null 时生成正数随机 id；history 快照；首轮空历史加 system（默认 `You are a helpful assistant!`，`maxTurns` 默认 10）；消息列表 + 本轮缓冲；每轮 `tools` 由 `toolContext.tools()` 转 `ChatCompletionToolParam`；请求后取 `choices[0]`，用 `FinishReason.from(...)` 判定；`STOP` 分支提交并返回，非 `STOP` 暂时抛 3003（工具分支由 Task 13 补）；`load`/`appendAll` 的异常包成 `StorageException(5001)`；builder 校验三个必填（1001）。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest=AgentRunTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/agent src/test/java/io/github/xxxyjade/lumirose/agent src/test/java/io/github/xxxyjade/lumirose/testing
git commit -m "feat(agent): atomic run with configurable system message"
```

---

### Task 13: Agent 决策与工具循环

**Files:**
- Modify: `agent/LumiRoseAgent.java`
- Test: `agent/AgentDecisionTest.java`、`agent/AgentToolLoopTest.java`

**Interfaces:**
- 行为：R09 五级判定（`FinishReason` 枚举）；R10 顺序执行与 fail-fast；`tool_calls[].function` 缺失或 name 空白 → 3003；`executeToolCalls` 为私有方法。

- [ ] **Step 1: 写失败测试（TC-A07、A10–A19、A24、A26、A27）**

```java
@Test
void tc_a10_lengthIsTruncatedError() {
    server.enqueue(200, "{\"choices\":[{\"index\":0,\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"半截\"}}]}");
    var ex = assertThrows(AgentException.class, () -> agent.run(1L, "写长文"));
    assertEquals(3002, ex.getErrorCode().code());
}

@Test
void tc_a27_unknownFinishReasonRejected() {
    server.enqueue(200, "{\"choices\":[{\"index\":0,\"finish_reason\":\"weird\",\"message\":{\"role\":\"assistant\",\"content\":\"x\"}}]}");
    var ex = assertThrows(AgentException.class, () -> agent.run(1L, "hi"));
    assertEquals(3003, ex.getErrorCode().code());
}
```

A24 `tool_calls[0]` 无 `function`；A26 工具返回 100KB 字符串且第二轮 tool 消息长度一致；A18 fail-fast 只执行第一个。

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest='AgentDecisionTest,AgentToolLoopTest'`
Expected: 断言失败。

- [ ] **Step 3: 实现**

判定顺序：`LENGTH` → 3002；`tool_calls` 非空 → 先追加 assistant 再顺序执行工具（结果 null 变空串，`tool_call_id` 取自调用）→ 继续；否则 `finishReason != STOP` → 3003；`content` 空 → 3003；返回。`tool_calls[].function` 缺 name → 3003。工具异常直接上抛。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest='AgentDecisionTest,AgentToolLoopTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/agent src/test/java/io/github/xxxyjade/lumirose/agent
git commit -m "feat(agent): decision table and tool loop"
```

---

### Task 14: Agent 轮次上限

**Files:**
- Modify: `agent/LumiRoseAgent.java`
- Test: `agent/AgentTurnTest.java`

**Interfaces:**
- 行为：R12；`maxTurns < 1` 在 `build()` 抛 1001。

- [ ] **Step 1: 写失败测试（TC-A08、A20–A23）**

```java
@Test
void tc_a20_turnLimitStopsAndKeepsHistory() {
    for (int i = 0; i < 10; i++) server.enqueue(200, toolCallResponse());
    var ex = assertThrows(AgentException.class, () -> agent.run(1L, "转圈"));
    assertEquals(3001, ex.getErrorCode().code());
    assertEquals(10, server.requests().size());
    assertTrue(repo.load(1L).isEmpty());
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest=AgentTurnTest`
Expected: 断言失败。

- [ ] **Step 3: 实现**

每次请求计数 +1，循环条件 `turn < maxTurns`；正常结束才 `appendAll`；循环结束仍无最终文本 → 3001；builder 校验 `maxTurns < 1` → 1001。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest=AgentTurnTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/agent src/test/java/io/github/xxxyjade/lumirose/agent
git commit -m "feat(agent): bounded turns with explicit error"
```

---

### Task 15: 日志

**Files:**
- Modify: `agent/LumiRoseAgent.java`
- Create: `src/test/java/io/github/xxxyjade/lumirose/testing/TestLogs.java`
- Test: `logging/LoggingTest.java`

**Interfaces:**
- Produces：`TestLogs.attach()` 返回 `ListAppender<ILoggingEvent>`（挂在 `io.github.xxxyjade.lumirose`，级别 DEBUG）；`text(appender)`；`detach(appender)`。
- 行为：R28 的事件表与禁记字段。

- [ ] **Step 1: 写失败测试（TC-L01–L02）**

```java
@Test
void tc_l01_debugHasToolNameButNoPayload() {
    var appender = TestLogs.attach();
    readTool.willReturn("secret-content");
    agent.run(1L, "读一下");
    String logs = TestLogs.text(appender);
    assertTrue(logs.contains("read_file"));
    assertFalse(logs.contains("secret-content"));
    assertFalse(logs.contains("你好"));
    assertFalse(logs.contains("test-key"));
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -o test -Dtest=LoggingTest`
Expected: `logs.contains("read_file")` 失败。

- [ ] **Step 3: 实现**

按 R28 打点：运行开始/结束、每轮请求与响应、每次工具执行、ERROR 异常；只传元数据字段，不传内容或参数。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -o test -Dtest=LoggingTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/xxxyjade/lumirose/agent src/test/java/io/github/xxxyjade/lumirose/testing src/test/java/io/github/xxxyjade/lumirose/logging
git commit -m "feat: framework debug logging without payloads"
```

---

### Task 16: 清理与全量验收

**Files:**
- Modify: 全仓库（清死代码与残留注释）
- Test: 全部

**Interfaces:**
- 无新增。

- [ ] **Step 1: 清理**

删除 `LumiRoseAgent` 中注释掉的旧实现、未使用的 import；确认 `tool/ReatTool.java` 与旧 `main()` 测试不存在，`TimeTool` 保留；`pom.xml` 无 openai-java / reactor / hutool。

- [ ] **Step 2: 全量测试**

Run: `mvn -o test`
Expected: BUILD SUCCESS，所有测试类通过。

- [ ] **Step 3: 凭据与覆盖率检查**

```bash
git grep -nE "sk-[A-Za-z0-9]{16,}" -- src || echo "no keys"
```

Expected: `no keys`。再核对 §11 的 100 条用例与新增 9 条（A24–A27、F20、T12、M07、E02/E03），覆盖矩阵 R01–R32 无遗漏。

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "chore: final cleanup and full acceptance run"
```

---

## 依赖关系

- Task 2 依赖 Task 1；Task 3 依赖 Task 1；Task 4 依赖 Task 1、3；Task 5 依赖 Task 1。
- Task 6–9 依赖 Task 4、5；Task 10–11 依赖 Task 2、5；Task 12 依赖 Task 3、10、11；Task 13 依赖 Task 12、4、6–9；Task 14 依赖 Task 12；Task 15 依赖 Task 12、13；Task 16 依赖全部。
