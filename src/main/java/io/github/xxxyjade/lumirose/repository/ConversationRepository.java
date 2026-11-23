package io.github.xxxyjade.lumirose.repository;

import io.github.xxxyjade.lumirose.model.entity.Conversation;
import io.github.xxxyjade.lumirose.model.entity.Message;

import java.util.List;

public interface ConversationRepository {
    Conversation load(long conversationId);

    void append(long conversationId, List<Message> pending, int contextTokens);

    void compact(long conversationId, Message summary, List<Message> pending, int contextTokens);
}
