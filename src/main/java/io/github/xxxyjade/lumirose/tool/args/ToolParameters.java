package io.github.xxxyjade.lumirose.tool.args;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具参数标记接口，负责导出 JSON Schema。
 */
public interface ToolParameters {

    @JsonValue
    default JsonNode toSchema() {
        return ToolSchemaFactory.generate(getClass());
    }
}
