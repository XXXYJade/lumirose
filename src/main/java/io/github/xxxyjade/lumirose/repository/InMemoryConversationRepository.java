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

/**
 * 线程安全的内存会话仓储。
 *
 * <p>不同会话可以并行写入；同一会话的写入在 {@code StoredConversation}
 * 的监视器上串行。消息物理保留，压缩只切换 {@code active} 标志。
 */
public final class InMemoryConversationRepository implements ConversationRepository {
    private final ConcurrentHashMap<Long, StoredConversation> conversations = new ConcurrentHashMap<>();
    private final AtomicLong messageIds = new AtomicLong();
    private final AtomicLong toolCallIds = new AtomicLong();

    /**
     * {@inheritDoc}
     *
     * <p>本实现返回的快照与内部可变列表解耦。
     */
    @Override
    public Conversation load(long conversationId) {
        StoredConversation stored = conversations.get(conversationId);
        if (stored == null) {
            // 空仓储只返回空会话，不能因为一次 load 而建行。
            return Conversation.empty(conversationId);
        }
        synchronized (stored) {
            List<Message> messages = stored.messages.stream()
                    // 压缩后的旧消息仍保留在内存中，但不再对上层可见。
                    .filter(item -> item.message.active())
                    .sorted(Comparator.comparingLong(item -> item.message.id()))
                    .map(item -> MessageConverter.toEntity(item.message, item.toolCalls))
                    .toList();
            return Conversation.of(conversationId, messages, stored.contextTokens);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>同一会话的写入在会话监视器上串行；不同会话可并行。
     */
    @Override
    public void append(long conversationId, List<Message> pending, int contextTokens) {
        if (pending == null) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "pending 不能为空");
        }
        if (pending.isEmpty()) {
            // 空 append 是 no-op，不能创建会话行。
            return;
        }
        if (contextTokens < 0) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "contextTokens 不能为负");
        }
        Instant now = Instant.now();
        conversations.compute(conversationId, (id, existing) -> {
            StoredConversation stored = existing == null ? new StoredConversation(id, now) : existing;
            // compute 只保证 map 层面的互斥；同一会话的列表修改仍在 stored 上串行。
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

    /**
     * {@inheritDoc}
     *
     * <p>旧消息只切换 {@code active}，不删除记录，id 也不重置。
     */
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
                // 逻辑截断：旧 active 全部停用，但记录仍保留，id 也不回退。
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
            // 工具调用行单独分配 id，但用 messageId + callIndex 与消息关联。
            toolCalls.add(new ToolCallDO(toolCallIds.incrementAndGet(), messageId, index,
                    converted.callId(), converted.callType(), converted.functionName(),
                    converted.arguments(), now));
        }
        stored.messages.add(new StoredMessage(messageDO, List.copyOf(toolCalls)));
    }

    /**
     * 单个会话的内存状态；所有可变字段只在实例监视器内访问。
     */
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

    /**
     * 一条消息及其工具调用行；工具调用行本身不需要停用。
     */
    private static final class StoredMessage {
        private MessageDO message;
        private final List<ToolCallDO> toolCalls;

        private StoredMessage(MessageDO message, List<ToolCallDO> toolCalls) {
            this.message = message;
            this.toolCalls = toolCalls;
        }

        /**
         * 逻辑停用：MessageDO 不可变，用 active=false 的新实例替换。
         */
        private void deactivate() {
            MessageDO old = message;
            message = new MessageDO(old.id(), old.conversationId(), old.role(), old.content(),
                    old.reasoningContent(), old.toolCallId(), false, old.createdAt(), old.updatedAt());
        }
    }
}
