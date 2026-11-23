package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class ChatCompletionChoice {
    private final int index;
    private final ChatCompletionMessage message;
    private final String finishReason;

    public ChatCompletionChoice(int index, ChatCompletionMessage message, String finishReason) {
        this.index = index;
        this.message = message;
        this.finishReason = finishReason;
    }

    @JsonProperty("index")
    public int index() {
        return index;
    }

    @JsonProperty("message")
    public ChatCompletionMessage message() {
        return message;
    }

    @JsonProperty("finish_reason")
    public String finishReason() {
        return finishReason;
    }
}
