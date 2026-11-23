package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class ChatCompletionResponse {
    private final List<ChatCompletionChoice> choices;
    private final ChatCompletionUsage usage;

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
