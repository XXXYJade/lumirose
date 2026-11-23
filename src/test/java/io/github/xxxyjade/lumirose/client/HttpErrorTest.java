package io.github.xxxyjade.lumirose.client;

import io.github.xxxyjade.lumirose.common.exception.ClientException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionMessage;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionRequest;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP 状态、错误体脱敏与网络故障映射测试。
 */
class HttpErrorTest {

    private static final String API_KEY = "sk-test-secret";

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
    void mapsHttpStatusToClientHttpError() {
        for (int status : List.of(400, 401, 403, 404, 429, 500)) {
            server.respondJson(status, "{\"error\":{\"message\":\"provider detail\","
                    + "\"code\":\"some_code\",\"type\":\"some_type\"}}");

            ClientException error = assertThrows(ClientException.class,
                    () -> client().chatCompletion(request()));

            assertEquals(LumiRoseErrorCode.CLIENT_HTTP_ERROR, error.getErrorCode());
            assertEquals(status, error.httpStatus().intValue());
            assertTrue(error.getMessage().contains("HTTP " + status));
            assertTrue(error.getMessage().contains("code=some_code"));
            assertTrue(error.getMessage().contains("type=some_type"));
            assertFalse(error.getMessage().contains("provider detail"));
            assertEquals(status == 429 || status >= 500, error.retriable());
        }
    }

    @Test
    void handlesUnparseableErrorBodyWithStatusOnly() {
        server.respondJson(500, "<html>not json</html>");

        ClientException error = assertThrows(ClientException.class,
                () -> client().chatCompletion(request()));

        assertEquals(LumiRoseErrorCode.CLIENT_HTTP_ERROR, error.getErrorCode());
        assertEquals(500, error.httpStatus().intValue());
        assertEquals("模型请求失败: HTTP 500", error.getMessage());
        assertFalse(error.getMessage().contains("html"));
        assertNull(error.getCause());
        assertTrue(error.retriable());
    }

    @Test
    void keepsContextLength400AsHttpError() {
        server.respondJson(400, "{\"error\":{\"message\":\"too long\","
                + "\"code\":\"context_length_exceeded\",\"type\":\"invalid_request_error\"}}");

        ClientException error = assertThrows(ClientException.class,
                () -> client().chatCompletion(request()));

        assertEquals(LumiRoseErrorCode.CLIENT_HTTP_ERROR, error.getErrorCode());
        assertEquals(400, error.httpStatus().intValue());
        assertTrue(error.getMessage().contains("code=context_length_exceeded"));
        assertFalse(error.retriable());
    }

    @Test
    void mapsConnectionFailureToIoError() {
        server.abort();

        ClientException error = assertThrows(ClientException.class,
                () -> client().chatCompletion(request()));

        assertEquals(LumiRoseErrorCode.CLIENT_IO_ERROR, error.getErrorCode());
        assertTrue(error.getCause() instanceof IOException);
        assertTrue(error.retriable());
    }

    @Test
    void neverLeaksApiKeyOrProviderMessage() {
        server.respondJson(401, "{\"error\":{\"message\":\"invalid key\","
                + "\"code\":\"invalid_api_key\",\"type\":\"authentication_error\"}}");
        ClientException httpError = assertThrows(ClientException.class,
                () -> client().chatCompletion(request()));

        assertFalse(describe(httpError).contains(API_KEY));
        assertFalse(httpError.getMessage().contains("invalid key"));

        server.respondSse("data: not-json\n\n");
        ClientException protocolError = assertThrows(ClientException.class,
                () -> client().chatCompletion(request()));

        assertEquals(LumiRoseErrorCode.CLIENT_RESPONSE_INVALID, protocolError.getErrorCode());
        assertFalse(describe(protocolError).contains(API_KEY));
    }

    private LumiRoseClient client() {
        return LumiRoseClient.builder()
                .baseUrl(server.baseUrl())
                .apiKey(API_KEY)
                .model("test-model")
                .build();
    }

    private static ChatCompletionRequest request() {
        return new ChatCompletionRequest(
                List.of(ChatCompletionMessage.user("hi")), List.of(), null);
    }

    /**
     * 把异常链和堆栈渲染成文本，用于检查敏感信息是否泄漏。
     */
    private static String describe(Throwable error) {
        StringBuilder text = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            text.append(current).append('\n');
            for (StackTraceElement element : current.getStackTrace()) {
                text.append(element).append('\n');
            }
            current = current.getCause();
        }
        return text.toString();
    }
}
