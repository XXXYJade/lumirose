package io.github.xxxyjade.lumirose.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.xxxyjade.lumirose.common.enums.MessageRole;
import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseException;
import io.github.xxxyjade.lumirose.common.exception.ToolException;
import io.github.xxxyjade.lumirose.common.result.LumiRoseResult;
import io.github.xxxyjade.lumirose.model.entity.Conversation;
import io.github.xxxyjade.lumirose.tool.EmptyParameters;
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 工具循环的顺序、历史重建、失败不提交和轮次上限测试。
 */
class AgentToolLoopTest {

    private AgentFixture fixture;
    private final List<String> executions = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        fixture = new AgentFixture();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void executesToolCallsInOrderAndPersistsOneCommit() {
        fixture.server.enqueueSse(AgentFixture.toolReply("我先读一下",
                AgentFixture.ToolCallSpec.of("call_1", "read_file", "{}"),
                AgentFixture.ToolCallSpec.of("call_2", "write_file", "{}")));
        fixture.server.enqueueSse(AgentFixture.textReply("完成", 10, 5, 15));
        AgentFixture.CountingRepository counting =
                new AgentFixture.CountingRepository(fixture.repository);

        LumiRoseResult result = fixture.agent(tools(), counting).build().run(1L, "do it");

        assertEquals("我先读一下完成", result.content());
        assertEquals(List.of("read_file", "write_file"), executions);
        assertEquals(1, counting.appends);
        assertEquals(0, counting.compacts);

        List<io.github.xxxyjade.lumirose.testing.FakeServer.RecordedRequest> requests =
                fixture.server.requests();
        assertEquals(2, requests.size());
        JsonNode messages = AgentFixture.body(requests.get(1)).get("messages");
        assertEquals(5, messages.size());
        assertEquals("assistant", messages.get(2).get("role").asText());
        assertEquals("我先读一下", messages.get(2).get("content").asText());
        assertEquals(2, messages.get(2).get("tool_calls").size());
        assertEquals("call_1", messages.get(2).get("tool_calls").get(0).get("id").asText());
        assertEquals("call_1", messages.get(3).get("tool_call_id").asText());
        assertEquals("A", messages.get(3).get("content").asText());
        assertEquals("call_2", messages.get(4).get("tool_call_id").asText());
        assertEquals("B", messages.get(4).get("content").asText());
        assertEquals(1, countRole(messages, "user"));

        Conversation loaded = fixture.repository.load(1L);
        assertEquals(5, loaded.messages().size());
        assertEquals(MessageRole.USER, loaded.messages().get(0).role());
        assertEquals(MessageRole.ASSISTANT, loaded.messages().get(1).role());
        assertEquals(2, loaded.messages().get(1).toolCalls().size());
        assertEquals(MessageRole.TOOL, loaded.messages().get(2).role());
        assertEquals(MessageRole.TOOL, loaded.messages().get(3).role());
        assertEquals("完成", loaded.messages().get(4).content());
        assertEquals(15, loaded.contextTokens());
    }

    @Test
    void toolFailureDoesNotCommit() {
        ToolContext failing = ToolContext.builder()
                .register(Tool.of("boom", "失败", new EmptyParameters(), parameters -> {
                    throw new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, "boom", "失败");
                }))
                .build();
        fixture.server.enqueueSse(AgentFixture.toolReply(null,
                AgentFixture.ToolCallSpec.of("call_1", "boom", "{}")));

        LumiRoseException error = assertThrows(LumiRoseException.class,
                () -> fixture.agent(failing).build().run(1L, "hi"));

        assertEquals(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, error.getErrorCode());
        assertEquals(0, fixture.repository.load(1L).messages().size());
    }

    @Test
    void turnLimitStopsEndlessToolLoop() {
        fixture.server.enqueueSse(AgentFixture.toolReply(null,
                AgentFixture.ToolCallSpec.of("call_1", "read_file", "{}")));
        fixture.server.enqueueSse(AgentFixture.toolReply(null,
                AgentFixture.ToolCallSpec.of("call_2", "read_file", "{}")));

        AgentException error = assertThrows(AgentException.class,
                () -> fixture.agent(tools()).maxTurns(2).build().run(1L, "hi"));

        assertEquals(LumiRoseErrorCode.AGENT_TURN_LIMIT, error.getErrorCode());
        assertEquals(2, executions.size());
        assertEquals(0, fixture.repository.load(1L).messages().size());
    }

    private ToolContext tools() {
        return ToolContext.builder()
                .register(Tool.of("read_file", "读", new EmptyParameters(), parameters -> {
                    executions.add("read_file");
                    return "A";
                }))
                .register(Tool.of("write_file", "写", new EmptyParameters(), parameters -> {
                    executions.add("write_file");
                    return "B";
                }))
                .build();
    }

    private static long countRole(JsonNode messages, String role) {
        long count = 0;
        for (JsonNode message : messages) {
            if (role.equals(message.get("role").asText())) {
                count++;
            }
        }
        return count;
    }
}
