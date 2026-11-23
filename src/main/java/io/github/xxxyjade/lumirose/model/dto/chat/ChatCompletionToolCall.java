package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 模型返回的工具调用。
 */
public final class ChatCompletionToolCall {
    private final String id;
    private final String type;
    private final Function function;

    /**
     * 创建工具调用。
     *
     * @param id       调用 id
     * @param type     调用类型，空值默认 {@code function}
     * @param function 函数调用内容
     */
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

    /**
     * 函数名和原始 JSON 参数。
     */
    public static final class Function {
        private final String name;
        private final String arguments;

        /**
         * 创建函数调用内容。
         *
         * @param name      函数名
         * @param arguments 原始 JSON 参数字符串
         */
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
