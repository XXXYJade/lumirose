package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 请求中的工具定义。
 *
 * <p>只支持 {@code function} 类型；{@code parameters} 是 JSON Schema 节点。
 */
public final class ChatCompletionTool {
    private final String type;
    private final Function function;

    /**
     * 创建工具定义。
     *
     * @param type     工具类型，空值默认 {@code function}
     * @param function 函数定义
     */
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

    /**
     * 工具的函数名、描述和参数 Schema。
     */
    public static final class Function {
        private final String name;
        private final String description;
        private final JsonNode parameters;

        /**
         * 创建函数定义。
         *
         * @param name        函数名
         * @param description 函数描述
         * @param parameters  JSON Schema 参数定义
         */
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
