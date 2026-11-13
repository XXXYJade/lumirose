package io.github.xxxyjade.lumirose.model.chat.completion;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class ChatCompletionUserMessageParam implements ChatCompletionMessageParam {

    private final String role = "user";
    private final String content;

    private ChatCompletionUserMessageParam(Builder builder) {
        this.content = builder.content;
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

        public ChatCompletionUserMessageParam build() {
            return new ChatCompletionUserMessageParam(this);
        }

    }
}
