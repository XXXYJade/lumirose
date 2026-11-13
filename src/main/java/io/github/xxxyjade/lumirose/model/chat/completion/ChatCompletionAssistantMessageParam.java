package io.github.xxxyjade.lumirose.model.chat.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@JsonDeserialize(builder = ChatCompletionAssistantMessageParam.Builder.class)
public final class ChatCompletionAssistantMessageParam implements ChatCompletionMessageParam {

    private final String role = "assistant";
    private final String content;
    private final String reasoningContent;
    private final List<ChatCompletionToolCall> toolCalls;

    private ChatCompletionAssistantMessageParam(Builder builder) {
        this.content = builder.content;
        this.reasoningContent = builder.reasoningContent;
        this.toolCalls = builder.toolCalls;
    }

    @JsonProperty("role")
    public String role() {
        return this.role;
    }

    @JsonProperty("content")
    public String content() {
        return this.content;
    }

    @JsonInclude(NON_EMPTY)
    @JsonProperty("reasoning_content")
    public String reasoningContent() {
        return this.reasoningContent;
    }

    @JsonInclude(NON_EMPTY)
    @JsonProperty("tool_calls")
    public List<ChatCompletionToolCall> toolCalls() {
        return this.toolCalls;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String content;
        private String reasoningContent;
        private List<ChatCompletionToolCall> toolCalls;

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder reasoningContent(String reasoningContent) {
            this.reasoningContent = reasoningContent;
            return this;
        }

        public Builder toolCalls(List<ChatCompletionToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public ChatCompletionAssistantMessageParam build() {
            return new ChatCompletionAssistantMessageParam(this);
        }
    }
}