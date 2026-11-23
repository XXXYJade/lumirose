package io.github.xxxyjade.lumirose.model;

import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.model.entity.Conversation;
import io.github.xxxyjade.lumirose.model.entity.Message;
import io.github.xxxyjade.lumirose.model.entity.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体工厂的校验规则和快照不可变性测试。
 */
class MessageModelTest {

    @Test
    void assistantRejectsEmptyContentAndToolCalls() {
        assertParamInvalid(() -> Message.assistant(null, null, List.of()));
        assertParamInvalid(() -> Message.assistant(" ", null, List.of()));
    }

    @Test
    void assistantAllowsReasoningAndToolCallWithoutContent() {
        ToolCall toolCall = new ToolCall("call_1", "function", "read_file", "{}");

        Message message = Message.assistant(null, "thinking", List.of(toolCall));

        assertNull(message.content());
        assertEquals("thinking", message.reasoningContent());
        assertEquals(1, message.toolCalls().size());
        assertEquals("call_1", message.toolCalls().get(0).id());
    }

    @Test
    void toolRequiresToolCallId() {
        assertParamInvalid(() -> Message.tool(null, "x"));
        assertParamInvalid(() -> Message.tool(" ", "x"));
    }

    @Test
    void userRejectsBlankContent() {
        assertParamInvalid(() -> Message.user(" "));
        assertParamInvalid(() -> Message.user(null));
    }

    @Test
    void conversationRejectsSystemMessages() {
        assertParamInvalid(() -> Conversation.of(1L, List.of(Message.system("system")), 0));
    }

    @Test
    void conversationRejectsNegativeContextTokens() {
        assertParamInvalid(() -> Conversation.of(1L, List.of(Message.user("hi")), -1));
    }

    @Test
    void toolCallsAreImmutable() {
        List<ToolCall> calls = new ArrayList<>();
        calls.add(new ToolCall("call_1", "function", "read_file", "{}"));

        Message message = Message.assistant("hi", null, calls);
        calls.clear();

        assertEquals(1, message.toolCalls().size());
        assertThrows(UnsupportedOperationException.class,
                () -> message.toolCalls().add(new ToolCall("call_2", "function", "read_file", "{}")));
    }

    @Test
    void conversationMessagesAreImmutable() {
        List<Message> history = new ArrayList<>();
        history.add(Message.user("hi"));

        Conversation conversation = Conversation.of(7L, history, 3);
        history.clear();

        assertEquals(1, conversation.messages().size());
        assertThrows(UnsupportedOperationException.class,
                () -> conversation.messages().add(Message.user("again")));
    }

    @Test
    void emptyConversationHasNoMessagesOrTokens() {
        Conversation conversation = Conversation.empty(7L);

        assertEquals(7L, conversation.id());
        assertTrue(conversation.messages().isEmpty());
        assertEquals(0, conversation.contextTokens());
        assertThrows(UnsupportedOperationException.class,
                () -> conversation.messages().add(Message.user("hi")));
    }

    private static void assertParamInvalid(Executable executable) {
        AgentException exception = assertThrows(AgentException.class, executable);
        assertEquals(LumiRoseErrorCode.PARAM_INVALID, exception.getErrorCode());
    }
}
