package io.github.xxxyjade.lumirose.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 消息角色。
 *
 * <p>{@link #wire()} 返回协议中的字符串值；{@link #fromWire(String)}
 * 对未知值返回 {@code null}，由调用方决定如何报错。
 */
public enum MessageRole {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

    private final String wire;

    MessageRole(String wire) {
        this.wire = wire;
    }

    /**
     * 返回协议字符串。
     *
     * @return {@code system}、{@code user}、{@code assistant} 或 {@code tool}
     */
    @JsonValue
    public String wire() {
        return wire;
    }

    /**
     * 按协议字符串查找角色。
     *
     * @param value 协议字符串
     * @return 匹配的角色；未知值返回 {@code null}
     */
    public static MessageRole fromWire(String value) {
        for (MessageRole role : values()) {
            if (role.wire.equals(value)) {
                return role;
            }
        }
        return null;
    }
}
