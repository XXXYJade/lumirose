package io.github.xxxyjade.lumirose.model.convert;

import io.github.xxxyjade.lumirose.model.data.ConversationDO;
import io.github.xxxyjade.lumirose.model.entity.Conversation;
import io.github.xxxyjade.lumirose.model.entity.Message;

import java.time.Instant;
import java.util.List;

public final class ConversationConverter {
    private ConversationConverter() {
    }

    public static Conversation toEntity(ConversationDO conversationDO, List<Message> messages) {
        return Conversation.of(conversationDO.id(), messages, conversationDO.contextTokens());
    }

    public static ConversationDO toDo(Conversation conversation, Instant now) {
        return new ConversationDO(conversation.id(), conversation.contextTokens(), now, now);
    }
}
