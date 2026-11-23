package io.github.xxxyjade.lumirose.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.xxxyjade.lumirose.common.exception.ClientException;
import io.github.xxxyjade.lumirose.common.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionRequest;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionResponse;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * OpenAI 兼容的流式模型客户端。
 *
 * <p>只支持 {@code /chat/completions} 的 SSE 路径：请求体固定带
 * {@code stream: true}，响应由 {@link SseAccumulator} 装配成完整对象。
 * 不提供非流式回退。
 *
 * <p>错误映射：非 2xx 抛 {@link LumiRoseErrorCode#CLIENT_HTTP_ERROR} 并保留
 * 状态码；网络与读取失败抛 {@link LumiRoseErrorCode#CLIENT_IO_ERROR} 并保留
 * cause；流结构问题抛 {@link LumiRoseErrorCode#CLIENT_RESPONSE_INVALID}。
 * HTTP 错误消息只包含状态码和供应商的 {@code code}/{@code type}，不包含
 * 供应商原始 message、响应体或 API Key。
 *
 * <p>实例不可变，可安全共享；内部 {@link OkHttpClient} 自带连接池。
 */
public final class LumiRoseClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Integer maxTokens;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private LumiRoseClient(Builder builder) {
        String base = requireText(builder.baseUrl, "baseUrl");
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            throw configuration("配置项 baseUrl 必须以 http:// 或 https:// 开头");
        }
        // 允许调用方以任意数量的斜杠结尾，统一去掉后再拼接路径。
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.baseUrl = base;
        this.apiKey = requireText(builder.apiKey, "apiKey");
        this.model = requireText(builder.model, "model");
        if (builder.maxTokens != null && builder.maxTokens <= 0) {
            throw configuration("配置项 maxTokens 必须大于 0");
        }
        this.maxTokens = builder.maxTokens;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                // 重试由上层 Agent 决定，HTTP 层不隐式重放请求。
                .retryOnConnectionFailure(false)
                .build();
    }

    /**
     * 发起流式补全，不观察正文增量。
     *
     * @param request 请求内容
     * @return 装配后的响应
     */
    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
        return chatCompletion(request, ignored -> { });
    }

    /**
     * 发起流式补全，并在每段非空正文到达时回调。
     *
     * @param request   请求内容
     * @param onContent 正文增量回调，可为 {@code null}
     * @return 装配后的响应
     * @throws ClientException HTTP、网络或协议错误
     */
    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request,
                                                 Consumer<String> onContent) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.set("messages", mapper.valueToTree(request.messages()));
        if (!request.tools().isEmpty()) {
            // 空工具列表不发送，避免部分供应商把它当成非法字段。
            body.set("tools", mapper.valueToTree(request.tools()));
        }
        body.put("stream", true);
        Integer limit = request.maxTokens() != null ? request.maxTokens() : maxTokens;
        if (limit != null) {
            body.put("max_tokens", limit);
        }

        Request httpRequest = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw httpError(response);
            }
            if (response.body() == null) {
                throw new ClientException(LumiRoseErrorCode.CLIENT_RESPONSE_INVALID, "流式响应为空");
            }
            return SseAccumulator.consume(response.body().source(), onContent);
        } catch (IOException e) {
            throw new ClientException(LumiRoseErrorCode.CLIENT_IO_ERROR, "网络请求失败", e);
        }
    }

    private ClientException httpError(Response response) {
        int status = response.code();
        String code = null;
        String type = null;
        try {
            String body = response.body() == null ? "" : response.body().string();
            JsonNode error = mapper.readTree(body).path("error");
            code = textOrNull(error, "code");
            type = textOrNull(error, "type");
        } catch (IOException | RuntimeException ignored) {
            // 错误体不可解析时只保留状态码，绝不把原始 body 带进异常。
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
            throw configuration("配置项 " + field + " 不能为空");
        }
        return value;
    }

    private static ConfigurationException configuration(String message) {
        return new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, message);
    }

    /**
     * 创建客户端构建器。
     *
     * @return 新的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 客户端配置构建器；校验发生在 {@link #build()}。
     */
    public static final class Builder {
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer maxTokens;

        private Builder() {
        }

        /**
         * 设置 API 根地址，末尾斜杠会被去掉。
         *
         * @param baseUrl 以 {@code http://} 或 {@code https://} 开头的地址
         * @return 当前构建器
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * 设置 API Key，只用于 {@code Authorization} 头。
         *
         * @param apiKey 非空密钥
         * @return 当前构建器
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * 设置默认模型名。
         *
         * @param model 模型标识
         * @return 当前构建器
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * 设置默认最大输出 token 数；请求级设置优先。
         *
         * @param maxTokens 正整数，或 {@code null} 表示不发送
         * @return 当前构建器
         */
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * 校验并创建客户端。
         *
         * @return 客户端实例
         * @throws ConfigurationException 必填项缺失或取值非法
         */
        public LumiRoseClient build() {
            return new LumiRoseClient(this);
        }
    }
}
