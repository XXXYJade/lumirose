package io.github.xxxyjade.lumirose.client;

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
        this.objectMapper = new ObjectMapper();
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
