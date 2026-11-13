package io.github.xxxyjade.lumirose.repository;

import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessageParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息存储(内存实现)
 */
public class InMemoryMessageRepository implements MessageRepository {

    private final Map<Long, List<ChatCompletionMessageParam>> memoryStore = new ConcurrentHashMap<>();

    public void append(Long conversationId, ChatCompletionMessageParam messageParam) {
        List<ChatCompletionMessageParam> memory = memoryStore.getOrDefault(conversationId, new ArrayList<>());
        memory.add(messageParam);
        memoryStore.put(conversationId, memory);
    }

    public void append(Long conversationId, List<ChatCompletionMessageParam> messageParams) {
        List<ChatCompletionMessageParam> memory = memoryStore.getOrDefault(conversationId, new ArrayList<>());
        memory.addAll(messageParams);
        memoryStore.put(conversationId, memory);
    }

    public List<ChatCompletionMessageParam> load(Long conversationId) {
        return memoryStore.getOrDefault(conversationId, new ArrayList<>());
    }
}
