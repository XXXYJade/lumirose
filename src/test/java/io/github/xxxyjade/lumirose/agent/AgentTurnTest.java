package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.exception.AgentException;
import io.github.xxxyjade.lumirose.exception.ConfigurationException;
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

class AgentTurnTest {

    private FakeServer server;
    private InMemoryMessageRepository repository;
    private RecordingTool tool;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeServer();
        repository = new InMemoryMessageRepository();
        tool = new RecordingTool("probe", "探测");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void tc_a08_turnLimitKeepsHistoryEmpty() {
        server.enqueue(200, toolCallResponse());
        server.enqueue(200, toolCallResponse());

        var ex = assertThrows(AgentException.class, () -> agent(2).run(1L, "hi"));

        assertEquals(3001, ex.getErrorCode().code());
        assertTrue(repository.load(1L).isEmpty());
    }

    @Test
    void tc_a20_turnLimitStopsAfterTenRequests() {
        for (int i = 0; i < 10; i++) {
            server.enqueue(200, toolCallResponse());
        }

        var ex = assertThrows(AgentException.class, () -> agent(10).run(1L, "转圈"));

        assertEquals(3001, ex.getErrorCode().code());
        assertEquals(10, server.requests().size());
        assertTrue(repository.load(1L).isEmpty());
    }

    @Test
    void tc_a21_turnLimitStopsAfterThreeRequests() {
        for (int i = 0; i < 3; i++) {
            server.enqueue(200, toolCallResponse());
        }

        var ex = assertThrows(AgentException.class, () -> agent(3).run(1L, "转圈"));

        assertEquals(3001, ex.getErrorCode().code());
        assertEquals(3, server.requests().size());
    }

    @Test
    void tc_a22_nonPositiveMaxTurnsRejected() {
        var ex = assertThrows(ConfigurationException.class, () -> agent(0));

        assertEquals(1001, ex.getErrorCode().code());
    }

    @Test
    void tc_a23_singleTurnReturnsNormally() {
        server.enqueue(200, stopResponse("done"));

        assertEquals("done", agent(1).run(1L, "hi"));
        assertEquals(1, server.requests().size());
    }

    private LumiRoseAgent agent(int maxTurns) {
        return LumiRoseAgent.builder()
                .client(LumiRoseClient.builder()
                        .baseUrl(server.baseUrl())
                        .apiKey("test-key")
                        .model("deepseek-chat")
                        .build())
                .toolContext(ToolContext.builder().register(tool).build())
                .repository(repository)
                .maxTurns(maxTurns)
                .build();
    }

    private String toolCallResponse() {
        return "{\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\","
                + "\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":["
                + "{\"id\":\"c1\",\"type\":\"function\",\"function\":{\"name\":\"probe\",\"arguments\":\"{}\"}}]}}]}";
    }

    private String stopResponse(String content) {
        return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}]}";
    }
}
