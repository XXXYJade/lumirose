package io.github.xxxyjade.lumirose.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.xxxyjade.lumirose.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.exception.ToolException;
import io.github.xxxyjade.lumirose.tool.args.EmptyParameters;
import io.github.xxxyjade.lumirose.tool.args.ToolParameters;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolContextTest {

    @Test
    void tc_t01_toolOfRejectsNulls() {
        assertThrows(ConfigurationException.class,
                () -> Tool.of(null, "描述", new EmptyParameters(), parameters -> "ok"));
        assertThrows(ConfigurationException.class,
                () -> Tool.of("name", null, new EmptyParameters(), parameters -> "ok"));
        assertThrows(ConfigurationException.class,
                () -> Tool.of("name", "描述", null, parameters -> "ok"));
        assertThrows(ConfigurationException.class,
                () -> Tool.of("name", "描述", new EmptyParameters(), null));
    }

    @Test
    void tc_t02_registerNullRejected() {
        assertThrows(ConfigurationException.class, () -> ToolContext.builder().register(null));
    }

    @Test
    void tc_t03_duplicateNameRejected() {
        var ex = assertThrows(ConfigurationException.class, () -> ToolContext.builder()
                .register(tool("dup", parameters -> "a"))
                .register(tool("dup", parameters -> "b")));

        assertEquals(1001, ex.getErrorCode().code());
        assertTrue(ex.getMessage().contains("dup"));
    }

    @Test
    void tc_t04_toolsKeepRegistrationOrderAndAreImmutable() {
        var context = ToolContext.builder()
                .register(tool("a", parameters -> "a"))
                .register(tool("b", parameters -> "b"))
                .build();

        assertEquals(List.of("a", "b"), context.tools().stream().map(Tool::name).toList());
        assertThrows(UnsupportedOperationException.class,
                () -> context.tools().add(tool("c", parameters -> "c")));
    }

    @Test
    void tc_t05_unknownToolThrowsNotFound() {
        var context = ToolContext.builder().register(tool("known", parameters -> "ok")).build();

        var ex = assertThrows(ToolException.class, () -> context.execute("missing", "{}"));

        assertEquals(4001, ex.getErrorCode().code());
        assertEquals("missing", ex.toolName());
    }

    @Test
    void tc_t06_invalidJsonKeepsCause() {
        var context = ToolContext.builder().register(sample(parameters -> "ok")).build();

        var ex = assertThrows(ToolException.class, () -> context.execute("sample", "{bad"));

        assertEquals(4002, ex.getErrorCode().code());
        assertNotNull(ex.getCause());
    }

    @Test
    void tc_t07_unknownArgumentsAreIgnored() {
        var context = ToolContext.builder()
                .register(sample(parameters -> ((SampleParams) parameters).path()))
                .build();

        assertEquals("a.txt", context.execute("sample", "{\"path\":\"a.txt\",\"extra\":1}"));
    }

    @Test
    void tc_t08_nullArgumentsTreatedAsEmptyObject() {
        var context = ToolContext.builder().register(sample(parameters -> "ok")).build();

        assertEquals("ok", context.execute("sample", null));
        assertEquals("ok", context.execute("sample", ""));
    }

    @Test
    void tc_t09_lumiroseExceptionPassesThrough() {
        var context = ToolContext.builder()
                .register(tool("locked", parameters -> {
                    throw new ToolException(LumiRoseErrorCode.TOOL_FILE_EXISTS, "locked", "exists");
                }))
                .build();

        var ex = assertThrows(ToolException.class, () -> context.execute("locked", "{}"));

        assertEquals(4006, ex.getErrorCode().code());
        assertEquals("locked", ex.toolName());
    }

    @Test
    void tc_t10_toolThrowWrappedWithName() {
        var context = ToolContext.builder()
                .register(tool("boom_tool", parameters -> {
                    throw new IllegalStateException("boom");
                }))
                .build();

        var ex = assertThrows(ToolException.class, () -> context.execute("boom_tool", "{}"));

        assertEquals(4003, ex.getErrorCode().code());
        assertEquals("boom_tool", ex.toolName());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void tc_t11_registerAllRejectsNullList() {
        assertThrows(ConfigurationException.class, () -> ToolContext.builder().registerAll(null));
    }

    @Test
    void tc_t12_escapedArgumentsParse() {
        var seen = new AtomicReference<ToolParameters>();
        var context = ToolContext.builder()
                .register(sample(parameters -> {
                    seen.set(parameters);
                    return "ok";
                }))
                .build();

        String arguments = """
                {"path":"a\\"b","text":"第一行\\n第二行"}
                """;

        assertEquals("ok", context.execute("sample", arguments));

        var params = (SampleParams) seen.get();
        assertEquals("a\"b", params.path());
        assertEquals("第一行\n第二行", params.text());
    }

    private Tool sample(Function<ToolParameters, String> handler) {
        return Tool.of("sample", "示例工具", new SampleParams("p", "t"), handler);
    }

    private Tool tool(String name, Function<ToolParameters, String> handler) {
        return Tool.of(name, "示例工具", new EmptyParameters(), handler);
    }

    record SampleParams(
            @JsonProperty("path") String path,
            @JsonProperty("text") String text) implements ToolParameters {
    }
}
