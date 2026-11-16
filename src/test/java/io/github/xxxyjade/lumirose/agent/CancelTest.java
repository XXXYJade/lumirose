package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.exception.AgentException;
import io.github.xxxyjade.lumirose.repository.InMemoryMessageRepository;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import io.github.xxxyjade.lumirose.testing.RecordingTool;
import io.github.xxxyjade.lumirose.testing.StreamRecorder;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancelTest {

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
    void tc_cx01_cancelOnFirstDeltaStopsRun() {
        server.enqueueSse(sse(contentChunk("abc"), finalChunk("\"stop\"")));
        var recorder = new StreamRecorder();
        recorder.cancelOnContent = true;

        var ex = assertThrows(AgentException.class, () -> agent().runStream(1L, "hi", recorder));

        assertEquals(3004, ex.getErrorCode().code());
        assertTrue(repository.load(1L).isEmpty());
        assertEquals(List.of("start", "content:abc"), recorder.events);
    }

    @Test
    void tc_cx02_cancelInStartSkipsRequest() {
        server.enqueueSse(sse(contentChunk("abc"), finalChunk("\"stop\"")));
        var recorder = new StreamRecorder();
        recorder.cancelOnStart = true;

        var ex = assertThrows(AgentException.class, () -> agent().runStream(1L, "hi", recorder));

        assertEquals(3004, ex.getErrorCode().code());
        assertEquals(0, server.requestCount());
        assertTrue(repository.load(1L).isEmpty());
    }

    @Test
    void tc_cx03_cancelInToolStartWaitsForTool() {
        var tool = new RecordingTool("t1", "一");
        tool.willReturn("one");
        server.enqueueSse(sse(toolCallChunk("call_1", "t1"), finalChunk("\"tool_calls\"")));
        var recorder = new StreamRecorder();
        recorder.cancelOnToolStart = true;

        var ex = assertThrows(AgentException.class, () -> agent(tool).runStream(1L, "hi", recorder));

        assertEquals(3004, ex.getErrorCode().code());
        assertEquals(1, tool.calls().size());
        assertTrue(repository.load(1L).isEmpty());
        assertTrue(recorder.events.contains("toolEnd:call_1:t1:true"));
    }

    @Test
    void tc_cx04_cancelIsIdempotent() {
        server.enqueueSse(sse(contentChunk("abc"), finalChunk("\"stop\"")));
        var recorder = new StreamRecorder();
        recorder.cancelOnStart = true;

        assertThrows(AgentException.class, () -> agent().runStream(1L, "hi", recorder));

        recorder.control.cancel();
        recorder.control.cancel();
        assertTrue(recorder.control.isCancelled());
    }

    @Test
    void tc_cx05_canRunAgainAfterCancel() {
        server.enqueueSse(sse(contentChunk("abc"), finalChunk("\"stop\"")));
        server.enqueueSse(sse(contentChunk("done"), finalChunk("\"stop\"")));
        var cancelled = new StreamRecorder();
        cancelled.cancelOnContent = true;

        assertThrows(AgentException.class, () -> agent().runStream(1L, "hi", cancelled));
        var result = agent().runStream(1L, "hi", new StreamRecorder());

        assertEquals("done", result.content());
        assertEquals(3, repository.load(1L).size());
    }

    @Test
    void tc_cx06_cancelInTurnEndPreventsCommit() {
        server.enqueueSse(sse(contentChunk("abc"), finalChunk("\"stop\"")));
        var recorder = new StreamRecorder();
        recorder.cancelOnTurnEnd = true;

        var ex = assertThrows(AgentException.class, () -> agent().runStream(1L, "hi", recorder));

        assertEquals(3004, ex.getErrorCode().code());
        assertTrue(repository.load(1L).isEmpty());
    }

    private LumiRoseAgent agent(RecordingTool... tools) {
        ToolContext.Builder context = ToolContext.builder();
        for (RecordingTool tool : tools) {
            context.register(tool);
        }
        return LumiRoseAgent.builder()
                .client(LumiRoseClient.builder()
                        .baseUrl(server.baseUrl())
                        .apiKey("test-key")
                        .model("deepseek-chat")
                        .build())
                .toolContext(context.build())
                .repository(repository)
                .build();
    }

    private static String contentChunk(String delta) {
        return chunk("{\"content\":\"" + delta + "\"}");
    }

    private static String chunk(String deltaJson) {
        return "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
                + "\"delta\":" + deltaJson + ",\"finish_reason\":null}]}";
    }

    private static String toolCallChunk(String id, String name) {
        return chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"" + id + "\",\"type\":\"function\","
                + "\"function\":{\"name\":\"" + name + "\",\"arguments\":\"{}\"}}]}");
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
}
