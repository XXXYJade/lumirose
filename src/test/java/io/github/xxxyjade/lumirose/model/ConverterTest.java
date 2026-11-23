package io.github.xxxyjade.lumirose.model;

import io.github.xxxyjade.lumirose.common.enums.MessageRole;
import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.model.convert.ConversationConverter;
import io.github.xxxyjade.lumirose.model.convert.MessageConverter;
import io.github.xxxyjade.lumirose.model.convert.ToolCallConverter;
import io.github.xxxyjade.lumirose.model.data.ConversationDO;
import io.github.xxxyjade.lumirose.model.data.MessageDO;
import io.github.xxxyjade.lumirose.model.data.ToolCallDO;
import io.github.xxxyjade.lumirose.model.entity.Conversation;
import io.github.xxxyjade.lumirose.model.entity.Message;
import io.github.xxxyjade.lumirose.model.entity.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConverterTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void messageDoAndToolCallsMapToAssistantInCallIndexOrder() {
        MessageDO messageDO = new MessageDO(
                10L, 1L, MessageRole.ASSISTANT, "text", "reason", null, true, NOW, NOW);
        ToolCallDO callOne = new ToolCallDO(
                2L, 10L, 1, "call_1", "function", "write_file", "{\"path\":\"b\"}", NOW);
        ToolCallDO callZero = new ToolCallDO(
                1L, 10L, 0, "call_0", "function", "read_file", "{\"path\":\"a\"}", NOW);

        Message message = MessageConverter.toEntity(messageDO, List.of(callOne, callZero));

        assertEquals(MessageRole.ASSISTANT, message.role());
        assertEquals("text", message.content());
        assertEquals("reason", message.reasoningContent());
        assertNull(message.toolCallId());
        assertEquals(2, message.toolCalls().size());
        assertEquals("call_0", message.toolCalls().get(0).id());
        assertEquals("read_file", message.toolCalls().get(0).name());
        assertEquals("{\"path\":\"a\"}", message.toolCalls().get(0).arguments());
        assertEquals("call_1", message.toolCalls().get(1).id());
    }

    @Test
    void messageToDoPreservesFieldsAndRole() {
        ToolCall toolCall = new ToolCall("call_0", "function", "read_file", "{\"path\":\"a\"}");
        Message message = Message.assistant("text", "reason", List.of(toolCall));

        MessageDO messageDO = MessageConverter.toDo(10L, 1L, message, true, NOW);

        assertEquals(10L, messageDO.id());
        assertEquals(1L, messageDO.conversationId());
        assertEquals(MessageRole.ASSISTANT, messageDO.role());
        assertEquals("text", messageDO.content());
        assertEquals("reason", messageDO.reasoningContent());
        assertNull(messageDO.toolCallId());
        assertTrue(messageDO.active());
        assertEquals(NOW, messageDO.createdAt());
        assertEquals(NOW, messageDO.updatedAt());
    }

    @Test
    void toolCallRoundTripPreservesFields() {
        ToolCall toolCall = new ToolCall("call_0", "function", "read_file", "{\"path\":\"a\"}");

        ToolCallDO toolCallDO = ToolCallConverter.toDo(10L, 3, toolCall, NOW);
        ToolCall roundTrip = ToolCallConverter.toEntity(toolCallDO);

        assertEquals(0L, toolCallDO.id());
        assertEquals(10L, toolCallDO.messageId());
        assertEquals(3, toolCallDO.callIndex());
        assertEquals("call_0", toolCallDO.callId());
        assertEquals("function", toolCallDO.callType());
        assertEquals("read_file", toolCallDO.functionName());
        assertEquals("{\"path\":\"a\"}", toolCallDO.arguments());
        assertEquals(NOW, toolCallDO.createdAt());
        assertEquals("call_0", roundTrip.id());
        assertEquals("function", roundTrip.type());
        assertEquals("read_file", roundTrip.name());
        assertEquals("{\"path\":\"a\"}", roundTrip.arguments());
    }

    @Test
    void conversationRoundTripPreservesFields() {
        Conversation conversation = Conversation.of(7L, List.of(Message.user("u")), 5);

        ConversationDO conversationDO = ConversationConverter.toDo(conversation, NOW);
        Conversation roundTrip = ConversationConverter.toEntity(conversationDO, List.of(Message.user("u")));

        assertEquals(7L, conversationDO.id());
        assertEquals(5, conversationDO.contextTokens());
        assertEquals(NOW, conversationDO.createdAt());
        assertEquals(NOW, conversationDO.updatedAt());
        assertEquals(7L, roundTrip.id());
        assertEquals(5, roundTrip.contextTokens());
        assertEquals(1, roundTrip.messages().size());
        assertEquals("u", roundTrip.messages().get(0).content());
    }

    @Test
    void toolMessageMapsToolCallId() {
        MessageDO messageDO = new MessageDO(
                11L, 1L, MessageRole.TOOL, "result", null, "call_0", true, NOW, NOW);

        Message message = MessageConverter.toEntity(messageDO, List.of());

        assertEquals(MessageRole.TOOL, message.role());
        assertEquals("call_0", message.toolCallId());
        assertEquals("result", message.content());
    }

    @Test
    void invalidMessagesAndToolCallsAreRejected() {
        MessageDO assistant = new MessageDO(
                1L, 1L, MessageRole.ASSISTANT, "x", null, null, true, NOW, NOW);
        MessageDO noRole = new MessageDO(
                1L, 1L, null, "x", null, null, true, NOW, NOW);
        MessageDO toolWithoutId = new MessageDO(
                1L, 1L, MessageRole.TOOL, "x", null, null, true, NOW, NOW);
        ToolCallDO noCallId = new ToolCallDO(
                1L, 1L, 0, null, "function", "read_file", "{}", NOW);
        ToolCallDO noName = new ToolCallDO(
                1L, 1L, 0, "call_0", "function", " ", "{}", NOW);

        assertParamInvalid(() -> MessageConverter.toEntity(null, List.of()));
        assertParamInvalid(() -> MessageConverter.toEntity(noRole, List.of()));
        assertParamInvalid(() -> MessageConverter.toEntity(toolWithoutId, List.of()));
        assertParamInvalid(() -> MessageConverter.toEntity(assistant, List.of(noCallId)));
        assertParamInvalid(() -> MessageConverter.toEntity(assistant, List.of(noName)));
    }

    private static void assertParamInvalid(Executable executable) {
        AgentException exception = assertThrows(AgentException.class, executable);
        assertEquals(LumiRoseErrorCode.PARAM_INVALID, exception.getErrorCode());
    }
}
