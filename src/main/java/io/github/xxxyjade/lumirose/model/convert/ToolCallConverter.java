package io.github.xxxyjade.lumirose.model.convert;

import io.github.xxxyjade.lumirose.model.data.ToolCallDO;
import io.github.xxxyjade.lumirose.model.entity.ToolCall;

import java.time.Instant;

/**
 * 工具调用 DO 与实体之间的转换。
 */
public final class ToolCallConverter {
    private ToolCallConverter() {
    }

    /**
     * 把工具调用行还原为实体。
     *
     * @param toolCallDO 工具调用行
     * @return 工具调用实体
     */
    public static ToolCall toEntity(ToolCallDO toolCallDO) {
        return new ToolCall(toolCallDO.callId(), toolCallDO.callType(),
                toolCallDO.functionName(), toolCallDO.arguments());
    }

    /**
     * 把工具调用实体转换为新写入的行。
     *
     * <p>返回的 DO 行 id 为 0，由仓储在插入时分配。
     *
     * @param messageId 所属消息 id
     * @param callIndex 调用顺序
     * @param toolCall  工具调用实体
     * @param now       写入时间
     * @return 工具调用 DO
     */
    public static ToolCallDO toDo(long messageId, int callIndex, ToolCall toolCall, Instant now) {
        return new ToolCallDO(0L, messageId, callIndex, toolCall.id(), toolCall.type(),
                toolCall.name(), toolCall.arguments(), now);
    }
}
