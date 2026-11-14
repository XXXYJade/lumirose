package io.github.xxxyjade.lumirose.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.exception.AgentException;
import io.github.xxxyjade.lumirose.exception.StorageException;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessageParam;
import io.github.xxxyjade.lumirose.repository.InMemoryMessageRepository;
import io.github.xxxyjade.lumirose.repository.MessageRepository;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private FakeServer server;
    private InMemoryMessageRepository repository;
    private LumiRoseAgent agent;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeServer();
        repository = new InMemoryMessageRepository();
        agent = agent(repository);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void tc_a01_nullConversationIdIsGenerated() {
        var captured = new AtomicReference<Long>();
        MessageRepository recording = new MessageRepository() {
            @Override
            public List<ChatCompletionMessageParam> load(Long conversationId) {
                captured.set(conversationId);
                return List.of();
            }

            @Override
            public void appendAll(Long conversationId, List<ChatCompletionMessageParam> messages) {
                captured.set(conversationId);
            }
        };
        LumiRoseAgent generated = agent(recording);
        server.enqueue(200, stopResponse("hi"));

        assertEquals("hi", generated.run(null, "你好"));

        assertNotNull(captured.get());
        assertTrue(captured.get() > 0);
    }

    @Test
    void tc_a02_blankContentRejected() {
        var nullContent = assertThrows(AgentException.class, () -> agent.run(1L, null));
        assertEquals(1002, nullContent.getErrorCode().code());

        var blankContent = assertThrows(AgentException.class, () -> agent.run(1L, "  "));
        assertEquals(1002, blankContent.getErrorCode().code());
    }

    @Test
    void tc_a03_secondRunLoadsCommittedHistory() throws Exception {
        server.enqueue(200, stopResponse("hi"));

        assertEquals("hi", agent.run(1L, "你好"));
        assertEquals(3, repository.load(1L).size());

        server.enqueue(200, stopResponse("again"));
        agent.run(1L, "再来");

        JsonNode sent = mapper.readTree(server.lastRequest().body()).get("messages");
        assertEquals(4, sent.size());
    }

    @Test
    void tc_a04_nullSystemMessageIsSkipped() throws Exception {
        LumiRoseAgent withoutSystem = agentWithSystem(repository, null);
        server.enqueue(200, stopResponse("hi"));

        withoutSystem.run(1L, "你好");

        JsonNode sent = mapper.readTree(server.lastRequest().body()).get("messages");
        assertEquals("user", sent.get(0).get("role").asText());
        assertEquals(2, repository.load(1L).size());
    }

    @Test
    void tc_a05_blankSystemMessageIsSkipped() throws Exception {
        LumiRoseAgent withoutSystem = agentWithSystem(repository, "  ");
        server.enqueue(200, stopResponse("hi"));

        withoutSystem.run(1L, "你好");

        JsonNode sent = mapper.readTree(server.lastRequest().body()).get("messages");
        assertEquals("user", sent.get(0).get("role").asText());
        assertEquals(2, repository.load(1L).size());
    }

    @Test
    void tc_a06_conversationsAreIsolated() {
        server.enqueue(200, stopResponse("one"));
        server.enqueue(200, stopResponse("two"));

        agent.run(1L, "first");
        agent.run(2L, "second");

        assertEquals(3, repository.load(1L).size());
        assertEquals(3, repository.load(2L).size());
    }

    @Test
    void tc_a09_storageFailureWrapped() {
        MessageRepository failing = new MessageRepository() {
            @Override
            public List<ChatCompletionMessageParam> load(Long conversationId) {
                return List.of();
            }

            @Override
            public void appendAll(Long conversationId, List<ChatCompletionMessageParam> messages) {
                throw new IllegalStateException("boom");
            }
        };
        LumiRoseAgent unhealthy = agent(failing);
        server.enqueue(200, stopResponse("hi"));

        var ex = assertThrows(StorageException.class, () -> unhealthy.run(1L, "你好"));

        assertEquals(5001, ex.getErrorCode().code());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void tc_a25_firstChoiceOnly() {
        server.enqueue(200, "{\"choices\":["
                + "{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"first\"},\"finish_reason\":\"stop\"},"
                + "{\"index\":1,\"message\":{\"role\":\"assistant\",\"content\":\"second\"},\"finish_reason\":\"stop\"}"
                + "]}");

        assertEquals("first", agent.run(1L, "hi"));
    }

    private LumiRoseAgent agent(MessageRepository repository) {
        return agentWithSystem(repository, "You are a helpful assistant!");
    }

    private LumiRoseAgent agentWithSystem(MessageRepository repository, String systemMessage) {
        return LumiRoseAgent.builder()
                .client(LumiRoseClient.builder()
                        .baseUrl(server.baseUrl())
                        .apiKey("test-key")
                        .model("deepseek-chat")
                        .build())
                .toolContext(ToolContext.builder().build())
                .repository(repository)
                .systemMessage(systemMessage)
                .build();
    }

    private String stopResponse(String content) {
        return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}]}";
    }
}
