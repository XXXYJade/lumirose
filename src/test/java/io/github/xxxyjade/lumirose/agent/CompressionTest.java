package io.github.xxxyjade.lumirose.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.result.LumiRoseResult;
import io.github.xxxyjade.lumirose.model.entity.Conversation;
import io.github.xxxyjade.lumirose.model.entity.Message;
import io.github.xxxyjade.lumirose.model.entity.ToolCall;
import io.github.xxxyjade.lumirose.repository.ConversationRepository;
import io.github.xxxyjade.lumirose.tool.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上下文压缩的阈值、摘要长度公式、请求形状、失败链路和二次压缩测试。
 */
class CompressionTest {

    private static final String SUMMARY_PREFIX = "【摘要】";

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
    void compressesBeyondThresholdAndCompactsOnce() {
        seed(1L, 700);
        fixture.server.enqueueSse(AgentFixture.textReply("SUMMARY", 5, 5, 10));
        fixture.server.enqueueSse(AgentFixture.textReply("最终回答", 20, 10, 30));
        AgentFixture.CountingRepository counting =
                new AgentFixture.CountingRepository(fixture.repository);

        LumiRoseResult result = agent(1000, counting).build().run(1L, "新问题");

        assertEquals("最终回答", result.content());
        assertEquals(1, counting.compacts);
        assertEquals(0, counting.appends);

        List<io.github.xxxyjade.lumirose.testing.FakeServer.RecordedRequest> requests =
                fixture.server.requests();
        assertEquals(2, requests.size());

        JsonNode compression = AgentFixture.body(requests.get(0));
        assertFalse(compression.has("tools"));
        assertEquals(245, compression.get("max_tokens").asInt());
        JsonNode compressionMessages = compression.get("messages");
        assertEquals(3, compressionMessages.size());
        assertTrue(compressionMessages.get(0).get("content").asText().contains("上下文压缩器"));

        JsonNode messages = AgentFixture.body(requests.get(1)).get("messages");
        assertEquals(3, messages.size());
        assertEquals("system", messages.get(0).get("role").asText());
        assertEquals("assistant", messages.get(1).get("role").asText());
        assertTrue(messages.get(1).get("content").asText().startsWith(SUMMARY_PREFIX));
        assertTrue(messages.get(1).get("content").asText().contains("SUMMARY"));
        assertEquals("user", messages.get(2).get("role").asText());
        assertEquals("新问题", messages.get(2).get("content").asText());

        Conversation loaded = fixture.repository.load(1L);
        assertEquals(3, loaded.messages().size());
        assertEquals("assistant", loaded.messages().get(0).role().wire());
        assertEquals("最终回答", loaded.messages().get(2).content());
        assertEquals(30, loaded.contextTokens());
    }

    @Test
    void doesNotCompressExactlyAtThreshold() {
        seed(1L, 650);
        fixture.server.enqueueSse(AgentFixture.textReply("ok"));

        agent(1000, fixture.repository).build().run(1L, "hi");

        assertEquals(1, fixture.server.requests().size());
        JsonNode messages = AgentFixture.body(fixture.server.requests().get(0)).get("messages");
        assertEquals(4, messages.size());
    }

    @Test
    void doesNotCompressWithoutWindow() {
        seed(1L, 100_000);
        fixture.server.enqueueSse(AgentFixture.textReply("ok"));

        agent(null, fixture.repository).build().run(1L, "hi");

        assertEquals(1, fixture.server.requests().size());
    }

    @Test
    void summaryMaxTokensFollowsFormula() {
        seed(1L, 3000);
        fixture.server.enqueueSse(AgentFixture.textReply("SUMMARY"));
        fixture.server.enqueueSse(AgentFixture.textReply("done", 1, 1, 2));

        agent(4000, fixture.repository).build().run(1L, "hi");

        JsonNode compression = AgentFixture.body(fixture.server.requests().get(0));
        assertEquals(980, compression.get("max_tokens").asInt());
    }

    @Test
    void summaryMaxTokensIsCapped() {
        seed(1L, 70_000);
        fixture.server.enqueueSse(AgentFixture.textReply("SUMMARY"));
        fixture.server.enqueueSse(AgentFixture.textReply("done", 1, 1, 2));

        agent(100_000, fixture.repository).build().run(1L, "hi");

        JsonNode compression = AgentFixture.body(fixture.server.requests().get(0));
        assertEquals(2048, compression.get("max_tokens").asInt());
    }

    @Test
    void blankSummaryFailsWithoutWrite() {
        seed(1L, 700);
        fixture.server.enqueueSse(AgentFixture.textReply("", 1, 1, 2));

        AgentException error = assertThrows(AgentException.class,
                () -> agent(1000, fixture.repository).build().run(1L, "hi"));

        assertEquals(LumiRoseErrorCode.AGENT_COMPRESSION_FAILED, error.getErrorCode());
        assertEquals(1, fixture.server.requests().size());
        assertEquals(2, fixture.repository.load(1L).messages().size());
        assertEquals(700, fixture.repository.load(1L).contextTokens());
    }

