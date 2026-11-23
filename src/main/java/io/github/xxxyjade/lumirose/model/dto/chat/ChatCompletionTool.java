package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public final class ChatCompletionTool {
    private final String type;
    private final Function function;

    public ChatCompletionTool(String type, Function function) {
        this.type = type == null || type.isBlank() ? "function" : type;
        this.function = function;
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
        private final String description;
        private final JsonNode parameters;

        public Function(String name, String description, JsonNode parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }

        @JsonProperty("name")
        public String name() {
            return name;
        }

        @JsonProperty("description")
        public String description() {
            return description;
        }

        @JsonProperty("parameters")
        public JsonNode parameters() {
            return parameters;
        }
    }
}
