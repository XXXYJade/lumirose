package io.github.xxxyjade.lumirose.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.xxxyjade.lumirose.exception.ClientException;
import io.github.xxxyjade.lumirose.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionRequestParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionResponseParam;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

import java.io.IOException;
import java.time.Duration;

/**
 * DeepSeek Chat Completions 同步客户端。
 */
public final class LumiRoseClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Integer maxTokens;
    private final OkHttpClient httpClient;
    private final OkHttpClient streamingHttpClient;
    private final ObjectMapper objectMapper;

    public ChatCompletionResponseParam chatCompletion(ChatCompletionRequestParam requestParam) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.set("messages", objectMapper.valueToTree(requestParam.messages()));
        if (!requestParam.tools().isEmpty()) {
            body.set("tools", objectMapper.valueToTree(requestParam.tools()));
        }
        if (maxTokens != null) {
            body.put("max_tokens", maxTokens);
        }

        MediaType json = MediaType.get("application/json; charset=utf-8");
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .post(RequestBody.create(body.toString(), json))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw httpError(response);
            }

            String text = response.body() == null ? "" : response.body().string();

            ChatCompletionResponseParam parsed;
            try {
                parsed = objectMapper.readValue(text, ChatCompletionResponseParam.class);
            } catch (IOException e) {
                throw new ClientException(LumiRoseErrorCode.CLIENT_RESPONSE_INVALID, "模型响应结构非法");
            }

            if (parsed.choices() == null || parsed.choices().isEmpty()
                    || parsed.choices().get(0).message() == null) {
                throw new ClientException(LumiRoseErrorCode.CLIENT_RESPONSE_INVALID, "模型响应结构非法");
            }
            return parsed;
        } catch (IOException e) {
            throw new ClientException(LumiRoseErrorCode.CLIENT_IO_ERROR, "模型请求失败", e);
        }
    }

    /**
     * 以 SSE 方式请求模型，边接收边回调增量，返回装配后的完整响应。
     */
    public ChatCompletionResponseParam streamChatCompletion(ChatCompletionRequestParam requestParam,
                                                            CompletionStreamListener listener) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.set("messages", objectMapper.valueToTree(requestParam.messages()));
        if (!requestParam.tools().isEmpty()) {
            body.set("tools", objectMapper.valueToTree(requestParam.tools()));
        }
        if (maxTokens != null) {
            body.put("max_tokens", maxTokens);
        }
        body.put("stream", true);

        MediaType json = MediaType.get("application/json; charset=utf-8");
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(body.toString(), json))
                .build();

        try (Response response = streamingHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw httpError(response);
            }
            if (response.body() == null) {
                throw new ClientException(LumiRoseErrorCode.CLIENT_RESPONSE_INVALID, "流式响应结构非法");
            }
            try (BufferedSource source = response.body().source()) {
                return SseAssembler.consume(
                        source, listener == null ? CompletionStreamListener.NOOP : listener);
            }
        } catch (IOException e) {
            throw new ClientException(LumiRoseErrorCode.CLIENT_IO_ERROR, "模型请求失败", e);
        }
    }

    private LumiRoseClient(Builder builder) {
        String normalizedBaseUrl = requireText(builder.baseUrl, "baseUrl");
        if (!normalizedBaseUrl.startsWith("http://") && !normalizedBaseUrl.startsWith("https://")) {
            throw configurationError("配置项 baseUrl 必须以 http:// 或 https:// 开头");
        }
        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        this.baseUrl = normalizedBaseUrl;
        this.apiKey = requireText(builder.apiKey, "apiKey");
        this.model = requireText(builder.model, "model");

        if (builder.maxTokens != null && builder.maxTokens <= 0) {
            throw configurationError("配置项 maxTokens 必须大于 0");
        }
        this.maxTokens = builder.maxTokens;

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .callTimeout(Duration.ofSeconds(180))
                .retryOnConnectionFailure(false)
                .build();
        this.streamingHttpClient = this.httpClient.newBuilder()
                .callTimeout(Duration.ZERO)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private ClientException httpError(Response response) {
        int status = response.code();
        String code = null;
        String type = null;

        try {
            String errorBody = response.body() == null ? "" : response.body().string();
            JsonNode error = objectMapper.readTree(errorBody).path("error");
            code = textOrNull(error, "code");
            type = textOrNull(error, "type");
        } catch (IOException | RuntimeException ignored) {
            // 错误体无法解析时，异常里只保留状态码
        }

        StringBuilder message = new StringBuilder("模型请求失败: HTTP ").append(status);
        if (code != null || type != null) {
            message.append(" (");
            if (code != null) {
                message.append("code=").append(code);
            }
            if (code != null && type != null) {
                message.append(", ");
            }
            if (type != null) {
                message.append("type=").append(type);
            }
            message.append(")");
        }

        return new ClientException(LumiRoseErrorCode.CLIENT_HTTP_ERROR, status, message.toString());
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw configurationError("配置项 " + field + " 不能为空");
        }
        return value;
    }

    private static ConfigurationException configurationError(String message) {
        return new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, message);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer maxTokens;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public LumiRoseClient build() {
            return new LumiRoseClient(this);
        }
    }
}
