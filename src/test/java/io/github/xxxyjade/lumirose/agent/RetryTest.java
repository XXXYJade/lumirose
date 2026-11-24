package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.common.exception.ClientException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.result.LumiRoseResult;
import io.github.xxxyjade.lumirose.tool.EmptyParameters;
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 可重试错误、重试上限、工具不重跑和增量后失败不重试的测试。
 */
class RetryTest {

    private AgentFixture fixture;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new AgentFixture();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void retriesOn500ThenSucceeds() {
        fixture.server.enqueueJson(500, "{}");
        fixture.server.enqueueSse(AgentFixture.textReply("ok"));

        LumiRoseResult result = agent(2).run(1L, "hi");

        assertEquals("ok", result.content());
        assertEquals(2, fixture.server.requests().size());
    }

    @Test
    void retriesOn429ThenSucceeds() {
        fixture.server.enqueueJson(429, "{}");
        fixture.server.enqueueSse(AgentFixture.textReply("ok"));

        LumiRoseResult result = agent(2).run(1L, "hi");

        assertEquals("ok", result.content());
        assertEquals(2, fixture.server.requests().size());
    }

    @Test
    void doesNotRetry401() {
        fixture.server.enqueueJson(401, "{\"error\":{\"code\":\"bad_key\",\"type\":\"auth\"}}");

        ClientException error = assertThrows(ClientException.class, () -> agent(2).run(1L, "hi"));

        assertEquals(LumiRoseErrorCode.CLIENT_HTTP_ERROR, error.getErrorCode());
        assertEquals(401, error.httpStatus().intValue());
        assertEquals(1, fixture.server.requests().size());
    }

    @Test
    void stopsAfterMaxRetries() {
        for (int attempt = 0; attempt < 3; attempt++) {
            fixture.server.enqueueJson(500, "{}");
        }

        ClientException error = assertThrows(ClientException.class, () -> agent(2).run(1L, "hi"));

        assertEquals(LumiRoseErrorCode.CLIENT_HTTP_ERROR, error.getErrorCode());
        assertEquals(500, error.httpStatus().intValue());
        assertEquals(3, fixture.server.requests().size());
        assertEquals(0, fixture.repository.load(1L).messages().size());
    }

    @Test
    void doesNotRerunToolsOnRetry() {
        AtomicInteger toolRuns = new AtomicInteger();
        ToolContext tools = ToolContext.builder()
                .register(Tool.of("count", "计数", new EmptyParameters(), parameters -> {
                    toolRuns.incrementAndGet();
                    return "ok";
                }))
                .build();
        fixture.server.enqueueSse(AgentFixture.toolReply(null,
                AgentFixture.ToolCallSpec.of("call_1", "count", "{}")));
        fixture.server.enqueueJson(500, "{}");
        fixture.server.enqueueSse(AgentFixture.textReply("done"));

        LumiRoseResult result = fixture.agent(tools).maxRetries(2)
                .retryBaseDelayMillis(1).retryMaxDelayMillis(5).build().run(1L, "hi");

        assertEquals("done", result.content());
        assertEquals(1, toolRuns.get());
        assertEquals(3, fixture.server.requests().size());
    }

    @Test
    void retryDoesNotConsumeTurnBudget() {
        fixture.server.enqueueSse(AgentFixture.toolReply(null,
                AgentFixture.ToolCallSpec.of("call_1", "count", "{}")));
        fixture.server.enqueueJson(500, "{}");
        fixture.server.enqueueSse(AgentFixture.textReply("done"));
        ToolContext tools = ToolContext.builder()
                .register(Tool.of("count", "计数", new EmptyParameters(), parameters -> "ok"))
                .build();

        LumiRoseResult result = fixture.agent(tools).maxTurns(2).maxRetries(2)
                .retryBaseDelayMillis(1).retryMaxDelayMillis(5).build().run(1L, "hi");

        assertEquals("done", result.content());
    }

    @Test
    void runRetriesAfterPartialStream() {
        fixture.server.enqueueTruncatedSse(AgentFixture.chunk(AgentFixture.content("a"), null), 64);
        fixture.server.enqueueSse(AgentFixture.textReply("ok"));

        LumiRoseResult result = agent(2).run(1L, "hi");

        // run 不向调用方交付增量，因此断流后重试不会产生重复正文。
        assertEquals("ok", result.content());
        assertEquals(2, fixture.server.requests().size());
        assertEquals(2, fixture.repository.load(1L).messages().size());
    }

    @Test
    void streamRunDoesNotRetryAfterDeltaWasDelivered() {
        fixture.server.enqueueTruncatedSse(AgentFixture.chunk(AgentFixture.content("a"), null), 64);
        fixture.server.enqueueSse(AgentFixture.textReply("ok"));

        LumiRoseResult result = agent(2).streamRun(1L, "hi");
        StringBuilder delivered = new StringBuilder();

        ClientException error = assertThrows(ClientException.class, () -> {
            for (String delta : result.deltas()) {
                delivered.append(delta);
            }
        });

        assertEquals("a", delivered.toString());
        assertEquals(LumiRoseErrorCode.CLIENT_STREAM_INTERRUPTED, error.getErrorCode());
        assertEquals(1, fixture.server.requests().size());
        assertEquals(0, fixture.repository.load(1L).messages().size());
    }

    private LumiRoseAgent agent(int maxRetries) {
        return fixture.agent().maxRetries(maxRetries)
                .retryBaseDelayMillis(1).retryMaxDelayMillis(5).build();
    }
}
