package io.github.xxxyjade.lumirose.repository;

import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.model.entity.Conversation;
import io.github.xxxyjade.lumirose.model.entity.Message;
import io.github.xxxyjade.lumirose.model.entity.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class RepositoryContract {

    protected abstract ConversationRepository newRepository();

    @Test
    void loadEmptyReturnsEmptyConversation() {
        Conversation conversation = newRepository().load(42L);

        assertEquals(42L, conversation.id());
        assertTrue(conversation.messages().isEmpty());
        assertEquals(0, conversation.contextTokens());
    }

    @Test
    void appendThenLoadReturnsMessagesInOrderAndUpdatesContextTokens() {
        ConversationRepository repository = newRepository();

        repository.append(1L, List.of(Message.user("u1"), Message.assistant("a1", null, List.of())), 10);

        Conversation conversation = repository.load(1L);
        assertEquals(List.of("u1", "a1"), contents(conversation));
        assertEquals(10, conversation.contextTokens());
    }

    @Test
    void appendDoesNotDisturbExistingActiveMessages() {
        ConversationRepository repository = newRepository();
        repository.append(1L, List.of(Message.user("u1"), Message.assistant("a1", null, List.of())), 10);

        repository.append(1L, List.of(Message.user("u2")), 20);
        repository.append(1L, List.of(), 99);

        Conversation conversation = repository.load(1L);
        assertEquals(List.of("u1", "a1", "u2"), contents(conversation));
        assertEquals(20, conversation.contextTokens());
    }

    @Test
    void compactDeactivatesOldMessagesAndInsertsSummaryAndPending() {
        ConversationRepository repository = newRepository();
        ToolCall oldToolCall = new ToolCall("call_old", "function", "read_file", "{}");
        repository.append(1L, List.of(
                Message.user("u1"),
                Message.assistant("a1", "reason", List.of(oldToolCall))), 10);

        repository.compact(1L,
                Message.assistant("summary", null, List.of()),
                List.of(Message.user("u2")),
                42);

        Conversation conversation = repository.load(1L);
        assertEquals(List.of("summary", "u2"), contents(conversation));
        assertEquals(42, conversation.contextTokens());
        assertTrue(conversation.messages().get(0).toolCalls().isEmpty());
    }

    @Test
    void compactAgainDeactivatesPreviousSummaryAndPending() {
        ConversationRepository repository = newRepository();
        repository.append(1L, List.of(Message.user("u1")), 1);
        repository.compact(1L,
                Message.assistant("summary-1", null, List.of()),
                List.of(Message.user("u2")),
                2);
        repository.append(1L, List.of(Message.user("u3")), 3);

        repository.compact(1L,
                Message.assistant("summary-2", null, List.of()),
                List.of(Message.user("u4")),
                4);

        Conversation conversation = repository.load(1L);
        assertEquals(List.of("summary-2", "u4"), contents(conversation));
        assertEquals(4, conversation.contextTokens());
    }

    @Test
    void toolCallsAreAttachedToTheirAssistantMessageInIndexOrder() {
        ConversationRepository repository = newRepository();
        ToolCall first = new ToolCall("call_0", "function", "read_file", "{\"path\":\"a\"}");
        ToolCall second = new ToolCall("call_1", "function", "write_file", "{\"path\":\"b\"}");

        repository.append(1L, List.of(
                Message.user("u"),
                Message.assistant("preamble", "reason", List.of(first, second))), 7);
        repository.append(1L, List.of(
                Message.assistant(null, null,
                        List.of(new ToolCall("call_2", "function", "delete_file", "{}")))), 8);

        Conversation conversation = repository.load(1L);
        Message firstAssistant = conversation.messages().get(1);
        assertEquals("preamble", firstAssistant.content());
        assertEquals("reason", firstAssistant.reasoningContent());
        assertEquals(2, firstAssistant.toolCalls().size());
        assertEquals("call_0", firstAssistant.toolCalls().get(0).id());
        assertEquals("read_file", firstAssistant.toolCalls().get(0).name());
        assertEquals("{\"path\":\"a\"}", firstAssistant.toolCalls().get(0).arguments());
        assertEquals("call_1", firstAssistant.toolCalls().get(1).id());

        Message secondAssistant = conversation.messages().get(2);
        assertNull(secondAssistant.content());
        assertEquals(1, secondAssistant.toolCalls().size());
        assertEquals("call_2", secondAssistant.toolCalls().get(0).id());
    }

    @Test
    void loadReturnsImmutableSnapshot() {
        ConversationRepository repository = newRepository();
        repository.append(1L, List.of(Message.user("u")), 1);

        Conversation snapshot = repository.load(1L);
        repository.append(1L, List.of(Message.user("v")), 2);

        assertEquals(1, snapshot.messages().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.messages().add(Message.user("x")));
    }

    @Test
    void appendAndCompactValidateArguments() {
        ConversationRepository repository = newRepository();
        Message summary = Message.assistant("summary", null, List.of());

        assertParamInvalid(() -> repository.append(1L, null, 0));
        assertParamInvalid(() -> repository.append(1L, List.of(Message.user("u")), -1));
        assertParamInvalid(() -> repository.compact(1L, null, List.of(), 0));
        assertParamInvalid(() -> repository.compact(1L, summary, null, 0));
        assertParamInvalid(() -> repository.compact(1L, summary, List.of(), -1));
    }

    @Test
    void differentConversationsCanBeWrittenConcurrently() throws Exception {
        ConversationRepository repository = newRepository();
        int conversationCount = 4;
        int messagesPerConversation = 25;
        ExecutorService pool = Executors.newFixedThreadPool(conversationCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < conversationCount; index++) {
                long conversationId = index + 1L;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int messageIndex = 0; messageIndex < messagesPerConversation; messageIndex++) {
                        repository.append(conversationId,
                                List.of(Message.user("u" + messageIndex)), 7);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        for (int index = 0; index < conversationCount; index++) {
            Conversation conversation = repository.load(index + 1L);
            assertEquals(messagesPerConversation, conversation.messages().size());
            assertEquals(7, conversation.contextTokens());
        }
    }

    private static List<String> contents(Conversation conversation) {
        return conversation.messages().stream().map(Message::content).toList();
    }

    private static void assertParamInvalid(Executable executable) {
        AgentException exception = assertThrows(AgentException.class, executable);
        assertEquals(LumiRoseErrorCode.PARAM_INVALID, exception.getErrorCode());
    }
}
