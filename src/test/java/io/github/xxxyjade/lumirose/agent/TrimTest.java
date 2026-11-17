package io.github.xxxyjade.lumirose.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionAssistantMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionSystemMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionToolCall;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionToolMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionUserMessageParam;
import io.github.xxxyjade.lumirose.repository.InMemoryMessageRepository;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrimTest {

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
    void tc_tr01_dropsAllHistoryUnderTinyBudget() throws Exception {
        repository.appendAll(1L, List.of(user("u1"), assistant("a1"), user("u2"), assistant("a2")));
        server.enqueue(200, stopJson("ok"));

        agent(10).run(1L, "now");

        var sent = sentMessages();
        assertEquals(1, sent.size());
        assertEquals("now", sent.get(0).get("content").asText());
        assertEquals(6, repository.load(1L).size());
    }

    @Test
    void tc_tr02_noTrimWhenUnderBudget() throws Exception {
        repository.appendAll(1L, List.of(user("u1"), assistant("a1"), user("u2"), assistant("a2")));
        server.enqueue(200, stopJson("ok"));

        agent(10000).run(1L, "now");

        assertEquals(5, sentMessages().size());
    }

    @Test
    void tc_tr03_dropsWholeToolBlock() throws Exception {
        repository.appendAll(1L, List.of(
                user("u1"),
                toolCallAssistant("c1", "t1"),
                toolResult("c1", "r1"),
                user("u2"),
                assistant("a2")));
        server.enqueue(200, stopJson("ok"));

        agent(31).run(1L, "now");

        var sent = sentMessages();
        assertEquals(3, sent.size());
        for (var message : sent) {
            assertTrue(!"tool".equals(message.get("role").asText()));
        }
        assertEquals("user", sent.get(0).get("role").asText());
        assertEquals("assistant", sent.get(1).get("role").asText());
    }

    @Test
    void tc_tr04_systemAlwaysKept() throws Exception {
        repository.appendAll(1L, List.of(system("sys"), user("u1"), assistant("a1")));
        server.enqueue(200, stopJson("ok"));

        agent(10).run(1L, "now");

        var sent = sentMessages();
        assertEquals(2, sent.size());
        assertEquals("system", sent.get(0).get("role").asText());
        assertEquals("now", sent.get(1).get("content").asText());
    }

    @Test
    void tc_tr05_repositoryUnchanged() {
        repository.appendAll(1L, List.of(user("u1"), assistant("a1"), user("u2"), assistant("a2")));
        server.enqueue(200, stopJson("ok"));

        agent(10).run(1L, "now");

        var history = repository.load(1L);
        assertEquals(6, history.size());
        assertEquals("now", ((ChatCompletionUserMessageParam) history.get(4)).content());
        assertEquals("ok", ((ChatCompletionAssistantMessageParam) history.get(5)).content());
    }

    @Test
    void tc_tr06_oversizedMessageSentAsIs() throws Exception {
        server.enqueue(200, stopJson("ok"));
        String big = "x".repeat(1000);

        agent(10).run(1L, big);

        var sent = sentMessages();
        assertEquals(2, sent.size());
        assertEquals("system", sent.get(0).get("role").asText());
        assertEquals(1000, sent.get(1).get("content").asText().length());
    }

    @Test
    void tc_tr07_invalidBudgetRejected() {
        assertThrows(ConfigurationException.class, () -> agent(0));
        assertThrows(ConfigurationException.class, () -> agent(-1));
    }

    @Test
    void tc_tr08_noOrphanToolMessage() throws Exception {
        repository.appendAll(1L, List.of(toolResult("c1", "o1"), toolResult("c2", "o2")));
        server.enqueue(200, stopJson("ok"));

        agent(20).run(1L, "now");

        var sent = sentMessages();
        assertEquals(1, sent.size());
        assertEquals("user", sent.get(0).get("role").asText());
        assertEquals("now", sent.get(0).get("content").asText());
    }

    private LumiRoseAgent agent(int budget) {
        return LumiRoseAgent.builder()
                .client(LumiRoseClient.builder()
                        .baseUrl(server.baseUrl())
                        .apiKey("test-key")
                        .model("deepseek-chat")
                        .build())
                .toolContext(ToolContext.builder().build())
                .repository(repository)
                .contextBudgetChars(budget)
                .build();
    }

    private com.fasterxml.jackson.databind.JsonNode sentMessages() throws Exception {
        return mapper.readTree(server.lastRequest().body()).get("messages");
    }

    private static ChatCompletionSystemMessageParam system(String content) {
        return ChatCompletionSystemMessageParam.builder().content(content).build();
    }

    private static ChatCompletionUserMessageParam user(String content) {
        return ChatCompletionUserMessageParam.builder().content(content).build();
    }

    private static ChatCompletionAssistantMessageParam assistant(String content) {
        return ChatCompletionAssistantMessageParam.builder().content(content).build();
    }

    private static ChatCompletionAssistantMessageParam toolCallAssistant(String id, String name) {
        return ChatCompletionAssistantMessageParam.builder()
                .content(null)
                .toolCalls(List.of(ChatCompletionToolCall.builder()
                        .id(id)
                        .type("function")
                        .function(ChatCompletionToolCall.Function.builder()
                                .name(name)
                                .arguments("{}")
                                .build())
                        .build()))
                .build();
    }

    private static ChatCompletionToolMessageParam toolResult(String id, String content) {
        return ChatCompletionToolMessageParam.builder().toolCallId(id).content(content).build();
    }

    private String stopJson(String content) {
        return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}]}";
    }
}
