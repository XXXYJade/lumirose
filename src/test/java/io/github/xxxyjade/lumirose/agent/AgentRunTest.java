package io.github.xxxyjade.lumirose.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.xxxyjade.lumirose.common.enums.MessageRole;
import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.result.LumiRoseResult;
import io.github.xxxyjade.lumirose.model.entity.Conversation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 首次/二次运行、空输入校验、默认 system 与 run/streamRun 等价性测试。
 */
class AgentRunTest {

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
    void firstRunPersistsUserAndAssistant() {
        fixture.server.enqueueSse(AgentFixture.textReply("hello", 3, 5, 8));

        LumiRoseResult result = fixture.agent().build().run(1L, "hi");

        assertEquals("hello", result.content());
        assertEquals(3, result.promptTokens());
        assertEquals(5, result.completionTokens());

        Conversation loaded = fixture.repository.load(1L);
        assertEquals(2, loaded.messages().size());
        assertEquals(MessageRole.USER, loaded.messages().get(0).role());
        assertEquals("hi", loaded.messages().get(0).content());
        assertEquals(MessageRole.ASSISTANT, loaded.messages().get(1).role());
        assertEquals("hello", loaded.messages().get(1).content());
        assertEquals(8, loaded.contextTokens());
    }

    @Test
    void secondRunKeepsHistoryAndAppends() {
        fixture.server.enqueueSse(AgentFixture.textReply("one"));
        fixture.agent().build().run(1L, "first");
        fixture.server.enqueueSse(AgentFixture.textReply("two"));

        LumiRoseResult second = fixture.agent().build().run(1L, "second");

        assertEquals("two", second.content());
        Conversation loaded = fixture.repository.load(1L);
        assertEquals(4, loaded.messages().size());
        assertEquals("first", loaded.messages().get(0).content());
        assertEquals("one", loaded.messages().get(1).content());
        assertEquals("second", loaded.messages().get(2).content());
        assertEquals("two", loaded.messages().get(3).content());

        JsonNode messages = AgentFixture.body(fixture.server.requests().get(1)).get("messages");
        assertEquals(4, messages.size());
        assertEquals("user", messages.get(1).get("role").asText());
        assertEquals("first", messages.get(1).get("content").asText());
        assertEquals("assistant", messages.get(2).get("role").asText());
        assertEquals("one", messages.get(2).get("content").asText());
        assertEquals("user", messages.get(3).get("role").asText());
        assertEquals("second", messages.get(3).get("content").asText());
    }

    @Test
    void rejectsBlankContent() {
        AgentException error = assertThrows(AgentException.class,
                () -> fixture.agent().build().run(1L, "   "));

        assertEquals(LumiRoseErrorCode.PARAM_INVALID, error.getErrorCode());
        assertEquals(0, fixture.server.requests().size());
        assertEquals(0, fixture.repository.load(1L).messages().size());
    }

    @Test
    void sendsDefaultSystemMessage() {
        fixture.server.enqueueSse(AgentFixture.textReply("ok"));

        fixture.agent().build().run(1L, "hi");

        JsonNode messages = AgentFixture.body(fixture.server.requests().get(0)).get("messages");
        assertEquals("system", messages.get(0).get("role").asText());
        assertEquals("You are a helpful assistant!", messages.get(0).get("content").asText());
        assertEquals("user", messages.get(1).get("role").asText());
    }

    @Test
    void runAndStreamRunProduceSameResult() {
        fixture.server.enqueueSse(AgentFixture.textReply("same", 2, 3, 5));
        LumiRoseResult blocking = fixture.agent().build().run(1L, "hi");

        fixture.server.enqueueSse(AgentFixture.textReply("same", 2, 3, 5));
        LumiRoseResult streaming = fixture.agent().build().streamRun(2L, "hi");
        streaming.deltas().forEach(ignored -> { });

        assertEquals(blocking.content(), streaming.content());
        assertEquals(blocking.promptTokens(), streaming.promptTokens());
        assertEquals(blocking.completionTokens(), streaming.completionTokens());
        assertEquals(fixture.repository.load(1L).contextTokens(),
                fixture.repository.load(2L).contextTokens());
    }
}
