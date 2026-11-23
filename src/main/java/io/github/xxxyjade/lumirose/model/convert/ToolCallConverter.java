package io.github.xxxyjade.lumirose.model.convert;

import io.github.xxxyjade.lumirose.model.data.ToolCallDO;
import io.github.xxxyjade.lumirose.model.entity.ToolCall;

import java.time.Instant;

public final class ToolCallConverter {
    private ToolCallConverter() {
    }

    public static ToolCall toEntity(ToolCallDO toolCallDO) {
        return new ToolCall(toolCallDO.callId(), toolCallDO.callType(),
                toolCallDO.functionName(), toolCallDO.arguments());
    }

    public static ToolCallDO toDo(long messageId, int callIndex, ToolCall toolCall, Instant now) {
        return new ToolCallDO(0L, messageId, callIndex, toolCall.id(), toolCall.type(),
                toolCall.name(), toolCall.arguments(), now);
    }
}
