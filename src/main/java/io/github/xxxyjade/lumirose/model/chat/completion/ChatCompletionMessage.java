package io.github.xxxyjade.lumirose.model.chat.completion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;
import java.util.Objects;

@JsonDeserialize(builder = ChatCompletionMessage.Builder.class)
public class ChatCompletionMessage {

    private final String role;
    private final String content;
    private final String reasoningContent;
    private final List<ChatCompletionToolCall> toolCalls;

    private ChatCompletionMessage(Builder builder) {
        this.role = builder.role;
        this.content = builder.content;
        this.reasoningContent = builder.reasoningContent;
        this.toolCalls = builder.toolCalls;
    }

    public String role() {
        return this.role;
    }

    public String content() {
        return this.content;
    }

    public String reasoningContent() {
        return this.reasoningContent;
    }

    public List<ChatCompletionToolCall> toolCalls() {
        return this.toolCalls;
    }

    @Override
    public String toString() {
        return "ChatCompletionMessage{" +
                "role='" + role + '\'' +
                ", content='" + content + '\'' +
                ", reasoningContent='" + reasoningContent + '\'' +
                ", toolCalls='" + toolCalls + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String role;
        private String content;
        @JsonProperty("reasoning_content") private String reasoningContent;
        @JsonProperty("tool_calls") private List<ChatCompletionToolCall> toolCalls;

        public Builder role(String role) {
            this.role = role;
            return this;
        }

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

        public ChatCompletionMessage build() {
            Objects.requireNonNull(this.role, "role");
            Objects.requireNonNull(this.content, "content");
            return new ChatCompletionMessage(this);
        }
    }
}