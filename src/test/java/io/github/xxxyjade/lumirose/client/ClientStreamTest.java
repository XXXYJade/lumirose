package io.github.xxxyjade.lumirose.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionRequestParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionUserMessageParam;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientStreamTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private FakeServer server;
    private LumiRoseClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeServer();
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
    void tc_st01_assemblesContentAndUsage() {
        Recorder recorder = new Recorder();
        server.enqueueSse(sse(
                chunk("{\"role\":\"assistant\",\"content\":\"\"}"),
                chunk("{\"content\":\"从前\"}"),
                chunk("{\"content\":\"有座山\"}"),
                finalChunk("\"stop\"", usage(10, 2))));

        var response = client.streamChatCompletion(request(), recorder);

        assertEquals("从前有座山", response.choices().get(0).message().content());
        assertEquals(List.of("从前", "有座山"), recorder.content);
        assertEquals(10, response.usage().promptTokens());
        assertEquals(2, response.usage().completionTokens());
    }

    @Test
    void tc_st02_roleOnlyChunkHasNoContent() {
        Recorder recorder = new Recorder();
        server.enqueueSse(sse(
                chunk("{\"role\":\"assistant\",\"content\":\"\"}"),
                finalChunk("\"stop\"", null)));

        var response = client.streamChatCompletion(request(), recorder);

        assertNull(response.choices().get(0).message().content());
        assertTrue(recorder.content.isEmpty());
    }

    @Test
    void tc_st03_assemblesToolCallFragments() {
        server.enqueueSse(sse(
                chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"read_file\",\"arguments\":\"\"}}]}"),
                chunk("{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"path\\\":\"}}]}"),
                chunk("{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"a.txt\\\"}\"}}]}"),
                finalChunk("\"tool_calls\"", null)));

        var response = client.streamChatCompletion(request(), CompletionStreamListener.NOOP);

        var toolCalls = response.choices().get(0).message().toolCalls();
        assertEquals(1, toolCalls.size());
        assertEquals("call_1", toolCalls.get(0).id());
        assertEquals("read_file", toolCalls.get(0).function().name());
        assertEquals("{\"path\":\"a.txt\"}", toolCalls.get(0).function().arguments());
        assertNull(response.choices().get(0).message().content());
    }

    @Test
    void tc_st04_assemblesInterleavedToolCalls() {
        server.enqueueSse(sse(
                chunk("{\"tool_calls\":[{\"index\":0,\"id\":\"c0\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"t0\",\"arguments\":\"\"}}]}"),
                chunk("{\"tool_calls\":[{\"index\":1,\"id\":\"c1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"t1\",\"arguments\":\"\"}}]}"),
                chunk("{\"tool_calls\":[{\"index\":1,\"function\":{\"arguments\":\"{\\\"b\\\":\"}}]}"),
                chunk("{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"a\\\":\"}}]}"),
                chunk("{\"tool_calls\":[{\"index\":1,\"function\":{\"arguments\":\"2}\"}}]}"),
                chunk("{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"1}\"}}]}"),
                finalChunk("\"tool_calls\"", null)));

        var toolCalls = client.streamChatCompletion(request(), CompletionStreamListener.NOOP)
                .choices().get(0).message().toolCalls();

        assertEquals(2, toolCalls.size());
        assertEquals("t0", toolCalls.get(0).function().name());
        assertEquals("{\"a\":1}", toolCalls.get(0).function().arguments());
        assertEquals("t1", toolCalls.get(1).function().name());
        assertEquals("{\"b\":2}", toolCalls.get(1).function().arguments());
    }

    @Test
    void tc_st05_forwardsReasoningDeltas() {
        Recorder recorder = new Recorder();
        server.enqueueSse(sse(
                chunk("{\"reasoning_content\":\"先想\"}"),
                chunk("{\"reasoning_content\":\"一下\"}"),
                chunk("{\"content\":\"答案\"}"),
                finalChunk("\"stop\"", null)));

        var response = client.streamChatCompletion(request(), recorder);

        assertEquals("先想一下", response.choices().get(0).message().reasoningContent());
        assertEquals(List.of("先想", "一下"), recorder.reasoning);
        assertEquals(List.of("答案"), recorder.content);
    }

    @Test
    void tc_st06_acceptsMultilineDataAndComments() {
        String body = ": keep-alive\n"
                + "event: message\n"
                + "data: {\"id\":\"c1\",\"choices\":[\n"
                + "data: {\"index\":0,\"delta\":{\"content\":\"x\"},\"finish_reason\":null}]}\n\n"
                + "data: " + finalChunk("\"stop\"", null) + "\n\n"
                + "data: [DONE]\n\n";
        server.enqueueSse(body);

        var response = client.streamChatCompletion(request(), CompletionStreamListener.NOOP);

        assertEquals("x", response.choices().get(0).message().content());
    }

    @Test
    void tc_st07_acceptsFinishReasonWithoutDone() {
        server.enqueueSse("data: " + chunk("{\"content\":\"ok\"}") + "\n\n"
                + "data: " + finalChunk("\"stop\"", null) + "\n\n");

        var response = client.streamChatCompletion(request(), CompletionStreamListener.NOOP);

        assertEquals("ok", response.choices().get(0).message().content());
    }

    @Test
    void tc_st08_ignoresUnknownFieldsAndEmptyChoices() {
        server.enqueueSse(sse(
                "{\"choices\":[],\"unknown\":{\"a\":1}}",
                chunk("{\"content\":\"ok\"}"),
                finalChunk("\"stop\"", null)));

        var response = client.streamChatCompletion(request(), CompletionStreamListener.NOOP);

        assertEquals("ok", response.choices().get(0).message().content());
        assertNull(response.usage());
    }

    @Test
    void tc_st09_ignoresNullContentDelta() {
        Recorder recorder = new Recorder();
        server.enqueueSse(sse(
                chunk("{\"content\":null}"),
                chunk("{\"content\":\"x\"}"),
                finalChunk("\"stop\"", null)));

        client.streamChatCompletion(request(), recorder);

        assertEquals(List.of("x"), recorder.content);
    }

    @Test
    void tc_st10_ignoresUsageOnlyChunk() {
        server.enqueueSse(sse(
                chunk("{\"content\":\"x\"}"),
                "{\"id\":\"c1\",\"usage\":" + usage(7, 8) + "}",
                finalChunk("\"stop\"", null)));

        var response = client.streamChatCompletion(request(), CompletionStreamListener.NOOP);

        assertEquals(7, response.usage().promptTokens());
        assertEquals(8, response.usage().completionTokens());
    }

    @Test
    void tc_q07_requestHasStreamFlagAndSseAccept() throws Exception {
        server.enqueueSse(sse(finalChunk("\"stop\"", null)));

        client.streamChatCompletion(request(), CompletionStreamListener.NOOP);

        var sent = server.lastRequest();
        assertEquals("text/event-stream", sent.header("accept"));
        assertEquals("application/json; charset=utf-8", sent.header("content-type"));
        var body = mapper.readTree(sent.body());
        assertTrue(body.get("stream").asBoolean());
        assertFalse(body.has("stream_options"));
    }

    private ChatCompletionRequestParam request() {
        return ChatCompletionRequestParam.builder()
                .messages(List.of(ChatCompletionUserMessageParam.builder().content("hi").build()))
                .build();
    }

    private static String chunk(String deltaJson) {
        return "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":"
                + deltaJson + ",\"finish_reason\":null}]}";
    }

    private static String finalChunk(String reasonJson, String usageJson) {
        return "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
                + "\"delta\":{},\"finish_reason\":" + reasonJson + "}]"
                + (usageJson == null ? "" : ",\"usage\":" + usageJson) + "}";
    }

    private static String usage(int prompt, int completion) {
        return "{\"prompt_tokens\":" + prompt + ",\"completion_tokens\":" + completion
                + ",\"total_tokens\":" + (prompt + completion) + "}";
    }

    private static String sse(String... payloads) {
        StringBuilder body = new StringBuilder();
        for (String payload : payloads) {
            body.append("data: ").append(payload).append("\n\n");
        }
        return body.append("data: [DONE]\n\n").toString();
    }

    private static final class Recorder implements CompletionStreamListener {
        private final List<String> content = new ArrayList<>();
        private final List<String> reasoning = new ArrayList<>();

        @Override
        public void onContentDelta(String delta) {
            content.add(delta);
        }

        @Override
        public void onReasoningDelta(String delta) {
            reasoning.add(delta);
        }
    }
}
