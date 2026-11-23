package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * chat completions 请求体中由 SDK 负责的字段。
 *
 * <p>{@code model}、{@code stream} 等由客户端配置补充；本类只承载
 * messages、可选 tools 和可选 max_tokens。
 */
public final class ChatCompletionRequest {
    private final List<ChatCompletionMessage> messages;
    private final List<ChatCompletionTool> tools;
    private final Integer maxTokens;

    /**
     * 创建请求 DTO。
     *
     * @param messages  消息列表，{@code null} 视为空列表
     * @param tools     工具定义列表，{@code null} 视为空列表
     * @param maxTokens 最大输出 token，{@code null} 表示不发送
     */
    public ChatCompletionRequest(List<ChatCompletionMessage> messages,
                                 List<ChatCompletionTool> tools,
                                 Integer maxTokens) {
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        this.maxTokens = maxTokens;
    }

    @JsonProperty("messages")
    public List<ChatCompletionMessage> messages() {
        return messages;
    }

    @JsonProperty("tools")
    public List<ChatCompletionTool> tools() {
        return tools;
    }

    @JsonProperty("max_tokens")
    public Integer maxTokens() {
        return maxTokens;
    }
}
