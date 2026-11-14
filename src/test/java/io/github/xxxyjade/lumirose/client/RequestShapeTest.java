package io.github.xxxyjade.lumirose.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionAssistantMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionRequestParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionSystemMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionToolParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionUserMessageParam;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import io.github.xxxyjade.lumirose.tool.TimeTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RequestShapeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private FakeServer server;
    private LumiRoseClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeServer();
        server.enqueue(200, stopResponse());
        client = LumiRoseClient.builder()
                .baseUrl(server.baseUrl())
                .apiKey("test-key")
                .model("deepseek-chat")
                .build();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void tc_q01_noToolsNoMaxTokens() throws Exception {
        client.chatCompletion(request(user("hi")));

        var recorded = server.lastRequest();
        JsonNode body = mapper.readTree(recorded.body());

        assertEquals(Set.of("model", "messages"), fieldNames(body));
        assertEquals("deepseek-chat", body.get("model").asText());
        assertEquals("POST", recorded.method());
        assertEquals("Bearer test-key", recorded.header("Authorization"));
        assertEquals("application/json; charset=utf-8", recorded.header("Content-Type"));
        assertEquals("application/json", recorded.header("Accept"));
    }

    @Test
    void tc_q02_toolsIncluded() throws Exception {
        var tools = List.of(ChatCompletionToolParam.builder().function(new TimeTool()).build());

        client.chatCompletion(ChatCompletionRequestParam.builder()
                .messages(List.of(user("hi")))
                .tools(tools)
                .build());

        JsonNode body = mapper.readTree(server.lastRequest().body());

        assertEquals("function", body.at("/tools/0/type").asText());
        assertEquals("get_current_time", body.at("/tools/0/function/name").asText());
        assertEquals("object", body.at("/tools/0/function/parameters/type").asText());
    }

    @Test
    void tc_q03_maxTokensIncluded() throws Exception {
        LumiRoseClient limited = LumiRoseClient.builder()
                .baseUrl(server.baseUrl())
                .apiKey("test-key")
                .model("deepseek-chat")
                .maxTokens(512)
                .build();

        limited.chatCompletion(request(user("hi")));

        JsonNode body = mapper.readTree(server.lastRequest().body());

        assertEquals(512, body.get("max_tokens").asInt());
    }

    @Test
    void tc_q04_maxTokensAbsentWhenNotConfigured() throws Exception {
        client.chatCompletion(request(user("hi")));

        JsonNode body = mapper.readTree(server.lastRequest().body());

        assertFalse(body.has("max_tokens"));
    }

    @Test
    void tc_q05_postsToChatCompletions() throws Exception {
        client.chatCompletion(request(user("hi")));

        assertEquals("POST", server.lastRequest().method());
        assertEquals("/chat/completions", server.lastRequest().path());
    }

    @Test
    void tc_q06_messagesKeepOrder() throws Exception {
        List<ChatCompletionMessageParam> messages = List.of(
                ChatCompletionSystemMessageParam.builder().content("sys").build(),
                ChatCompletionUserMessageParam.builder().content("u1").build(),
                ChatCompletionAssistantMessageParam.builder().content("a1").build(),
                ChatCompletionUserMessageParam.builder().content("u2").build());

        client.chatCompletion(ChatCompletionRequestParam.builder().messages(messages).build());

        JsonNode body = mapper.readTree(server.lastRequest().body());
        JsonNode sent = body.get("messages");

        assertEquals(4, sent.size());
        assertEquals("system", sent.get(0).get("role").asText());
        assertEquals("user", sent.get(1).get("role").asText());
        assertEquals("assistant", sent.get(2).get("role").asText());
        assertEquals("user", sent.get(3).get("role").asText());
    }

    private ChatCompletionRequestParam request(ChatCompletionMessageParam... messages) {
        return ChatCompletionRequestParam.builder().messages(List.of(messages)).build();
    }

    private ChatCompletionMessageParam user(String content) {
        return ChatCompletionUserMessageParam.builder().content(content).build();
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private String stopResponse() {
        return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
    }
}
