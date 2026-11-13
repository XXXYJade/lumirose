package io.github.xxxyjade.lumirose.model.chat.completion;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.xxxyjade.lumirose.exception.AgentException;
import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;

public final class ChatCompletionToolMessageParam implements ChatCompletionMessageParam {

    private final String role = "tool";
    private final String content;
    private final String toolCallId;

    private ChatCompletionToolMessageParam(Builder builder) {
        this.content = builder.content;
        this.toolCallId = builder.toolCallId;
    }

    @JsonProperty("role")
    public String role() {
        return this.role;
    }

    @JsonProperty("content")
    public String content() {
        return this.content;
    }

    @JsonProperty("tool_call_id")
    public String toolCallId() {
        return this.toolCallId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String content;
        private String toolCallId;

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public ChatCompletionToolMessageParam build() {
            if (toolCallId == null || toolCallId.isBlank()) {
                throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "tool_call_id 不能为空");
            }
            return new ChatCompletionToolMessageParam(this);
        }
    }
}