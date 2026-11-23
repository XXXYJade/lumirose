package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 响应中的 token 用量。
 *
 * <p>字段可为 {@code null}；调用方按 0 处理缺失值。
 */
public final class ChatCompletionUsage {
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;

    /**
     * 创建用量 DTO。
     *
     * @param promptTokens     prompt token 数
     * @param completionTokens completion token 数
     * @param totalTokens      总 token 数
     */
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
