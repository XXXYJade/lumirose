package io.github.xxxyjade.lumirose.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionRequestParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionResponseParam;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

public class LumiRoseClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ChatCompletionResponseParam chatCompletion(ChatCompletionRequestParam requestParam) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.set("messages", objectMapper.valueToTree(requestParam.messages()));
        body.set("tools", objectMapper.valueToTree(requestParam.tools()));

        // TODO 请求参数校验

        MediaType json = MediaType.get("application/json; charset=utf-8");
        HttpUrl url = HttpUrl.get( baseUrl + "/chat/completions");

        Request httpRequest = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .post(RequestBody.create(body.toString(), json))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            // TODO 异常结果判断和处理
            ChatCompletionResponseParam responseParam = objectMapper.readValue(response.body().string(), ChatCompletionResponseParam.class);
            return responseParam;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private LumiRoseClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.apiKey = builder.apiKey;
        this.model = builder.model;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .callTimeout(Duration.ofSeconds(180))
                .retryOnConnectionFailure(false)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String baseUrl;
        private String apiKey;
        private String model;

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

        public LumiRoseClient build() {
            Objects.requireNonNull(this.baseUrl, "Base URL is required");
            Objects.requireNonNull(this.apiKey, "API Key is required");
            Objects.requireNonNull(this.model, "Model is required");
            return new LumiRoseClient(this);
        }
    }
}
