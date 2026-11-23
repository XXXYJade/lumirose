package io.github.xxxyjade.lumirose.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseException;
import io.github.xxxyjade.lumirose.common.exception.ToolException;
import io.github.xxxyjade.lumirose.tool.impl.TimeTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具工厂、注册表、参数解析和异常映射测试。
 */
class ToolTest {

    @Test
    void ofRejectsNullArguments() {
        assertConfigInvalid(() -> Tool.of(null, "说明", new EmptyParameters(), p -> ""));
        assertConfigInvalid(() -> Tool.of("t", null, new EmptyParameters(), p -> ""));
        assertConfigInvalid(() -> Tool.of("t", "说明", null, p -> ""));
        assertConfigInvalid(() -> Tool.of("t", "说明", new EmptyParameters(), null));
    }

    @Test
    void parametersClassIsDerivedFromInstance() {
        EchoParams parameters = new EchoParams();
        Tool tool = Tool.of("echo", "回显", parameters, p -> "");

        assertSame(parameters, tool.parameters());
        assertSame(EchoParams.class, tool.parametersClass());
        assertEquals("echo", tool.name());
        assertEquals("回显", tool.description());
    }

    @Test
    void registerRejectsNullTool() {
        assertConfigInvalid(() -> ToolContext.builder().register(null));
    }

    @Test
    void registerRejectsDuplicateName() {
        ToolContext.Builder builder = ToolContext.builder().register(echo("dup", new AtomicReference<>()));

        assertConfigInvalid(() -> builder.register(echo("dup", new AtomicReference<>())));
    }

    @Test
    void registerAllRejectsNull() {
        assertConfigInvalid(() -> ToolContext.builder().registerAll(null));
    }

    @Test
    void toolsAreImmutableAndOrdered() {
        ToolContext context = ToolContext.builder()
                .register(echo("second", new AtomicReference<>()))
                .register(echo("first", new AtomicReference<>()))
                .build();

        assertEquals(List.of("second", "first"),
                context.tools().stream().map(Tool::name).toList());
        assertThrows(UnsupportedOperationException.class,
                () -> context.tools().add(echo("third", new AtomicReference<>())));
    }

    @Test
    void executeTreatsNullAndBlankArgumentsAsEmptyObject() {
        AtomicReference<String> seen = new AtomicReference<>("unset");
        ToolContext context = toolContext(echo("echo", seen));

        assertEquals("ok", context.execute("echo", null));
        assertNull(seen.get());

        seen.set("unset");
        assertEquals("ok", context.execute("echo", "  \n\t "));
        assertNull(seen.get());
    }

    @Test
    void executeRejectsMalformedJsonWithCause() {
        ToolContext context = toolContext(echo("echo", new AtomicReference<>()));

        ToolException error = assertThrows(ToolException.class,
                () -> context.execute("echo", "{\"text\": "));

        assertEquals(LumiRoseErrorCode.TOOL_ARGS_INVALID, error.getErrorCode());
        assertEquals("echo", error.toolName());
        assertTrue(error.getCause() instanceof IOException);
    }

    @Test
    void executeRejectsJsonNullArguments() {
        ToolContext context = toolContext(echo("echo", new AtomicReference<>()));

        ToolException error = assertThrows(ToolException.class,
                () -> context.execute("echo", "null"));

        assertEquals(LumiRoseErrorCode.TOOL_ARGS_INVALID, error.getErrorCode());
    }

    @Test
    void executeIgnoresUnknownFields() {
        AtomicReference<String> seen = new AtomicReference<>();
        ToolContext context = toolContext(echo("echo", seen));

        assertEquals("ok", context.execute("echo", "{\"text\":\"hello\",\"extra\":[1,2,3]}"));

        assertEquals("hello", seen.get());
    }

    @Test
    void executePassesThroughFrameworkException() {
        LumiRoseException original = new ToolException(
                LumiRoseErrorCode.TOOL_FILE_EXISTS, "boom", "文件已存在");
        ToolContext context = toolContext(Tool.of(
                "boom", "抛框架异常", new EmptyParameters(), p -> {
                    throw original;
                }));

        LumiRoseException error = assertThrows(LumiRoseException.class,
                () -> context.execute("boom", "{}"));

        assertSame(original, error);
    }

    @Test
    void executeWrapsUnexpectedException() {
        IllegalStateException cause = new IllegalStateException("bug");
        ToolContext context = toolContext(Tool.of(
                "explode", "抛运行时异常", new EmptyParameters(), p -> {
                    throw cause;
                }));

        ToolException error = assertThrows(ToolException.class,
                () -> context.execute("explode", "{}"));

        assertEquals(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, error.getErrorCode());
        assertEquals("explode", error.toolName());
        assertSame(cause, error.getCause());
    }

    @Test
    void executeRejectsUnknownTool() {
        ToolContext context = toolContext(echo("known", new AtomicReference<>()));

        ToolException error = assertThrows(ToolException.class,
                () -> context.execute("unknown", "{}"));

        assertEquals(LumiRoseErrorCode.TOOL_NOT_FOUND, error.getErrorCode());
        assertEquals("unknown", error.toolName());
    }

    @Test
    void timeToolReturnsSecondPrecisionOffsetTime() {
        TimeTool tool = new TimeTool();

        assertEquals("get_current_time", tool.name());
        assertEquals(EmptyParameters.class, tool.parametersClass());
        OffsetDateTime parsed = OffsetDateTime.parse(tool.execute(new EmptyParameters()));
        assertEquals(0, parsed.getNano());
    }

    private static ToolContext toolContext(Tool... tools) {
        return ToolContext.builder().registerAll(List.of(tools)).build();
    }

    private static Tool echo(String name, AtomicReference<String> seen) {
        return Tool.of(name, "回显参数", new EchoParams(), parameters -> {
            seen.set(((EchoParams) parameters).text);
            return "ok";
        });
    }

    private static void assertConfigInvalid(Executable executable) {
        LumiRoseException error = assertThrows(LumiRoseException.class, executable);
        assertEquals(LumiRoseErrorCode.CONFIG_INVALID, error.getErrorCode());
    }

    public static final class EchoParams implements ToolParameters {
        @JsonProperty("text")
        public String text;
    }
}
