package io.github.xxxyjade.lumirose.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageRole {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

    private final String wire;

    MessageRole(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    public static MessageRole fromWire(String value) {
        for (MessageRole role : values()) {
            if (role.wire.equals(value)) {
                return role;
            }
        }
        return null;
    }
}
