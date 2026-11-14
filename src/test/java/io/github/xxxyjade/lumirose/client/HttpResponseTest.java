package io.github.xxxyjade.lumirose.client;

import io.github.xxxyjade.lumirose.exception.ClientException;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessageParam;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpResponseTest {

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
    void tc_h01_httpErrorKeepsCodeTypeAndStatus() {
        server.enqueue(401, "{\"error\":{\"message\":\"Authentication Fails\","
                + "\"type\":\"authentication_error\",\"code\":\"invalid_api_key\"}}");

        var ex = assertThrows(ClientException.class, () -> client.chatCompletion(request()));

        assertEquals(2001, ex.getErrorCode().code());
        assertEquals(401, ex.httpStatus());
        assertFalse(ex.retriable());
        assertTrue(ex.getMessage().contains("HTTP 401"));
        assertTrue(ex.getMessage().contains("code=invalid_api_key"));
        assertTrue(ex.getMessage().contains("type=authentication_error"));
        assertFalse(ex.getMessage().contains("Authentication Fails"));
    }

    @Test
    void tc_h02_nonJsonErrorBodyKeepsStatusOnly() {
        server.enqueue(500, "oops");

        var ex = assertThrows(ClientException.class, () -> client.chatCompletion(request()));

        assertEquals(2001, ex.getErrorCode().code());
        assertEquals("模型请求失败: HTTP 500", ex.getMessage());
    }

    @Test
    void tc_h03_rateLimitIsRetriableButNotRetried() {
        server.enqueue(429, "{}");

        var ex = assertThrows(ClientException.class, () -> client.chatCompletion(request()));

        assertEquals(2001, ex.getErrorCode().code());
        assertTrue(ex.retriable());
        assertEquals(1, server.requests().size());
    }

    @Test
    void tc_h04_emptyBodyRejected() {
        server.enqueue(200, "");

        var ex = assertThrows(ClientException.class, () -> client.chatCompletion(request()));

        assertEquals(2002, ex.getErrorCode().code());
    }

    @Test
    void tc_h05_malformedBodyHasNoCause() {
        server.enqueue(200, "not json");

        var ex = assertThrows(ClientException.class, () -> client.chatCompletion(request()));

        assertEquals(2002, ex.getErrorCode().code());
        assertNull(ex.getCause());
    }

    @Test
    void tc_h06_emptyChoicesRejected() {
        server.enqueue(200, "{\"choices\":[]}");

        var ex = assertThrows(ClientException.class, () -> client.chatCompletion(request()));

        assertEquals(2002, ex.getErrorCode().code());
    }

    @Test
    void tc_h07_missingMessageRejected() {
        server.enqueue(200, "{\"choices\":[{\"index\":0}]}");

        var ex = assertThrows(ClientException.class, () -> client.chatCompletion(request()));

        assertEquals(2002, ex.getErrorCode().code());
    }

    @Test
    void tc_h08_connectionFailureIsIoError() throws Exception {
        FakeServer dead = new FakeServer();
        String baseUrl = dead.baseUrl();
        dead.close();

        LumiRoseClient failing = LumiRoseClient.builder()
                .baseUrl(baseUrl)
                .apiKey("k")
                .model("m")
                .build();

        var ex = assertThrows(ClientException.class, () -> failing.chatCompletion(request()));

        assertEquals(2003, ex.getErrorCode().code());
        assertInstanceOf(IOException.class, ex.getCause());
        assertTrue(ex.retriable());
    }

    private ChatCompletionRequestParam request() {
        return ChatCompletionRequestParam.builder()
                .messages(List.of(user("hi")))
                .build();
    }

    private ChatCompletionMessageParam user(String content) {
        return ChatCompletionUserMessageParam.builder().content(content).build();
    }
}
