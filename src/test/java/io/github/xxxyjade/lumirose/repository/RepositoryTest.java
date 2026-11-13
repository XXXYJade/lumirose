package io.github.xxxyjade.lumirose.repository;

import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionUserMessageParam;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryTest {

    private final InMemoryMessageRepository repository = new InMemoryMessageRepository();

    @Test
    void tc_d01_emptyConversationReturnsEmptyImmutableList() {
        var messages = repository.load(1L);

        assertTrue(messages.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> messages.add(user("x")));
    }

    @Test
    void tc_d02_appendAllKeepsOrder() {
        repository.appendAll(1L, List.of(user("a")));
        repository.appendAll(1L, List.of(user("b"), user("c")));

        var messages = repository.load(1L);

        assertEquals(3, messages.size());
        assertEquals("a", contentOf(messages.get(0)));
        assertEquals("b", contentOf(messages.get(1)));
        assertEquals("c", contentOf(messages.get(2)));
    }

    @Test
    void tc_d03_emptyAppendIsNoop() {
        repository.appendAll(1L, List.of());

        assertTrue(repository.load(1L).isEmpty());
    }

    @Test
    void tc_d04_concurrentAppendsKeepAllMessages() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 8; i++) {
            pool.submit(() -> {
                for (int j = 0; j < 100; j++) {
                    repository.appendAll(1L, List.of(user("m")));
                }
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(800, repository.load(1L).size());
    }

    @Test
    void tc_d05_conversationsAreIsolated() {
        repository.appendAll(1L, List.of(user("one")));
        repository.appendAll(2L, List.of(user("two")));

        var first = repository.load(1L);
        var second = repository.load(2L);

        assertEquals(1, first.size());
        assertEquals("one", contentOf(first.get(0)));
        assertEquals(1, second.size());
        assertEquals("two", contentOf(second.get(0)));
    }

    private ChatCompletionUserMessageParam user(String content) {
        return ChatCompletionUserMessageParam.builder().content(content).build();
    }

    private String contentOf(ChatCompletionMessageParam message) {
        return ((ChatCompletionUserMessageParam) message).content();
    }
}
