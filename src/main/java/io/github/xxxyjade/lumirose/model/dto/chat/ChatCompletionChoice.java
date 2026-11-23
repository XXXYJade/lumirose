package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 响应中的一个 choice。
 */
public final class ChatCompletionChoice {
    private final int index;
    private final ChatCompletionMessage message;
    private final String finishReason;

    /**
     * 创建 choice。
     *
     * @param index        选择下标
     * @param message      assistant 消息
     * @param finishReason 结束原因，可为 {@code null}
     */
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
