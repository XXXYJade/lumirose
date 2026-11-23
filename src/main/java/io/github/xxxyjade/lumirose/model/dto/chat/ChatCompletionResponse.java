package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * chat completions 响应。
 *
 * <p>客户端只取 {@code choices[0]}；{@code usage} 可能为 {@code null}。
 */
public final class ChatCompletionResponse {
    private final List<ChatCompletionChoice> choices;
    private final ChatCompletionUsage usage;

    /**
     * 创建响应 DTO。
     *
     * @param choices 选择列表，{@code null} 视为空列表
     * @param usage   token 用量，可为 {@code null}
     */
    public ChatCompletionResponse(List<ChatCompletionChoice> choices, ChatCompletionUsage usage) {
        this.choices = choices == null ? List.of() : List.copyOf(choices);
        this.usage = usage;
    }

    @JsonProperty("choices")
    public List<ChatCompletionChoice> choices() {
        return choices;
    }

    @JsonProperty("usage")
    public ChatCompletionUsage usage() {
        return usage;
    }
}
