# 工具

## 定义

```java
public interface Tool {
    String name();
    String description();
    ToolParameters parameters();
    Class<? extends ToolParameters> parametersClass();
    String execute(ToolParameters parameters);
}
```

用 `Tool.of` 创建：

```java
Tool time = Tool.of(
        "get_current_time",
        "返回当前时间",
        new EmptyParameters(),
        p -> ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
```

四个参数必须非 null，否则 `CONFIG_INVALID(1001)`。

## 注册

```java
ToolContext tools = ToolContext.builder()
        .register(new TimeTool())
        .register(myTool)
        .build();
```

- 名称不能重复；重复 → 1001；
- `tools()` 返回不可变列表，顺序等于注册顺序；
- `registerAll(null)` → 1001。

## 执行

模型返回 `tool_calls` 后，Agent 按数组顺序串行执行：

- `arguments` 为 null 或空白时按 `{}` 处理；
- 未知字段忽略；
- 未注册工具 → `TOOL_NOT_FOUND(4001)`；
- JSON 非法 → `TOOL_ARGS_INVALID(4002)`，保留 cause；
- 工具抛 `LumiRoseException` 原样透传；
- 其他异常 → `TOOL_EXECUTION_FAILED(4003)`，保留 cause；
- 任一工具失败，本次运行终止，后续工具不执行，历史不提交。

工具结果原样作为 tool 消息内容；本版不截断。

## Schema

Schema 由参数类反射生成：

- 根节点固定 `{"type":"object","properties":{...}}`；
- 只收录实例字段、非 transient/synthetic、带 `@JsonProperty`；
- 属性名取 `@JsonProperty.value()`，为空时取字段名；
- `@JsonPropertyDescription` 输出 `description`；
- `required` 只由 `@JsonProperty(required = true)` 声明。

类型映射：

| Java 类型 | 输出 |
|---|---|
| String、char/Character | string |
| 整数类型及包装类、BigInteger | integer |
| 浮点类型及包装类、BigDecimal | number |
| boolean/Boolean | boolean |
| enum | string + enum 常量名 |
| `List<标量>`、标量数组 | array |
| 自定义类 | 递归 properties |

以下类型在生成时抛 1001，不静默降级：`Map`、`Set`、`Optional`、`List<对象>`、对象数组、日期时间、递归自引用。

## 内置工具

| 工具 | 参数 | 行为 |
|---|---|---|
| `read_file` | `path` | 返回 UTF-8 全文 |
| `write_file` | `path`、`content`、`overwrite` | 默认不覆盖；临时文件 + 原子移动 |
| `edit_file` | `path`、`old_string`、`new_string` | old_string 必须恰好一次 |
| `delete_file` | `path` | 只删普通文件 |
| `get_current_time` | 无 | 系统默认时区 ISO_OFFSET_DATE_TIME，精确到秒 |

文件工具构造时传入工作区 `Path`；路径必须存在且是目录，否则 1001。

## 路径安全

四个文件工具共用：

- 只接受相对路径；绝对路径 → 4004；
- `workspace.resolve(path).normalize()` 必须在工作区内；
- 目标存在时 `toRealPath()` 必须在区内；
- 目标不存在时父目录 `toRealPath()` 必须在区内；
- 符号链接越界 → 4004。

## 错误码

| 码 | 场景 |
|---|---|
| 4001 | 未注册工具 |
| 4002 | 参数 JSON 非法，或工具自检参数失败 |
| 4003 | 工具执行抛异常、文件读写失败 |
| 4004 | 路径越界、绝对路径、目标为目录 |
| 4005 | 文件不存在 |
| 4006 | 文件已存在且未授权覆盖 |
| 4007 | 编辑目标未匹配 |
| 4008 | 编辑目标多处匹配 |

## 自定义工具示例

```java
public final class EchoParameters implements ToolParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("要回显的文本")
    public String text;
}

Tool echo = Tool.of(
        "echo",
        "原样返回输入",
        new EchoParameters(),
        p -> ((EchoParameters) p).text);
```

`parametersClass()` 由参数实例推导，不需要外部传入。
