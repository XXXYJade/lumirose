package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.exception.AgentException;
import io.github.xxxyjade.lumirose.exception.ClientException;
import io.github.xxxyjade.lumirose.repository.InMemoryMessageRepository;
import io.github.xxxyjade.lumirose.testing.BrokenSseServer;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import io.github.xxxyjade.lumirose.testing.RecordingTool;
import io.github.xxxyjade.lumirose.testing.StreamRecorder;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryTest {

    private FakeServer server;
    private InMemoryMessageRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeServer();
        repository = new InMemoryMessageRepository();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void tc_rt01_retriesServerErrorThenSucceeds() {
        server.enqueue(500, "{}");
        server.enqueue(200, stopJson("ok"));

        assertEquals("ok", agent(1, 0).run(1L, "hi"));
        assertEquals(2, server.requestCount());
    }

    @Test
    void tc_rt02_doesNotRetry401() {
        server.enqueue(401, "{\"error\":{\"code\":\"invalid_api_key\"}}");

        var ex = assertThrows(ClientException.class, () -> agent(3, 0).run(1L, "hi"));

        assertEquals(2001, ex.getErrorCode().code());
        assertEquals(1, server.requestCount());
    }

    @Test
    void tc_rt03_retries429Twice() {
        server.enqueue(429, "{}");
        server.enqueue(429, "{}");
        server.enqueue(200, stopJson("ok"));

        assertEquals("ok", agent(2, 0).run(1L, "hi"));
        assertEquals(3, server.requestCount());
    }

    @Test
    void tc_rt04_throwsAfterRetriesExhausted() {
        server.enqueue(500, "{}");
        server.enqueue(500, "{}");

        var ex = assertThrows(ClientException.class, () -> agent(1, 0).run(1L, "hi"));

        assertEquals(2001, ex.getErrorCode().code());
        assertEquals(2, server.requestCount());
    }

    @Test
    void tc_rt05_streamRetriesBeforeFirstDelta() {
        server.enqueue(500, "{}");
        server.enqueueSse(sse(contentChunk("done"), finalChunk("\"stop\"")));
        var recorder = new StreamRecorder();

        var result = agent(2, 0).runStream(1L, "hi", recorder);

        assertEquals("done", result.content());
        assertEquals(2, server.requestCount());
        assertEquals(1, recorder.count("start"));
        assertEquals(1, recorder.count("turnEnd:"));
    }

    @Test
    void tc_rt06_streamDoesNotRetryAfterDelta() throws Exception {
        try (var broken = new BrokenSseServer("data: " + contentChunk("a") + "\n\n", 4096)) {
            var ex = assertThrows(ClientException.class,
                    () -> agentAt(broken.baseUrl(), 2, 0).runStream(1L, "hi", new StreamRecorder()));

            assertEquals(2003, ex.getErrorCode().code());
            assertEquals(1, broken.connectionCount());
            assertTrue(repository.load(1L).isEmpty());
        }
    }

    @Test
    void tc_rt07_delayIsExponentialAndCapped() {
        assertEquals(100, RetryPolicy.delayMillis(1, 100, 8000));
        assertEquals(200, RetryPolicy.delayMillis(2, 100, 8000));
        assertEquals(400, RetryPolicy.delayMillis(3, 100, 8000));
        assertEquals(8000, RetryPolicy.delayMillis(10, 100, 8000));
        assertEquals(0, RetryPolicy.delayMillis(3, 0, 8000));
    }

    @Test
    void tc_rt08_retryDoesNotCountAsTurn() {
        server.enqueue(500, "{}");
        server.enqueue(500, "{}");
        server.enqueue(200, stopJson("ok"));
        var recorder = new RecordingRecorder();

        var result = agent(recorder, 2, 0).runResult(1L, "hi");

        assertEquals(1, result.turns());
        assertEquals(2, recorder.count("retry:"));
    }

    @Test
    void tc_rt09_cancelDuringBackoffStopsRetry() {
        server.enqueue(500, "{}");
        server.enqueue(200, stopJson("ok"));
        var stream = new StreamRecorder();
        var recorder = new MetricsRecorder() {
            @Override
            public void onRequest(int turn, long elapsedMillis, boolean success) {
                if (!success && stream.control != null) {
                    stream.control.cancel();
                }
            }
        };

        var ex = assertThrows(AgentException.class,
                () -> agent(recorder, 3, 5000).runStream(1L, "hi", stream));

        assertEquals(3004, ex.getErrorCode().code());
        assertEquals(1, server.requestCount());
    }

    @Test
    void tc_rt10_retryDoesNotRepeatTool() {
        var tool = new RecordingTool("t1", "一");
        tool.willReturn("one");
        server.enqueue(200, toolCalls("t1"));
        server.enqueue(500, "{}");
        server.enqueue(200, stopJson("done"));

        var result = agent(tool, 1, 0).runResult(1L, "hi");

        assertEquals("done", result.content());
        assertEquals(1, tool.calls().size());
        assertEquals(3, server.requestCount());
    }

    @Test
    void tc_mt02_recorderReceivesRetry() {
        server.enqueue(500, "{}");
        server.enqueue(200, stopJson("ok"));
        var recorder = new RecordingRecorder();

        agent(recorder, 1, 0).run(1L, "hi");

        assertEquals(1, recorder.count("retry:1:"));
        assertTrue(recorder.events.contains("retry:1:2001"));
    }

    private LumiRoseAgent agent(int maxRetries, long baseDelay) {
        return agent((MetricsRecorder) null, maxRetries, baseDelay);
    }

    private LumiRoseAgent agent(MetricsRecorder recorder, int maxRetries, long baseDelay) {
        return agent(null, recorder, maxRetries, baseDelay);
    }

    private LumiRoseAgent agentAt(String baseUrl, int maxRetries, long baseDelay) {
        return agent(baseUrl, null, null, maxRetries, baseDelay);
    }

    private LumiRoseAgent agent(RecordingTool tool, int maxRetries, long baseDelay) {
        return agent(tool, null, maxRetries, baseDelay);
    }

    private LumiRoseAgent agent(RecordingTool tool, MetricsRecorder recorder, int maxRetries, long baseDelay) {
        return agent(server.baseUrl(), tool, recorder, maxRetries, baseDelay);
    }

    private LumiRoseAgent agent(String baseUrl, RecordingTool tool, MetricsRecorder recorder,
                                int maxRetries, long baseDelay) {
        ToolContext.Builder context = ToolContext.builder();
        if (tool != null) {
            context.register(tool);
        }
        LumiRoseAgent.Builder builder = LumiRoseAgent.builder()
                .client(LumiRoseClient.builder()
                        .baseUrl(baseUrl)
                        .apiKey("test-key")
                        .model("deepseek-chat")
                        .build())
                .toolContext(context.build())
                .repository(repository)
                .maxRetries(maxRetries)
                .retryBaseDelayMillis(baseDelay)
                .retryMaxDelayMillis(8000);
        if (recorder != null) {
            builder.metricsRecorder(recorder);
        }
        return builder.build();
    }

    private String toolCalls(String... names) {
        StringBuilder json = new StringBuilder("{\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\","
                + "\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"id\":\"c").append(i + 1)
                    .append("\",\"type\":\"function\",\"function\":{\"name\":\"")
                    .append(names[i])
                    .append("\",\"arguments\":\"{}\"}}");
        }
        json.append("]}}]}");
        return json.toString();
    }

    private String stopJson(String content) {
        return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}]}";
    }

    private static String contentChunk(String delta) {
        return chunk("{\"content\":\"" + delta + "\"}");
    }

    private static String chunk(String deltaJson) {
        return "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
                + "\"delta\":" + deltaJson + ",\"finish_reason\":null}]}";
    }

    private static String finalChunk(String reasonJson) {
        return "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
                + "\"delta\":{},\"finish_reason\":" + reasonJson + "}]}";
    }

    private static String sse(String... payloads) {
        StringBuilder body = new StringBuilder();
        for (String payload : payloads) {
            body.append("data: ").append(payload).append("\n\n");
        }
        return body.append("data: [DONE]\n\n").toString();
    }

    private static final class RecordingRecorder implements MetricsRecorder {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onRetry(int attempt, long delayMillis, String errorCode) {
            events.add("retry:" + attempt + ":" + errorCode);
        }

        private int count(String prefix) {
            return (int) events.stream().filter(event -> event.startsWith(prefix)).count();
        }
    }
}
