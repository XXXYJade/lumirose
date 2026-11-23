package io.github.xxxyjade.lumirose.repository;

import io.github.xxxyjade.lumirose.model.entity.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用共享契约测试验证内存实现，并补充会话行创建的内部断言。
 */
class InMemoryRepositoryContractTest extends RepositoryContract {

    @Override
    protected ConversationRepository newRepository() {
        return new InMemoryConversationRepository();
    }

    @Test
    void loadDoesNotCreateConversationRow() throws Exception {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();

        repository.load(1L);

        assertFalse(rows(repository).containsKey(1L));
    }

    @Test
    void emptyAppendDoesNotCreateConversationRow() throws Exception {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();

        repository.append(1L, List.of(), 0);

        assertFalse(rows(repository).containsKey(1L));
    }

    @Test
    void nonEmptyAppendCreatesConversationRow() throws Exception {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();

        repository.append(1L, List.of(Message.user("u")), 1);

        assertTrue(rows(repository).containsKey(1L));
    }

    @Test
    void compactOnEmptyStoreCreatesRowAndLoadsOnlyActiveMessages() throws Exception {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();

        repository.compact(1L,
                Message.assistant("summary", null, List.of()),
                List.of(Message.user("pending")),
                9);

        assertTrue(rows(repository).containsKey(1L));
        assertEquals(2, repository.load(1L).messages().size());
        assertEquals(9, repository.load(1L).contextTokens());
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, ?> rows(InMemoryConversationRepository repository) throws Exception {
        // 公共 load 无法区分“没有会话行”和“空会话”，这里直接检查内存实现的
        // 私有存储，只用于验证契约中的“是否建行”，不参与生产逻辑。
        Field field = InMemoryConversationRepository.class.getDeclaredField("conversations");
        field.setAccessible(true);
        return (Map<Long, ?>) field.get(repository);
    }
}
