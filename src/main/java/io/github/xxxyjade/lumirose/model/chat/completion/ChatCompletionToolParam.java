package io.github.xxxyjade.lumirose.model.chat.completion;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.xxxyjade.lumirose.tool.Tool;

public class ChatCompletionToolParam {

    private final String type = "function";

    private final Tool function;

    private ChatCompletionToolParam(Builder builder) {
        this.function = builder.function;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty("type")
    public String getType() {
        return this.type;
    }

    @JsonProperty("function")
    public Tool function() {
        return this.function;
    }

    public static final class Builder {
        private Tool function;

        public Builder function(Tool function) {
            this.function = function;
            return this;
        }

        public ChatCompletionToolParam build() {
            return new ChatCompletionToolParam(this);
        }
    }
}
