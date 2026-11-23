package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class ChatCompletionToolCall {
    private final String id;
    private final String type;
    private final Function function;

    public ChatCompletionToolCall(String id, String type, Function function) {
        this.id = id;
        this.type = type == null || type.isBlank() ? "function" : type;
        this.function = function;
    }

    @JsonProperty("id")
    public String id() {
        return id;
    }

    @JsonProperty("type")
    public String type() {
        return type;
    }

    @JsonProperty("function")
    public Function function() {
        return function;
    }

    public static final class Function {
        private final String name;
        private final String arguments;

        public Function(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        @JsonProperty("name")
        public String name() {
            return name;
        }

        @JsonProperty("arguments")
        public String arguments() {
            return arguments;
        }
    }
}
