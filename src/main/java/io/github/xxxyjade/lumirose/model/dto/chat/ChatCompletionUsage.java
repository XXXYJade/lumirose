package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class ChatCompletionUsage {
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;

    public ChatCompletionUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    @JsonProperty("prompt_tokens")
    public Integer promptTokens() {
        return promptTokens;
    }

    @JsonProperty("completion_tokens")
    public Integer completionTokens() {
        return completionTokens;
    }

    @JsonProperty("total_tokens")
    public Integer totalTokens() {
        return totalTokens;
    }
}
