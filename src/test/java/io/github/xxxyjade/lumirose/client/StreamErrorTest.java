package io.github.xxxyjade.lumirose.client;

import io.github.xxxyjade.lumirose.exception.ClientException;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionRequestParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionUserMessageParam;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamErrorTest {

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
    void tc_se01_httpErrorMapsTo2001() {
        server.enqueue(500, "{\"error\":{\"code\":\"server_error\",\"type\":\"api_error\"}}");

        var ex = assertThrows(ClientException.class, () -> call());

        assertEquals(2001, ex.getErrorCode().code());
        assertEquals(500, ex.httpStatus());
        assertTrue(ex.getMessage().contains("HTTP 500"));
        assertTrue(ex.getMessage().contains("code=server_error"));
        assertEquals(1, server.requestCount());
    }

    @Test
    void tc_se02_truncatedStreamMapsTo2003() {
        String body = "data: " + "{\"id\":\"c1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"a\"},"
                + "\"finish_reason\":null}]}" + "\n\n";
        server.enqueueTruncatedSse(body, 4096);

        var ex = assertThrows(ClientException.class, () -> call());

        assertEquals(2003, ex.getErrorCode().code());
        assertNotNull(ex.getCause());
        assertTrue(ex.getCause() instanceof IOException);
    }

    @Test
    void tc_se03_malformedPayloadMapsTo2002WithoutCause() {
        server.enqueueSse("data: {oops\n\ndata: [DONE]\n\n");

        var ex = assertThrows(ClientException.class, () -> call());

        assertEquals(2002, ex.getErrorCode().code());
        assertEquals("流式响应结构非法", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void tc_se04_doneWithoutFinishReasonMapsTo2002() {
        server.enqueueSse("data: [DONE]\n\n");

        var ex = assertThrows(ClientException.class, () -> call());

        assertEquals(2002, ex.getErrorCode().code());
    }

    @Test
    void tc_se05_conflictingToolCallIdMapsTo2002() {
        server.enqueueSse(sse(
                "{\"id\":\"c1\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"t\","
                        + "\"arguments\":\"\"}}]},\"finish_reason\":null}]}",
                "{\"id\":\"c1\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"id\":\"call_2\"}]},\"finish_reason\":null}]}",
                "{\"id\":\"c1\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}"));

        var ex = assertThrows(ClientException.class, () -> call());

        assertEquals(2002, ex.getErrorCode().code());
    }

    @Test
    void tc_se06_emptyBodyMapsTo2002() {
        server.enqueueSse("");

        var ex = assertThrows(ClientException.class, () -> call());

        assertEquals(2002, ex.getErrorCode().code());
    }

    @Test
    void tc_se07_toolCallWithoutIndexMapsTo2002() {
        server.enqueueSse(sse(
                "{\"id\":\"c1\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{"
                        + "\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"t\","
                        + "\"arguments\":\"\"}}]},\"finish_reason\":null}]}",
                "{\"id\":\"c1\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}"));

        var ex = assertThrows(ClientException.class, () -> call());

        assertEquals(2002, ex.getErrorCode().code());
    }

    private void call() {
        client.streamChatCompletion(
                ChatCompletionRequestParam.builder()
                        .messages(List.of(ChatCompletionUserMessageParam.builder().content("hi").build()))
                        .build(),
                CompletionStreamListener.NOOP);
    }

    private static String sse(String... payloads) {
        StringBuilder body = new StringBuilder();
        for (String payload : payloads) {
            body.append("data: ").append(payload).append("\n\n");
        }
        return body.append("data: [DONE]\n\n").toString();
    }
}
