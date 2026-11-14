package io.github.xxxyjade.lumirose.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.exception.AgentException;
import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.exception.ToolException;
import io.github.xxxyjade.lumirose.repository.InMemoryMessageRepository;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import io.github.xxxyjade.lumirose.testing.RecordingTool;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolLoopTest {

    private final ObjectMapper mapper = new ObjectMapper();

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
    void tc_a07_toolFailureLeavesHistoryUnchanged() {
        var boom = new RecordingTool("boom", "爆炸");
        boom.willThrow(new IllegalStateException("boom"));
        server.enqueue(200, toolCalls("boom"));

        var ex = assertThrows(ToolException.class,
                () -> agent(boom).run(1L, "hi"));

        assertEquals(4003, ex.getErrorCode().code());
        assertTrue(repository.load(1L).isEmpty());
        assertEquals(1, boom.calls().size());
    }

    @Test
    void tc_a16_toolCallRoundTripKeepsReasoning() throws Exception {
        var tool = new RecordingTool("read_file", "读取");
        tool.willReturn("content");
        server.enqueue(200, "{\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\","
                + "\"message\":{\"role\":\"assistant\",\"content\":null,\"reasoning_content\":\"think\","
                + "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"read_file\",\"arguments\":\"{}\"}}]}}]}");
        server.enqueue(200, stopResponse("done"));
        LumiRoseAgent agent = agent(tool);

        assertEquals("done", agent.run(1L, "hi"));

        JsonNode sent = mapper.readTree(server.lastRequest().body()).get("messages");
        assertEquals(4, sent.size());
        assertEquals("assistant", sent.get(2).get("role").asText());
        assertEquals("think", sent.get(2).get("reasoning_content").asText());
        assertEquals(1, sent.get(2).get("tool_calls").size());
        assertEquals("call_1", sent.get(3).get("tool_call_id").asText());
        assertEquals("content", sent.get(3).get("content").asText());
    }

    @Test
    void tc_a17_toolCallsExecuteInOrder() throws Exception {
        var first = new RecordingTool("t1", "一");
        first.willReturn("one");
        var second = new RecordingTool("t2", "二");
        second.willReturn("two");
        var context = ToolContext.builder().register(first).register(second).build();
        server.enqueue(200, toolCalls("t1", "t2"));
        server.enqueue(200, stopResponse("done"));

        agent(context).run(1L, "hi");

        JsonNode sent = mapper.readTree(server.lastRequest().body()).get("messages");
        assertEquals("one", sent.get(3).get("content").asText());
        assertEquals("two", sent.get(4).get("content").asText());
        assertEquals(1, first.calls().size());
        assertEquals(1, second.calls().size());
    }

    @Test
    void tc_a18_failFastStopsRemainingTools() {
        var first = new RecordingTool("t1", "一");
        var second = new RecordingTool("t2", "二");
        second.willThrow(new IllegalStateException("boom"));
        var third = new RecordingTool("t3", "三");
        var context = ToolContext.builder().register(first).register(second).register(third).build();
        server.enqueue(200, toolCalls("t1", "t2", "t3"));

        var ex = assertThrows(ToolException.class, () -> agent(context).run(1L, "hi"));

        assertEquals(4003, ex.getErrorCode().code());
        assertEquals(1, first.calls().size());
        assertEquals(1, second.calls().size());
        assertTrue(third.calls().isEmpty());
        assertTrue(repository.load(1L).isEmpty());
    }

    @Test
    void tc_a19_lumiroseExceptionPassesThrough() {
        var locked = new RecordingTool("locked", "锁定");
        locked.willThrow(new ToolException(LumiRoseErrorCode.TOOL_FILE_EXISTS, "locked", "exists"));
        server.enqueue(200, toolCalls("locked"));

        var ex = assertThrows(ToolException.class, () -> agent(locked).run(1L, "hi"));

        assertEquals(4006, ex.getErrorCode().code());
    }

    @Test
    void tc_a24_malformedToolCallRejected() {
        server.enqueue(200, "{\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\","
                + "\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":["
                + "{\"id\":\"c1\",\"type\":\"function\"}]}}]}");

        var ex = assertThrows(AgentException.class, () -> agentWithNothing().run(1L, "hi"));

        assertEquals(3003, ex.getErrorCode().code());
    }

    @Test
    void tc_a26_largeToolResultIsKept() throws Exception {
        var big = new RecordingTool("big", "大");
        big.willReturn("x".repeat(100_000));
        server.enqueue(200, toolCalls("big"));
        server.enqueue(200, stopResponse("done"));

        agent(big).run(1L, "hi");

        JsonNode sent = mapper.readTree(server.lastRequest().body()).get("messages");
        assertEquals(100_000, sent.get(3).get("content").asText().length());
    }

    private LumiRoseAgent agent(RecordingTool tool) {
        return agent(ToolContext.builder().register(tool).build());
    }

    private LumiRoseAgent agent(ToolContext toolContext) {
        return LumiRoseAgent.builder()
                .client(LumiRoseClient.builder()
                        .baseUrl(server.baseUrl())
                        .apiKey("test-key")
                        .model("deepseek-chat")
                        .build())
                .toolContext(toolContext)
                .repository(repository)
                .build();
    }

    private LumiRoseAgent agentWithNothing() {
        return agent(ToolContext.builder().build());
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

    private String stopResponse(String content) {
        return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}]}";
    }
}
