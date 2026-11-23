package io.github.xxxyjade.lumirose.model.convert;

import io.github.xxxyjade.lumirose.common.enums.MessageRole;
import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.model.data.MessageDO;
import io.github.xxxyjade.lumirose.model.data.ToolCallDO;
import io.github.xxxyjade.lumirose.model.entity.Message;
import io.github.xxxyjade.lumirose.model.entity.ToolCall;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 消息 DO 与实体之间的转换。
 *
 * <p>读取时按 {@code callIndex} 排序工具调用；写入时只复制实体字段，
 * 行 id 和时间戳由仓储负责。
 */
public final class MessageConverter {
    private MessageConverter() {
    }

    /**
     * 把消息行和工具调用行还原为实体。
     *
     * @param messageDO   消息行
     * @param toolCallDOs 该消息的工具调用行，可为 {@code null}
     * @return 消息实体
     * @throws AgentException 角色非法、tool 消息缺少 id 或工具调用字段非法时抛出 {@code 1002}
     */
    public static Message toEntity(MessageDO messageDO, List<ToolCallDO> toolCallDOs) {
        if (messageDO == null || messageDO.role() == null) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "消息角色非法");
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        if (toolCallDOs != null) {
            // 数据库不保证返回顺序，按 callIndex 恢复模型原始调用顺序。
            toolCallDOs.stream()
                    .sorted(Comparator.comparingInt(ToolCallDO::callIndex))
                    .map(ToolCallConverter::toEntity)
                    .forEach(toolCalls::add);
        }
        return switch (messageDO.role()) {
            case SYSTEM -> Message.system(messageDO.content());
            case USER -> Message.user(messageDO.content());
            case ASSISTANT -> Message.assistant(messageDO.content(),
                    messageDO.reasoningContent(), toolCalls);
            case TOOL -> Message.tool(messageDO.toolCallId(), messageDO.content());
        };
    }

    /**
     * 把消息实体转换为新写入的消息行。
     *
     * @param id             消息 id
     * @param conversationId 所属会话 id
     * @param message        消息实体
     * @param active         是否 active
     * @param now            写入时间
     * @return 消息 DO
     */
    public static MessageDO toDo(long id, long conversationId, Message message,
                                 boolean active, Instant now) {
        return new MessageDO(id, conversationId, message.role(), message.content(),
                message.reasoningContent(), message.toolCallId(), active, now, now);
    }
}
