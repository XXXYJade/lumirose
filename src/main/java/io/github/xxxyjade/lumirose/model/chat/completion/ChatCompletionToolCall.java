package io.github.xxxyjade.lumirose.model.chat.completion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = ChatCompletionToolCall.Builder.class)
public class ChatCompletionToolCall {
    private final String id;
    private final String type;
    private final Function function;

    private ChatCompletionToolCall(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.function = builder.function;
    }

    @JsonProperty("id")
    public String id() {
        return this.id;
    }

    @JsonProperty("type")
    public String type() {
        return this.type;
    }

    @JsonProperty("function")
    public Function function() {
        return this.function;
    }

    @Override
    public String toString() {
        return "ToolCall{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", function='" + function + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String id;
        private String type;
        private Function function;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder function(Function function) {
            this.function = function;
            return this;
        }

        public ChatCompletionToolCall build() {
            return new ChatCompletionToolCall(this);
        }
    }

    @JsonDeserialize(builder = ChatCompletionToolCall.Function.Builder.class)
    public static class Function {
        private final String name;
        private final String arguments;

        private Function(Builder builder) {
            this.name = builder.name;
            this.arguments = builder.arguments;
        }

        @JsonProperty("name")
        public String name() {
            return this.name;
        }

        @JsonProperty("arguments")
        public String arguments() {
            return this.arguments;
        }

        @Override
        public String toString() {
            return "Function{" +
                    "name='" + name + '\'' +
                    ", arguments='" + arguments + '\'' +
                    '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            private String name;
            private String arguments;

            public Function.Builder name(String name) {
                this.name = name;
                return this;
            }

            public Function.Builder arguments(String arguments) {
                this.arguments = arguments;
                return this;
            }

            public Function build() {
                return new Function(this);
            }
        }
    }
}