    @Test
    void truncatedSummaryFailsWithoutWrite() {
        seed(1L, 700);
        fixture.server.enqueueSse(
                AgentFixture.chunk(AgentFixture.content("片段"), "length") + AgentFixture.DONE);

        AgentException error = assertThrows(AgentException.class,
                () -> agent(1000, fixture.repository).build().run(1L, "hi"));

        assertEquals(LumiRoseErrorCode.AGENT_COMPRESSION_FAILED, error.getErrorCode());
        assertEquals(2, fixture.repository.load(1L).messages().size());
    }

    @Test
    void clientErrorDuringCompressionBecomesCompressionFailure() {
        seed(1L, 700);
        fixture.server.enqueueSse("data: not-json\n\n" + AgentFixture.DONE);

        AgentException error = assertThrows(AgentException.class,
                () -> agent(1000, fixture.repository).build().run(1L, "hi"));

        assertEquals(LumiRoseErrorCode.AGENT_COMPRESSION_FAILED, error.getErrorCode());
        assertEquals(2, fixture.repository.load(1L).messages().size());
    }

    @Test
    void missingUsageAfterCompressionFailsWithoutWrite() {
        seed(1L, 700);
        fixture.server.enqueueSse(AgentFixture.textReply("SUMMARY"));
        fixture.server.enqueueSse(AgentFixture.textReply("final"));
        AgentFixture.CountingRepository counting =
                new AgentFixture.CountingRepository(fixture.repository);

        AgentException error = assertThrows(AgentException.class,
                () -> agent(1000, counting).build().run(1L, "hi"));

        assertEquals(LumiRoseErrorCode.AGENT_COMPRESSION_FAILED, error.getErrorCode());
        assertEquals(0, counting.compacts);
        assertEquals(2, fixture.repository.load(1L).messages().size());
        assertEquals(700, fixture.repository.load(1L).contextTokens());
    }

    @Test
    void secondCompressionReplacesPreviousSummary() {
        seed(1L, 700);
        fixture.server.enqueueSse(AgentFixture.textReply("SUMMARY1"));
        fixture.server.enqueueSse(AgentFixture.textReply("A1", 10, 10, 800));
        AgentFixture.CountingRepository counting =
                new AgentFixture.CountingRepository(fixture.repository);
        agent(1000, counting).build().run(1L, "first");

        fixture.server.enqueueSse(AgentFixture.textReply("SUMMARY2"));
        fixture.server.enqueueSse(AgentFixture.textReply("A2", 10, 10, 900));
        LumiRoseResult second = agent(1000, counting).build().run(1L, "second");

        assertEquals("A2", second.content());
        assertEquals(2, counting.compacts);

        Conversation loaded = fixture.repository.load(1L);
        assertEquals(3, loaded.messages().size());
        String summary = loaded.messages().get(0).content();
        assertTrue(summary.startsWith(SUMMARY_PREFIX));
        assertTrue(summary.contains("SUMMARY2"));
        assertFalse(summary.contains("SUMMARY1"));
        assertEquals("second", loaded.messages().get(1).content());
        assertEquals("A2", loaded.messages().get(2).content());
        assertEquals(900, loaded.contextTokens());
    }

    @Test
    void compressionRequestReplaysToolTurnsWithoutDeclaringTools() {
        fixture.repository.append(1L, List.of(
                Message.user("读 a.txt 告诉我端口"),
                Message.assistant("", null,
                        List.of(new ToolCall("call_1", "function", "read_file",
                                "{\"path\":\"a.txt\"}"))),
                Message.tool("call_1", "port: 8080")),
                3000);
        fixture.server.enqueueSse(AgentFixture.textReply("SUMMARY"));
        fixture.server.enqueueSse(AgentFixture.textReply("done", 10, 5, 15));

        agent(4000, fixture.repository).build().run(1L, "端口是多少");

        JsonNode compression = AgentFixture.body(fixture.server.requests().get(0));
        // 压缩请求保留原生角色结构，但不声明 tools；这条依赖供应商接受。
        assertFalse(compression.has("tools"));
        JsonNode messages = compression.get("messages");
        assertEquals(4, messages.size());
        assertEquals("system", messages.get(0).get("role").asText());
        assertEquals("user", messages.get(1).get("role").asText());
        assertEquals("assistant", messages.get(2).get("role").asText());
        assertEquals("call_1", messages.get(2).get("tool_calls").get(0).get("id").asText());
        assertEquals("tool", messages.get(3).get("role").asText());
        assertEquals("call_1", messages.get(3).get("tool_call_id").asText());
    }

    private void seed(long conversationId, int contextTokens) {
        fixture.repository.append(conversationId,
                List.of(Message.user("旧问题"), Message.assistant("旧回答", null, List.of())),
                contextTokens);
    }

    private LumiRoseAgent.Builder agent(Integer window,
                                        ConversationRepository target) {
        return fixture.agent(ToolContext.builder().build(), target).contextWindowTokens(window);
    }
}
