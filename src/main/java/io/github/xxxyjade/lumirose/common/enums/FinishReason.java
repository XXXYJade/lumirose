package io.github.xxxyjade.lumirose.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 模型结束原因。
 *
 * <p>只保留协议中已定义的三种值；未知值通过 {@link #fromWire(String)}
 * 返回 {@code null}，由 Agent 按响应语义非法处理。
 */
public enum FinishReason {
    STOP("stop"),
    LENGTH("length"),
    TOOL_CALLS("tool_calls");

    private final String wire;

    FinishReason(String wire) {
        this.wire = wire;
    }

    /**
     * 返回协议字符串。
     *
     * @return {@code stop}、{@code length} 或 {@code tool_calls}
     */
    @JsonValue
    public String wire() {
        return wire;
    }

    /**
     * 按协议字符串查找结束原因。
     *
     * @param value 协议字符串
     * @return 匹配的结束原因；未知值返回 {@code null}
     */
    public static FinishReason fromWire(String value) {
        for (FinishReason reason : values()) {
            if (reason.wire.equals(value)) {
                return reason;
            }
        }
        return null;
    }
}
