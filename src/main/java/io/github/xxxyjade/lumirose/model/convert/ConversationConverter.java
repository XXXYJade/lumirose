package io.github.xxxyjade.lumirose.model.convert;

import io.github.xxxyjade.lumirose.model.data.ConversationDO;
import io.github.xxxyjade.lumirose.model.entity.Conversation;
import io.github.xxxyjade.lumirose.model.entity.Message;

import java.time.Instant;
import java.util.List;

/**
 * 会话 DO 与实体之间的转换。
 */
public final class ConversationConverter {
    private ConversationConverter() {
    }

    /**
     * 把会话行和 active 消息还原为实体。
     *
     * @param conversationDO 会话行
     * @param messages       active 消息，按 id 升序
     * @return 会话快照
     */
    public static Conversation toEntity(ConversationDO conversationDO, List<Message> messages) {
        return Conversation.of(conversationDO.id(), messages, conversationDO.contextTokens());
    }

    /**
     * 把会话实体转换为新写入的会话行。
     *
     * @param conversation 会话实体
     * @param now          写入时间
     * @return 会话 DO
     */
    public static ConversationDO toDo(Conversation conversation, Instant now) {
        return new ConversationDO(conversation.id(), conversation.contextTokens(), now, now);
    }
}
