package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class ChatCompletionRequest {
    private final List<ChatCompletionMessage> messages;
    private final List<ChatCompletionTool> tools;
    private final Integer maxTokens;

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
