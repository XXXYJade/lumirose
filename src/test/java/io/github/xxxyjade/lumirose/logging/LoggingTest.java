package io.github.xxxyjade.lumirose.logging;

import ch.qos.logback.classic.Level;
import io.github.xxxyjade.lumirose.agent.AgentFixture;
import io.github.xxxyjade.lumirose.agent.LumiRoseAgent;
import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.exception.ToolException;
import io.github.xxxyjade.lumirose.common.result.LumiRoseResult;
import io.github.xxxyjade.lumirose.model.entity.Message;
import io.github.xxxyjade.lumirose.testing.TestLogs;
import io.github.xxxyjade.lumirose.tool.EmptyParameters;
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R59：运行、轮次、工具、压缩、重试、取消都有日志，且日志不泄漏敏感内容。
 */
class LoggingTest {

    private AgentFixture fixture;
    private TestLogs logs;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new AgentFixture();
        logs = TestLogs.attach(LumiRoseAgent.class);
    }

    @AfterEach
    void tearDown() {
        logs.close();
        fixture.close();
    }

    @Test
    void runEventsAreLoggedAtExpectedLevelsWithoutSensitiveContent() {
        ToolContext tools = ToolContext.builder()
                .register(Tool.of("read_file", "读", new EmptyParameters(),
                        parameters -> "工具结果敏感串"))
                .build();
        fixture.server.enqueueSse(AgentFixture.toolReply("开始",
                AgentFixture.ToolCallSpec.of("call_1", "read_file", "{}")));
        fixture.server.enqueueSse(AgentFixture.textReply("完成", 10, 5, 15));

        fixture.agent(tools).build().run(1L, "你好敏感内容");

        assertTrue(logs.hasEvent("run start", Level.INFO));
        assertTrue(logs.hasEvent("run end", Level.INFO));
        assertTrue(logs.hasEvent("request:", Level.DEBUG));
        assertTrue(logs.hasEvent("response:", Level.DEBUG));
        assertTrue(logs.hasEvent("tool start", Level.DEBUG));
        assertTrue(logs.hasEvent("tool end", Level.DEBUG));

        String all = logs.allText();
        assertTrue(all.contains("read_file"));
        assertFalse(all.contains(AgentFixture.API_KEY));
        assertFalse(all.contains("你好敏感内容"));
        assertFalse(all.contains("工具结果敏感串"));
    }

    @Test
    void compressionEventsDoNotLogSummaryContent() {
        fixture.repository.append(1L,
                List.of(Message.user("旧问题"), Message.assistant("旧回答", null, List.of())),
                700);
        fixture.server.enqueueSse(AgentFixture.textReply("SUMMARY敏感内容"));
        fixture.server.enqueueSse(AgentFixture.textReply("最终", 10, 5, 15));

        fixture.agent().contextWindowTokens(1000).build().run(1L, "新问题");

        assertTrue(logs.hasEvent("compress:", Level.INFO));
        assertTrue(logs.hasEvent("compressed:", Level.DEBUG));
        String all = logs.allText();
        assertFalse(all.contains("SUMMARY敏感内容"));
        assertFalse(all.contains("旧问题"));
        assertFalse(all.contains("旧回答"));
        assertFalse(all.contains("新问题"));
    }

    @Test
    void retryIsLoggedAndProviderMessageIsRedacted() {
        fixture.server.enqueueJson(500, "{\"error\":{\"message\":\"provider sensitive detail\","
                + "\"code\":\"server_error\",\"type\":\"server\"}}");
        fixture.server.enqueueSse(AgentFixture.textReply("ok"));

        fixture.agent().maxRetries(1)
                .retryBaseDelayMillis(1).retryMaxDelayMillis(2)
                .build().run(1L, "hi");

        assertTrue(logs.hasEvent("retry:", Level.DEBUG));
        assertTrue(logs.hasEvent("run end", Level.INFO));
        assertFalse(logs.allText().contains("provider sensitive detail"));
        assertFalse(logs.allText().contains(AgentFixture.API_KEY));
    }

    @Test
    void toolFailureLogsErrorWithoutArgumentsOrResult() {
        ToolContext tools = ToolContext.builder()
                .register(Tool.of("boom", "失败", new EmptyParameters(), parameters -> {
                    throw new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, "boom", "失败");
                }))
                .build();
        fixture.server.enqueueSse(AgentFixture.toolReply(null,
                AgentFixture.ToolCallSpec.of("call_1", "boom", "{\"secret\":\"参数敏感串\"}")));

        assertThrows(ToolException.class,
                () -> fixture.agent(tools).build().run(1L, "hi"));

        assertTrue(logs.hasEvent("tool failed", Level.ERROR));
        assertTrue(logs.hasEvent("run failed", Level.ERROR));
        assertTrue(logs.hasEvent("discard:", Level.WARN));
        assertTrue(logs.allText().contains("4003"));
        assertTrue(logs.allText().contains("messages=2"));
        assertFalse(logs.allText().contains("参数敏感串"));
    }

    @Test
    void cancelIsLoggedAtInfoLevel() {
        String first = AgentFixture.chunk(AgentFixture.content("a"), null);
        String rest = AgentFixture.chunk(AgentFixture.emptyDelta(), "stop") + AgentFixture.DONE;
        fixture.server.enqueueSlowSse(first, 300, rest);
        LumiRoseResult result = fixture.agent().build().streamRun(1L, "取消敏感内容");

        Iterator<String> iterator = result.deltas().iterator();
        assertTrue(iterator.hasNext());
        iterator.next();
        result.cancel();
        try {
            iterator.hasNext();
        } catch (AgentException expected) {
            // 取消异常由迭代器抛出，这里只关心日志。
        }

        assertTrue(logs.awaitEvent("cancel:", Level.INFO, 2000));
        assertTrue(logs.awaitEvent("discard:", Level.INFO, 2000));
        assertFalse(logs.allText().contains(AgentFixture.API_KEY));
        assertFalse(logs.allText().contains("取消敏感内容"));
    }
}
