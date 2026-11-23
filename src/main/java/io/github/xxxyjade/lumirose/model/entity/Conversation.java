package io.github.xxxyjade.lumirose.model.entity;

import io.github.xxxyjade.lumirose.common.enums.MessageRole;
import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;

import java.util.List;

public final class Conversation {
    private final long id;
    private final List<Message> messages;
    private final int contextTokens;

    private Conversation(long id, List<Message> messages, int contextTokens) {
        this.id = id;
        this.messages = List.copyOf(messages);
        this.contextTokens = contextTokens;
    }

    public static Conversation empty(long id) {
        return new Conversation(id, List.of(), 0);
    }

    public static Conversation of(long id, List<Message> messages, int contextTokens) {
        if (messages == null) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "messages 不能为空");
        }
        if (contextTokens < 0) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "contextTokens 不能为负");
        }
        for (Message message : messages) {
            if (message.role() == MessageRole.SYSTEM) {
                throw new AgentException(LumiRoseErrorCode.PARAM_INVALID,
                        "会话历史不能包含 system 消息");
            }
        }
        return new Conversation(id, messages, contextTokens);
    }

    public long id() {
        return id;
    }

    public List<Message> messages() {
        return messages;
    }

    public int contextTokens() {
        return contextTokens;
    }
}
