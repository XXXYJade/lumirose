package io.github.xxxyjade.lumirose.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.xxxyjade.lumirose.agent.LumiRoseAgent;
import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.exception.ToolException;
import io.github.xxxyjade.lumirose.repository.InMemoryMessageRepository;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import io.github.xxxyjade.lumirose.testing.RecordingTool;
import io.github.xxxyjade.lumirose.testing.TestLogs;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingTest {

    private FakeServer server;
    private InMemoryMessageRepository repository;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeServer();
        repository = new InMemoryMessageRepository();
        appender = TestLogs.attach();
    }

    @AfterEach
    void tearDown() {
        TestLogs.detach(appender);
        server.close();
    }

    @Test
    void tc_l01_debugHasToolNameButNoPayload() {
        var tool = new RecordingTool("read_file", "读取");
        tool.willReturn("secret-content");
        server.enqueue(200, "{\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\","
                + "\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":["
                + "{\"id\":\"c1\",\"type\":\"function\",\"function\":{\"name\":\"read_file\",\"arguments\":\"{}\"}}]}}]}");
        server.enqueue(200, stopResponse("done"));

        agent(tool).run(1L, "你好");

        String logs = TestLogs.text(appender);
        assertTrue(logs.contains("read_file"), logs);
        assertFalse(logs.contains("secret-content"), logs);
        assertFalse(logs.contains("你好"), logs);
        assertFalse(logs.contains("test-key"), logs);
    }

    @Test
    void tc_l02_errorLogKeepsCodeOnly() {
        var tool = new RecordingTool("probe", "探测");
        tool.willThrow(new IllegalStateException("secret-detail"));
        server.enqueue(200, "{\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\","
                + "\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":["
                + "{\"id\":\"c1\",\"type\":\"function\",\"function\":{\"name\":\"probe\",\"arguments\":\"{}\"}}]}}]}");

        assertThrows(ToolException.class, () -> agent(tool).run(1L, "hi"));

        String logs = TestLogs.text(appender);
        assertTrue(logs.contains("4003"), logs);
        assertFalse(logs.contains("secret-detail"), logs);
    }

    private LumiRoseAgent agent(RecordingTool tool) {
        return LumiRoseAgent.builder()
                .client(LumiRoseClient.builder()
                        .baseUrl(server.baseUrl())
                        .apiKey("test-key")
                        .model("deepseek-chat")
                        .build())
                .toolContext(ToolContext.builder().register(tool).build())
                .repository(repository)
                .build();
    }

    private String stopResponse(String content) {
        return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}]}";
    }
}
