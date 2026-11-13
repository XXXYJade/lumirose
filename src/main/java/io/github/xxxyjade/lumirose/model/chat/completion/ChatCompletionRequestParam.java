package io.github.xxxyjade.lumirose.model.chat.completion;

import java.util.List;
import java.util.Objects;

public class ChatCompletionRequestParam {

    private final List<ChatCompletionMessageParam> messages;
    private final List<ChatCompletionToolParam> tools;

    private ChatCompletionRequestParam(Builder builder) {
        this.messages = builder.messages;
        this.tools = builder.tools;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ChatCompletionMessageParam> messages() {
        return this.messages;
    }

    public List<ChatCompletionToolParam> tools() {
        return this.tools;
    }

    public static final class Builder {
        private List<ChatCompletionMessageParam> messages;
        private List<ChatCompletionToolParam> tools;

        public Builder messages(List<ChatCompletionMessageParam> messages) {
            this.messages = messages;
            return this;
        }

        public Builder tools(List<ChatCompletionToolParam> tools) {
            this.tools = tools;
            return this;
        }

        public ChatCompletionRequestParam build() {
            messages = Objects.requireNonNullElse(messages, List.of());
            tools = Objects.requireNonNullElse(tools, List.of());
            return new ChatCompletionRequestParam(this);
        }
    }
}
