package io.github.xxxyjade.lumirose.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xxxyjade.lumirose.common.enums.MessageRole;
import io.github.xxxyjade.lumirose.common.exception.ClientException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseException;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionMessage;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionRequest;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionResponse;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionTool;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionToolCall;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionUsage;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求形状、SSE 解析与增量装配测试。
 */
class ClientSseTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String API_KEY = "sk-test-secret";
    private static final String MODEL = "test-model";
    private static final String DONE = "data: [DONE]\n\n";

    private FakeServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void sendsStreamingRequestWithHeadersAndBody() throws IOException {
        server.respondSse(delta("{\"content\":\"hi\"}") + finish("stop") + DONE);

        client().chatCompletion(request());

        FakeServer.RecordedRequest recorded = server.lastRequest();
        assertEquals("POST", recorded.method());
        assertEquals("/chat/completions", recorded.path());
        assertEquals("Bearer " + API_KEY, recorded.header("Authorization"));
        assertEquals("application/json; charset=utf-8", recorded.header("Content-Type"));
        assertEquals("text/event-stream", recorded.header("Accept"));

        JsonNode body = JSON.readTree(recorded.body());
        assertEquals(MODEL, body.get("model").asText());
        assertTrue(body.get("stream").asBoolean());
        assertEquals("user", body.get("messages").get(0).get("role").asText());
        assertEquals("hi", body.get("messages").get(0).get("content").asText());
        assertFalse(body.has("tools"));
        assertFalse(body.has("max_tokens"));
    }

    @Test
    void sendsToolsOnlyWhenPresent() throws IOException {
        server.respondSse(delta("{\"content\":\"ok\"}") + finish("stop") + DONE);
        ChatCompletionTool tool = new ChatCompletionTool("function",
                new ChatCompletionTool.Function("read_file", "读文件",
                        JSON.createObjectNode().put("type", "object")));

        client().chatCompletion(new ChatCompletionRequest(
                List.of(ChatCompletionMessage.user("hi")), List.of(tool), null));

        JsonNode body = JSON.readTree(server.lastRequest().body());
        JsonNode tools = body.get("tools");
        assertEquals(1, tools.size());
        assertEquals("function", tools.get(0).get("type").asText());
        assertEquals("read_file", tools.get(0).get("function").get("name").asText());
        assertEquals("读文件", tools.get(0).get("function").get("description").asText());
    }

    @Test
    void requestMaxTokensOverridesClientDefault() throws IOException {
        server.respondSse(delta("{\"content\":\"ok\"}") + finish("stop") + DONE);
        LumiRoseClient client = LumiRoseClient.builder()
                .baseUrl(server.baseUrl())
                .apiKey(API_KEY)
                .model(MODEL)
                .maxTokens(50)
                .build();

        client.chatCompletion(request());
        assertEquals(50, JSON.readTree(server.lastRequest().body()).get("max_tokens").asInt());

        client.chatCompletion(new ChatCompletionRequest(
                List.of(ChatCompletionMessage.user("hi")), List.of(), 10));
        assertEquals(10, JSON.readTree(server.lastRequest().body()).get("max_tokens").asInt());
    }

    @Test
    void normalizesTrailingSlashesInBaseUrl() {
        server.respondSse(delta("{\"content\":\"ok\"}") + finish("stop") + DONE);
        LumiRoseClient client = LumiRoseClient.builder()
                .baseUrl(server.baseUrl() + "///")
                .apiKey(API_KEY)
                .model(MODEL)
                .build();

        client.chatCompletion(request());

        assertEquals("/chat/completions", server.lastRequest().path());
    }

    @Test
    void accumulatesContentAndInvokesCallback() {
        server.respondSse(delta("{\"content\":\"你好\"}")
                + delta("{\"content\":\"\"}")
                + delta("{\"content\":\"世界\"}")
                + finish("stop")
                + DONE);
        List<String> fragments = new ArrayList<>();

        ChatCompletionResponse response = client().chatCompletion(request(), fragments::add);

        assertEquals(List.of("你好", "世界"), fragments);
        ChatCompletionMessage message = response.choices().get(0).message();
        assertEquals(MessageRole.ASSISTANT, message.role());
        assertEquals("你好世界", message.content());
        assertNull(message.reasoningContent());
        assertTrue(message.toolCalls().isEmpty());
        assertEquals("stop", response.choices().get(0).finishReason());
        assertNull(response.usage());
    }

    @Test
    void accumulatesReasoningSeparately() {
        server.respondSse(delta("{\"reasoning_content\":\"step1\"}")
                + delta("{\"reasoning_content\":\"step2\"}")
                + finish("stop")
                + DONE);

        ChatCompletionMessage message = client().chatCompletion(request())
                .choices().get(0).message();

        assertEquals("step1step2", message.reasoningContent());
        assertNull(message.content());
    }

    @Test
    void mergesToolCallFragmentsByIndexAndOrdersThem() {
        server.respondSse(
                delta("{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"read_file\",\"arguments\":\"{\\\"pa\"}}]}")
                        + delta("{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"arguments\":\"th\\\":\\\"a.txt\\\"}\"}}]}")
                        + delta("{\"tool_calls\":[{\"index\":1,\"id\":\"call_0\","
                        + "\"function\":{\"name\":\"write_file\",\"arguments\":\"{}\"}}]}")
                        + finish("tool_calls")
                        + DONE);

        ChatCompletionResponse response = client().chatCompletion(request());
        ChatCompletionMessage message = response.choices().get(0).message();

        assertEquals(2, message.toolCalls().size());
        ChatCompletionToolCall first = message.toolCalls().get(0);
        assertEquals("call_1", first.id());
        assertEquals("function", first.type());
        assertEquals("read_file", first.function().name());
        assertEquals("{\"path\":\"a.txt\"}", first.function().arguments());
        ChatCompletionToolCall second = message.toolCalls().get(1);
        assertEquals("call_0", second.id());
        assertEquals("write_file", second.function().name());
        assertEquals("tool_calls", response.choices().get(0).finishReason());
        assertNull(message.content());
    }

    @Test
    void ignoresCommentsEventsIdsAndSupportsMultiLineDataAndCrlf() {
        server.respondSse(": keepalive\r\n"
                + "event: message\r\n"
                + "id: 42\r\n"
                + "data: {\"choices\":[{\"delta\":\r\n"
                + "data: {\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}\r\n"
                + "\r\n"
                + DONE);

        ChatCompletionMessage message = client().chatCompletion(request())
                .choices().get(0).message();

        assertEquals("hi", message.content());
    }

    @Test
    void usesLastUsage() {
        server.respondSse(delta("{\"content\":\"a\"}")
                + "data: {\"choices\":[{\"delta\":{}}],\"usage\":"
                + "{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}\n\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":"
                + "{\"prompt_tokens\":2,\"completion_tokens\":3,\"total_tokens\":5}}\n\n"
                + DONE);

        ChatCompletionUsage usage = client().chatCompletion(request()).usage();

        assertEquals(2, usage.promptTokens().intValue());
        assertEquals(3, usage.completionTokens().intValue());
        assertEquals(5, usage.totalTokens().intValue());
    }

    @Test
    void rejectsInvalidJson() {
        server.respondSse("data: not-json\n\n" + DONE);

        assertResponseInvalid(() -> client().chatCompletion(request()));
    }

    @Test
    void rejectsEmptyChoices() {
        server.respondSse("data: {\"choices\":[]}\n\n" + DONE);

        assertResponseInvalid(() -> client().chatCompletion(request()));
    }

    @Test
    void rejectsMissingFinishReason() {
        server.respondSse(delta("{\"content\":\"hi\"}") + DONE);

        assertResponseInvalid(() -> client().chatCompletion(request()));
    }

    @Test
    void rejectsMissingDoneMarker() {
        server.respondSse(delta("{\"content\":\"hi\"}") + finish("stop"));

        assertResponseInvalid(() -> client().chatCompletion(request()));
    }

    @Test
    void rejectsEmptyStream() {
        server.respondSse("");

        assertResponseInvalid(() -> client().chatCompletion(request()));
    }

    @Test
    void rejectsConflictingToolCallId() {
        server.respondSse(
                delta("{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\","
                        + "\"function\":{\"name\":\"read_file\"}}]}")
                        + delta("{\"tool_calls\":[{\"index\":0,\"id\":\"call_2\"}]}")
                        + finish("tool_calls")
                        + DONE);

        assertResponseInvalid(() -> client().chatCompletion(request()));
    }

    @Test
    void rejectsConflictingToolCallName() {
        server.respondSse(
                delta("{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\","
                        + "\"function\":{\"name\":\"read_file\"}}]}")
                        + delta("{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"name\":\"write_file\"}}]}")
                        + finish("tool_calls")
                        + DONE);

        assertResponseInvalid(() -> client().chatCompletion(request()));
    }

    @Test
    void rejectsIncompleteToolCall() {
        server.respondSse(
                delta("{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"arguments\":\"{}\"}}]}")
                        + finish("tool_calls")
                        + DONE);

        assertResponseInvalid(() -> client().chatCompletion(request()));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertConfigInvalid(() -> LumiRoseClient.builder().apiKey(API_KEY).model(MODEL).build());
        assertConfigInvalid(() -> LumiRoseClient.builder()
                .baseUrl("ftp://example.com").apiKey(API_KEY).model(MODEL).build());
        assertConfigInvalid(() -> LumiRoseClient.builder()
                .baseUrl(server.baseUrl()).model(MODEL).build());
        assertConfigInvalid(() -> LumiRoseClient.builder()
                .baseUrl(server.baseUrl()).apiKey(API_KEY).build());
        assertConfigInvalid(() -> LumiRoseClient.builder()
                .baseUrl(server.baseUrl()).apiKey(" ").model(MODEL).build());
        assertConfigInvalid(() -> LumiRoseClient.builder()
                .baseUrl(server.baseUrl()).apiKey(API_KEY).model(MODEL).maxTokens(0).build());
        assertConfigInvalid(() -> LumiRoseClient.builder()
                .baseUrl(server.baseUrl()).apiKey(API_KEY).model(MODEL).maxTokens(-1).build());
    }

    private LumiRoseClient client() {
        return LumiRoseClient.builder()
                .baseUrl(server.baseUrl())
                .apiKey(API_KEY)
                .model(MODEL)
                .build();
    }

    private static ChatCompletionRequest request() {
        return new ChatCompletionRequest(
                List.of(ChatCompletionMessage.user("hi")), List.of(), null);
    }

    private static String delta(String json) {
        return "data: {\"choices\":[{\"delta\":" + json + "}]}\n\n";
    }

    private static String finish(String reason) {
        return "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"" + reason + "\"}]}\n\n";
    }

    private static void assertResponseInvalid(Executable executable) {
        ClientException error = assertThrows(ClientException.class, executable);
        assertEquals(LumiRoseErrorCode.CLIENT_RESPONSE_INVALID, error.getErrorCode());
    }

    private static void assertConfigInvalid(Executable executable) {
        LumiRoseException error = assertThrows(LumiRoseException.class, executable);
        assertEquals(LumiRoseErrorCode.CONFIG_INVALID, error.getErrorCode());
    }
}
