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

public final class MessageConverter {
    private MessageConverter() {
    }

    public static Message toEntity(MessageDO messageDO, List<ToolCallDO> toolCallDOs) {
        if (messageDO == null || messageDO.role() == null) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "消息角色非法");
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        if (toolCallDOs != null) {
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

    public static MessageDO toDo(long id, long conversationId, Message message,
                                 boolean active, Instant now) {
        return new MessageDO(id, conversationId, message.role(), message.content(),
                message.reasoningContent(), message.toolCallId(), active, now, now);
    }
}
