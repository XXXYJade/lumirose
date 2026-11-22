package io.github.xxxyjade.lumirose.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FinishReason {
    STOP("stop"),
    LENGTH("length"),
    TOOL_CALLS("tool_calls");

    private final String wire;

    FinishReason(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    public static FinishReason fromWire(String value) {
        for (FinishReason reason : values()) {
            if (reason.wire.equals(value)) {
                return reason;
            }
        }
        return null;
    }
}
