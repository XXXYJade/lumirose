package io.github.xxxyjade.lumirose.repository;

import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionUserMessageParam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仓储契约：第三方实现复制本文件后继承，并实现 {@link #newRepository()}。
 */
public abstract class RepositoryContract {

    protected abstract MessageRepository newRepository();

    @Test
    void tc_rp01_emptyConversationReturnsEmptyList() {
        assertTrue(newRepository().load(1L).isEmpty());
    }

    @Test
    void tc_rp02_appendThenLoadIsConsistentAndSnapshotIsImmutable() {
        MessageRepository repository = newRepository();

        repository.appendAll(1L, List.of(user("u")));
        List<ChatCompletionMessageParam> loaded = repository.load(1L);

        assertEquals(1, loaded.size());
        assertThrows(UnsupportedOperationException.class, () -> loaded.add(user("x")));
        assertEquals(1, repository.load(1L).size());
    }

    @Test
    void tc_rp03_conversationsAreIsolatedAndEmptyAppendIsNoop() {
        MessageRepository repository = newRepository();

        repository.appendAll(1L, List.of(user("a")));
        repository.appendAll(2L, List.of(user("b")));
        repository.appendAll(1L, List.of());

        assertEquals("a", content(repository.load(1L).get(0)));
        assertEquals(1, repository.load(1L).size());
        assertEquals("b", content(repository.load(2L).get(0)));
    }

    private static ChatCompletionUserMessageParam user(String content) {
        return ChatCompletionUserMessageParam.builder().content(content).build();
    }

    private static String content(ChatCompletionMessageParam message) {
        return ((ChatCompletionUserMessageParam) message).content();
    }
}
