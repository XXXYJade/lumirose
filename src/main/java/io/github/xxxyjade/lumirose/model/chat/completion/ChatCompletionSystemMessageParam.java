package io.github.xxxyjade.lumirose.model.chat.completion;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class ChatCompletionSystemMessageParam implements ChatCompletionMessageParam {

    private final String role = "system";
    private final String content;

    private ChatCompletionSystemMessageParam(Builder builder) {
        content = builder.content;
    }

    @JsonProperty("role")
    public String role() {
        return this.role;
    }

    @JsonProperty("content")
    public String content() {
        return this.content;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String content;

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public ChatCompletionSystemMessageParam build() {
            Objects.requireNonNull(content, "content is required");
            return new ChatCompletionSystemMessageParam(this);
        }
    }
}