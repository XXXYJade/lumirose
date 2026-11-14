package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.exception.AgentException;
import io.github.xxxyjade.lumirose.repository.InMemoryMessageRepository;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import io.github.xxxyjade.lumirose.testing.RecordingTool;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentDecisionTest {

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
    void tc_a10_lengthIsTruncatedError() {
        server.enqueue(200, "{\"choices\":[{\"index\":0,\"finish_reason\":\"length\","
                + "\"message\":{\"role\":\"assistant\",\"content\":\"半截\"}}]}");

        var ex = assertThrows(AgentException.class, () -> agent(ToolContext.builder().build()).run(1L, "写长文"));

        assertEquals(3002, ex.getErrorCode().code());
    }

    @Test
    void tc_a11_nullContentWithoutToolCallsRejected() {
        server.enqueue(200, "{\"choices\":[{\"index\":0,\"finish_reason\":\"stop\","
                + "\"message\":{\"role\":\"assistant\",\"content\":null}}]}");

        var ex = assertThrows(AgentException.class, () -> agent(ToolContext.builder().build()).run(1L, "hi"));

        assertEquals(3003, ex.getErrorCode().code());
    }

    @Test
    void tc_a12_blankContentRejected() {
        server.enqueue(200, "{\"choices\":[{\"index\":0,\"finish_reason\":\"stop\","
                + "\"message\":{\"role\":\"assistant\",\"content\":\"\"}}]}");

        var ex = assertThrows(AgentException.class, () -> agent(ToolContext.builder().build()).run(1L, "hi"));

        assertEquals(3003, ex.getErrorCode().code());
    }

    @Test
    void tc_a13_contentFilterRejected() {
        server.enqueue(200, "{\"choices\":[{\"index\":0,\"finish_reason\":\"content_filter\","
                + "\"message\":{\"role\":\"assistant\",\"content\":\"x\"}}]}");

        var ex = assertThrows(AgentException.class, () -> agent(ToolContext.builder().build()).run(1L, "hi"));

        assertEquals(3003, ex.getErrorCode().code());
    }

    @Test
    void tc_a14_emptyToolCallsWithToolCallFinishRejected() {
        server.enqueue(200, "{\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\","
                + "\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[]}}]}");

        var ex = assertThrows(AgentException.class, () -> agent(ToolContext.builder().build()).run(1L, "hi"));

        assertEquals(3003, ex.getErrorCode().code());
    }

    @Test
    void tc_a15_stopWithToolCallsStillExecutes() {
        var probe = new RecordingTool("probe", "探测");
        server.enqueue(200, "{\"choices\":[{\"index\":0,\"finish_reason\":\"stop\","
                + "\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":["
                + "{\"id\":\"c1\",\"type\":\"function\",\"function\":{\"name\":\"probe\",\"arguments\":\"{}\"}}]}}]}");
        server.enqueue(200, stopResponse("done"));

        String result = agent(ToolContext.builder().register(probe).build()).run(1L, "hi");

        assertEquals("done", result);
        assertEquals(1, probe.calls().size());
    }

    @Test
    void tc_a27_unknownFinishReasonRejected() {
        server.enqueue(200, "{\"choices\":[{\"index\":0,\"finish_reason\":\"weird\","
                + "\"message\":{\"role\":\"assistant\",\"content\":\"x\"}}]}");

        var ex = assertThrows(AgentException.class, () -> agent(ToolContext.builder().build()).run(1L, "hi"));

        assertEquals(3003, ex.getErrorCode().code());
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

    private String stopResponse(String content) {
        return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}]}";
    }
}
