package io.github.xxxyjade.lumirose.testing;

import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessageParam;
import io.github.xxxyjade.lumirose.repository.InMemoryMessageRepository;
import io.github.xxxyjade.lumirose.repository.MessageRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 包装内存仓储，记录 load / append 的调用顺序。
 */
public final class RecordingRepository implements MessageRepository {

    private final InMemoryMessageRepository delegate = new InMemoryMessageRepository();
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public List<ChatCompletionMessageParam> load(Long conversationId) {
        events.add("load:" + conversationId);
        return delegate.load(conversationId);
    }

    @Override
    public void appendAll(Long conversationId, List<ChatCompletionMessageParam> messages) {
        events.add("append:" + conversationId);
        delegate.appendAll(conversationId, messages);
    }

    public List<String> events() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    public int count(String prefix) {
        synchronized (events) {
            int count = 0;
            for (String event : events) {
                if (event.startsWith(prefix)) {
                    count++;
                }
            }
            return count;
        }
    }
}
