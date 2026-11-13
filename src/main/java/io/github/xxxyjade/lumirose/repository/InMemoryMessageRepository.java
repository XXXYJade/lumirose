package io.github.xxxyjade.lumirose.repository;

import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessageParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息存储（内存实现）。按会话隔离，同一会话的读写串行。
 */
public final class InMemoryMessageRepository implements MessageRepository {

    private final Map<Long, List<ChatCompletionMessageParam>> memoryStore = new ConcurrentHashMap<>();

    @Override
    public void appendAll(Long conversationId, List<ChatCompletionMessageParam> messages) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(messages, "messages");
        if (messages.isEmpty()) {
            return;
        }
        List<ChatCompletionMessageParam> memory =
                memoryStore.computeIfAbsent(conversationId, id -> new ArrayList<>());
        synchronized (memory) {
            memory.addAll(messages);
        }
    }

    @Override
    public List<ChatCompletionMessageParam> load(Long conversationId) {
        Objects.requireNonNull(conversationId, "conversationId");
        List<ChatCompletionMessageParam> memory = memoryStore.get(conversationId);
        if (memory == null) {
            return List.of();
        }
        synchronized (memory) {
            return List.copyOf(memory);
        }
    }
}
