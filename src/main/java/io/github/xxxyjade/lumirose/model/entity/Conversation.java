package io.github.xxxyjade.lumirose.model.entity;

import io.github.xxxyjade.lumirose.common.enums.MessageRole;
import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;

import java.util.List;

/**
 * 一个会话的不可变快照。
 *
 * <p>只包含 active 消息，不包含 system 消息；{@code contextTokens}
 * 是本轮请求估算的上下文 token 数。
 */
public final class Conversation {
    private final long id;
    private final List<Message> messages;
    private final int contextTokens;

    private Conversation(long id, List<Message> messages, int contextTokens) {
        this.id = id;
        this.messages = List.copyOf(messages);
        this.contextTokens = contextTokens;
    }

    /**
     * 创建空会话。
     *
     * @param id 会话 id
     * @return 没有消息、token 为 0 的会话
     */
    public static Conversation empty(long id) {
        return new Conversation(id, List.of(), 0);
    }

    /**
     * 创建会话快照。
     *
     * @param id            会话 id
     * @param messages      active 消息列表
     * @param contextTokens 上下文 token 数，不能为负
     * @return 会话快照
     * @throws AgentException 消息为 {@code null}、token 为负或包含 system 消息时抛出 {@code 1002}
     */
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
