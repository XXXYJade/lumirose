package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.repository.InMemoryMessageRepository;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import io.github.xxxyjade.lumirose.testing.RecordingTool;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultMetricsTest {

    private final com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

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
    void tc_rs01_turnsAndUsageAreAggregated() {
        var tool = new RecordingTool("t1", "一");
        tool.willReturn("one");
        server.enqueue(200, toolCalls("t1"));
        server.enqueue(200, stopResponse("done", 30, 40));

        var result = agent(tool).runResult(1L, "hi");

        assertEquals(2, result.turns());
        assertEquals(40, result.promptTokens());
        assertEquals(60, result.completionTokens());
        assertEquals("done", result.content());
    }

    @Test
    void tc_rs02_toolCallsAreRecordedInOrder() {
        var first = new RecordingTool("t1", "一");
        first.willReturn("one");
        var second = new RecordingTool("t2", "二");
        second.willReturn("two");
        server.enqueue(200, toolCalls("t1", "t2"));
        server.enqueue(200, stopResponse("done", 30, 40));

        var result = agent(first, second).runResult(1L, "hi");

        assertEquals(2, result.toolCalls().size());
        assertEquals("c1", result.toolCalls().get(0).toolCallId());
        assertEquals("t1", result.toolCalls().get(0).toolName());
        assertEquals("c2", result.toolCalls().get(1).toolCallId());
        assertEquals("t2", result.toolCalls().get(1).toolName());
        assertTrue(result.toolCalls().get(0).elapsedMillis() >= 0);
    }

    @Test
    void tc_rs03_runEqualsRunResult() throws Exception {
        var tool = new RecordingTool("t1", "一");
        tool.willReturn("one");
        server.enqueue(200, toolCalls("t1"));
        server.enqueue(200, stopResponse("done", 30, 40));
        server.enqueue(200, toolCalls("t1"));
        server.enqueue(200, stopResponse("done", 30, 40));

        String run = agent(tool).run(1L, "hi");
        String runResult = agent(tool).runResult(2L, "hi").content();

        assertEquals(runResult, run);
        assertEquals(mapper.writeValueAsString(repository.load(1L)),
                mapper.writeValueAsString(repository.load(2L)));
    }

    @Test
    void tc_rs04_resultHasContentAndElapsed() {
        server.enqueue(200, stopResponse("done", 10, 20));

        var result = agent().runResult(1L, "hi");

        assertNotNull(result.content());
        assertTrue(result.elapsedMillis() > 0);
    }

    @Test
    void tc_mt01_recorderReceivesRequestUsageAndTool() {
        var tool = new RecordingTool("t1", "一");
        tool.willReturn("one");
        server.enqueue(200, toolCalls("t1"));
        server.enqueue(200, stopResponse("done", 30, 40));
        var recorder = new RecordingRecorder();

        agent(recorder, tool).run(1L, "hi");

        assertEquals(2, recorder.count("request:"));
        assertEquals(2, recorder.count("usage:"));
        assertEquals(1, recorder.count("tool:"));
    }

    @Test
    void tc_mt03_recorderFailureDoesNotBreakRun() {
        server.enqueue(200, stopResponse("done", 10, 20));
        var recorder = new RecordingRecorder();
        recorder.failing = true;

        var result = agent(recorder).runResult(1L, "hi");

        assertEquals("done", result.content());
    }

    @Test
    void tc_mt04_defaultRecorderIsNoop() {
        server.enqueue(200, stopResponse("done", 10, 20));

        assertEquals("done", agent().run(1L, "hi"));
    }

    @Test
    void tc_mt05_runEmitsMetrics() {
        server.enqueue(200, stopResponse("done", 10, 20));
        var recorder = new RecordingRecorder();

        agent(recorder).run(1L, "hi");

        assertEquals(1, recorder.count("request:"));
        assertEquals(1, recorder.count("usage:"));
    }

    private LumiRoseAgent agent(RecordingTool... tools) {
        return agent(null, tools);
    }

    private LumiRoseAgent agent(MetricsRecorder recorder, RecordingTool... tools) {
        ToolContext.Builder context = ToolContext.builder();
        for (RecordingTool tool : tools) {
            context.register(tool);
        }
        LumiRoseAgent.Builder builder = LumiRoseAgent.builder()
                .client(LumiRoseClient.builder()
                        .baseUrl(server.baseUrl())
                        .apiKey("test-key")
                        .model("deepseek-chat")
                        .build())
                .toolContext(context.build())
                .repository(repository);
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
        json.append("]}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20}}");
        return json.toString();
    }

    private String stopResponse(String content, int promptTokens, int completionTokens) {
        return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":" + promptTokens
                + ",\"completion_tokens\":" + completionTokens + "}}";
    }

    private static final class RecordingRecorder implements MetricsRecorder {
        private final List<String> events = new ArrayList<>();
        private boolean failing;

        @Override
        public void onRequest(int turn, long elapsedMillis, boolean success) {
            events.add("request:" + turn + ":" + success);
            fail();
        }

        @Override
        public void onRetry(int attempt, long delayMillis, String errorCode) {
            events.add("retry:" + attempt + ":" + errorCode);
            fail();
        }

        @Override
        public void onUsage(int promptTokens, int completionTokens) {
            events.add("usage:" + promptTokens + ":" + completionTokens);
            fail();
        }

        @Override
        public void onTool(String toolName, long elapsedMillis, boolean success) {
            events.add("tool:" + toolName + ":" + success);
            fail();
        }

        private void fail() {
            if (failing) {
                throw new IllegalStateException("recorder boom");
            }
        }

        private int count(String prefix) {
            return (int) events.stream().filter(event -> event.startsWith(prefix)).count();
        }
    }
}
