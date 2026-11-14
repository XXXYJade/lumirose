package io.github.xxxyjade.lumirose.client;

import io.github.xxxyjade.lumirose.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionRequestParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionUserMessageParam;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigTest {

    @Test
    void tc_c01_apiKeyMissingRejected() {
        var ex = assertThrows(ConfigurationException.class, () -> LumiRoseClient.builder()
                .baseUrl("http://127.0.0.1:1")
                .model("deepseek-chat")
                .build());

        assertEquals(1001, ex.getErrorCode().code());
        assertTrue(ex.getMessage().contains("apiKey"));
    }

    @Test
    void tc_c02_apiKeyBlankRejected() {
        var ex = assertThrows(ConfigurationException.class, () -> LumiRoseClient.builder()
                .baseUrl("http://127.0.0.1:1")
                .apiKey("  ")
                .model("deepseek-chat")
                .build());

        assertEquals(1001, ex.getErrorCode().code());
    }

    @Test
    void tc_c03_baseUrlMissingRejected() {
        assertThrows(ConfigurationException.class, () -> LumiRoseClient.builder()
                .apiKey("k").model("m").build());
        assertThrows(ConfigurationException.class, () -> LumiRoseClient.builder()
                .baseUrl("  ").apiKey("k").model("m").build());
    }

    @Test
    void tc_c04_baseUrlSchemeRejected() {
        var ex = assertThrows(ConfigurationException.class, () -> LumiRoseClient.builder()
                .baseUrl("ftp://host").apiKey("k").model("m").build());

        assertEquals(1001, ex.getErrorCode().code());
    }

    @Test
    void tc_c05_modelMissingRejected() {
        assertThrows(ConfigurationException.class, () -> LumiRoseClient.builder()
                .baseUrl("http://127.0.0.1:1").apiKey("k").build());
        assertThrows(ConfigurationException.class, () -> LumiRoseClient.builder()
                .baseUrl("http://127.0.0.1:1").apiKey("k").model("  ").build());
    }

    @Test
    void tc_c06_maxTokensNonPositiveRejected() {
        assertThrows(ConfigurationException.class, () -> LumiRoseClient.builder()
                .baseUrl("http://127.0.0.1:1").apiKey("k").model("m").maxTokens(0).build());
        assertThrows(ConfigurationException.class, () -> LumiRoseClient.builder()
                .baseUrl("http://127.0.0.1:1").apiKey("k").model("m").maxTokens(-1).build());
    }

    @Test
    void tc_c07_trailingSlashNormalized() throws Exception {
        try (FakeServer server = new FakeServer()) {
            server.enqueue(200, stopResponse());
            LumiRoseClient client = LumiRoseClient.builder()
                    .baseUrl(server.baseUrl() + "/")
                    .apiKey("k")
                    .model("m")
                    .build();

            client.chatCompletion(request());

            assertEquals("/chat/completions", server.lastRequest().path());
        }
    }

    @Test
    void tc_c08_apiKeyNotLeakedInMessage() {
        var ex = assertThrows(ConfigurationException.class, () -> LumiRoseClient.builder()
                .baseUrl("ftp://host").apiKey("sk-test-secret").model("m").build());

        assertFalse(ex.getMessage().contains("sk-test-secret"));
    }

    private ChatCompletionRequestParam request() {
        return ChatCompletionRequestParam.builder()
                .messages(List.of(user("hi")))
                .build();
    }

    private ChatCompletionMessageParam user(String content) {
        return ChatCompletionUserMessageParam.builder().content(content).build();
    }

    private String stopResponse() {
        return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
    }
}
