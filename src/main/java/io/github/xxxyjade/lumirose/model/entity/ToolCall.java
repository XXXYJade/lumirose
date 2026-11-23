package io.github.xxxyjade.lumirose.model.entity;

import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;

/**
 * 一次工具调用。
 *
 * <p>{@code arguments} 保留模型返回的原始 JSON 字符串，不在实体层解析；
 * 解析和校验由工具执行层负责。
 */
public final class ToolCall {
    private final String id;
    private final String type;
    private final String name;
    private final String arguments;

    /**
     * 创建工具调用。
     *
     * @param id        调用 id，不能为空
     * @param type      调用类型，空值默认 {@code function}
     * @param name      工具名，不能为空
     * @param arguments 原始 JSON 参数，{@code null} 转为空字符串
     */
    public ToolCall(String id, String type, String name, String arguments) {
        if (id == null || id.isBlank()) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "tool call id 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "tool name 不能为空");
        }
        this.id = id;
        this.type = type == null || type.isBlank() ? "function" : type;
        this.name = name;
        this.arguments = arguments == null ? "" : arguments;
    }

    public String id() {
        return id;
    }

    public String type() {
        return type;
    }

    public String name() {
        return name;
    }

    public String arguments() {
        return arguments;
    }
}
