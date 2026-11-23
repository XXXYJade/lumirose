package io.github.xxxyjade.lumirose.model.entity;

import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;

public final class ToolCall {
    private final String id;
    private final String type;
    private final String name;
    private final String arguments;

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
