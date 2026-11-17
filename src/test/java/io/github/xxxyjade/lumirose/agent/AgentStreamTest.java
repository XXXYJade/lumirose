package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.exception.AgentException;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionAssistantMessageParam;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentStreamTest {

    private FakeServer server;
    private InMemoryMessageRepository repository;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

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
    void tc_as01_streamReturnsFinalTextAndHistory() {
        server.enqueueSse(sse(contentChunks("你好"), finalChunk("\"stop\"")));
        var recorder = new StreamRecorder();

        var result = agent().runStream(1L, "hi", recorder);

        assertEquals("你好", result.content());
        assertEquals(1, result.turns());
        assertEquals(3, repository.load(1L).size());
    }

    @Test
    void tc_as02_eventOrderIsStartContentTurnEnd() {
        server.enqueueSse(sse(contentChunk("你"), contentChunk("好"), finalChunk("\"stop\"")));
        var recorder = new StreamRecorder();

        agent().runStream(1L, "hi", recorder);

        assertEquals(List.of("start", "content:你", "content:好", "turnEnd:1:STOP"), recorder.events);
    }

    @Test
    void tc_as03_toolEventsWrapExecution() {
        var tool = new RecordingTool("t1", "一");
        tool.willReturn("one");
        server.enqueueSse(sse(contentChunks("先看看"), toolCallChunk("call_1", "t1"), finalChunk("\"tool_calls\"")));
        server.enqueueSse(sse(contentChunks("完成"), finalChunk("\"stop\"")));
        var recorder = new StreamRecorder();

        var result = agent(tool).runStream(1L, "hi", recorder);

        assertEquals("完成", result.content());
        assertEquals(1, tool.calls().size());
        assertEquals(List.of(
                "start",
                "content:先看看",
                "toolStart:call_1:t1",
                "toolEnd:call_1:t1:true",
                "turnEnd:1:TOOL_CALLS",
                "content:完成",
                "turnEnd:2:STOP"), recorder.events);
        assertEquals(5, repository.load(1L).size());
    }

    @Test
    void tc_as04_reasoningEventsPrecedeContent() {
        server.enqueueSse(sse(
                chunk("{\"reasoning_content\":\"想\"}"),
                contentChunks("答"),
                finalChunk("\"stop\"")));
        var recorder = new StreamRecorder();

        agent().runStream(1L, "hi", recorder);

        assertEquals(List.of("start", "reasoning:想", "content:答", "turnEnd:1:STOP"), recorder.events);
        var assistant = (ChatCompletionAssistantMessageParam) repository.load(1L).get(2);
        assertEquals("想", assistant.reasoningContent());
    }

    @Test
    void tc_as05_nullListenerRejected() {
        var ex = assertThrows(AgentException.class, () -> agent().runStream(1L, "hi", null));

        assertEquals(1002, ex.getErrorCode().code());
    }

    @Test
    void tc_as06_emptyContentFailsWithoutCommit() {
        server.enqueueSse(sse(finalChunk("\"stop\"")));

        var ex = assertThrows(AgentException.class, () -> agent().runStream(1L, "hi", new StreamRecorder()));

        assertEquals(3003, ex.getErrorCode().code());
        assertTrue(repository.load(1L).isEmpty());
    }

    @Test
    void tc_as07_listenerExceptionPropagates() {
        server.enqueueSse(sse(contentChunks("x"), finalChunk("\"stop\"")));
        var listener = new StreamRecorder() {
            @Override
            public void onContentDelta(String delta) {
                throw new IllegalStateException("boom");
            }
        };

        var ex = assertThrows(IllegalStateException.class, () -> agent().runStream(1L, "hi", listener));

        assertEquals("boom", ex.getMessage());
        assertTrue(repository.load(1L).isEmpty());
    }

    @Test
    void tc_as08_streamMatchesRun() throws Exception {
        server.enqueue(200, stopJson("done"));
        server.enqueueSse(sse(contentChunk("done"), finalChunk("\"stop\"")));

        String run = agent().run(1L, "hi");
        String stream = agent().runStream(2L, "hi", new StreamRecorder()).content();

        assertEquals(run, stream);
        assertEquals(mapper.writeValueAsString(repository.load(1L)),
                mapper.writeValueAsString(repository.load(2L)));
    }

    @Test
    void tc_as09_startExceptionAbortsBeforeRequest() {
        server.enqueueSse(sse(contentChunks("x"), finalChunk("\"stop\"")));
        var listener = new StreamRecorder() {
            @Override
            public void onStart(StreamControl control) {
                super.onStart(control);
                throw new IllegalStateException("start boom");
            }
        };

        assertThrows(IllegalStateException.class, () -> agent().runStream(1L, "hi", listener));
        assertEquals(0, server.requestCount());
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

    private static String contentChunks(String... deltas) {
        StringBuilder json = new StringBuilder();
        for (String delta : deltas) {
            json.append(contentChunk(delta));
        }
        return json.toString();
    }

    private static String contentChunk(String delta) {
        return chunk("{\"content\":\"" + delta + "\"}");
    }

    private static String stopJson(String content) {
        return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}]}";
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
