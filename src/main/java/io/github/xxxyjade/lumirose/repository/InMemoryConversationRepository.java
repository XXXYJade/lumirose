package io.github.xxxyjade.lumirose.repository;

import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.model.convert.MessageConverter;
import io.github.xxxyjade.lumirose.model.convert.ToolCallConverter;
import io.github.xxxyjade.lumirose.model.data.MessageDO;
import io.github.xxxyjade.lumirose.model.data.ToolCallDO;
import io.github.xxxyjade.lumirose.model.entity.Conversation;
import io.github.xxxyjade.lumirose.model.entity.Message;
import io.github.xxxyjade.lumirose.model.entity.ToolCall;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryConversationRepository implements ConversationRepository {
    private final ConcurrentHashMap<Long, StoredConversation> conversations = new ConcurrentHashMap<>();
    private final AtomicLong messageIds = new AtomicLong();
    private final AtomicLong toolCallIds = new AtomicLong();

    @Override
    public Conversation load(long conversationId) {
        StoredConversation stored = conversations.get(conversationId);
        if (stored == null) {
            return Conversation.empty(conversationId);
        }
        synchronized (stored) {
            List<Message> messages = stored.messages.stream()
                    .filter(item -> item.message.active())
                    .sorted(Comparator.comparingLong(item -> item.message.id()))
                    .map(item -> MessageConverter.toEntity(item.message, item.toolCalls))
                    .toList();
            return Conversation.of(conversationId, messages, stored.contextTokens);
        }
    }

    @Override
    public void append(long conversationId, List<Message> pending, int contextTokens) {
        if (pending == null) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "pending 不能为空");
        }
        if (pending.isEmpty()) {
            return;
        }
        if (contextTokens < 0) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "contextTokens 不能为负");
        }
        Instant now = Instant.now();
        conversations.compute(conversationId, (id, existing) -> {
            StoredConversation stored = existing == null ? new StoredConversation(id, now) : existing;
            synchronized (stored) {
                for (Message message : pending) {
                    addMessage(stored, message, true, now);
                }
                stored.contextTokens = contextTokens;
                stored.updatedAt = now;
            }
            return stored;
        });
    }

    @Override
    public void compact(long conversationId, Message summary, List<Message> pending, int contextTokens) {
        if (summary == null) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "summary 不能为空");
        }
        if (pending == null) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "pending 不能为空");
        }
        if (contextTokens < 0) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "contextTokens 不能为负");
        }
        Instant now = Instant.now();
        conversations.compute(conversationId, (id, existing) -> {
            StoredConversation stored = existing == null ? new StoredConversation(id, now) : existing;
            synchronized (stored) {
                for (StoredMessage message : stored.messages) {
                    message.deactivate();
                }
                addMessage(stored, summary, true, now);
                for (Message message : pending) {
                    addMessage(stored, message, true, now);
                }
                stored.contextTokens = contextTokens;
                stored.updatedAt = now;
            }
            return stored;
        });
    }

    private void addMessage(StoredConversation stored, Message message, boolean active, Instant now) {
        long messageId = messageIds.incrementAndGet();
        MessageDO messageDO = MessageConverter.toDo(messageId, stored.id, message, active, now);
        List<ToolCallDO> toolCalls = new ArrayList<>();
        for (int index = 0; index < message.toolCalls().size(); index++) {
            ToolCall toolCall = message.toolCalls().get(index);
            ToolCallDO converted = ToolCallConverter.toDo(messageId, index, toolCall, now);
            toolCalls.add(new ToolCallDO(toolCallIds.incrementAndGet(), messageId, index,
                    converted.callId(), converted.callType(), converted.functionName(),
                    converted.arguments(), now));
        }
        stored.messages.add(new StoredMessage(messageDO, List.copyOf(toolCalls)));
    }

    private static final class StoredConversation {
        private final long id;
        private final Instant createdAt;
        private final List<StoredMessage> messages = new ArrayList<>();
        private int contextTokens;
        private Instant updatedAt;

        private StoredConversation(long id, Instant now) {
            this.id = id;
            this.createdAt = now;
            this.updatedAt = now;
        }
    }

    private static final class StoredMessage {
        private MessageDO message;
        private final List<ToolCallDO> toolCalls;

        private StoredMessage(MessageDO message, List<ToolCallDO> toolCalls) {
            this.message = message;
            this.toolCalls = toolCalls;
        }

        private void deactivate() {
            MessageDO old = message;
            message = new MessageDO(old.id(), old.conversationId(), old.role(), old.content(),
                    old.reasoningContent(), old.toolCallId(), false, old.createdAt(), old.updatedAt());
        }
    }
}
