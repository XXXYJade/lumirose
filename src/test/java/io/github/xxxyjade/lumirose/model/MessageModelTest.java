package io.github.xxxyjade.lumirose.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xxxyjade.lumirose.exception.AgentException;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionAssistantMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionResponseParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionToolCall;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionToolMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.FinishReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageModelTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void tc_m01_assistantNullContentKeepsToolCalls() {
        var message = ChatCompletionAssistantMessageParam.builder()
                .content(null)
                .toolCalls(List.of(toolCall()))
                .build();

        JsonNode json = mapper.valueToTree(message);

        assertTrue(json.get("content").isNull());
        assertEquals(1, json.get("tool_calls").size());
    }

    @Test
    void tc_m02_emptyToolCallsAreOmitted() {
        var message = ChatCompletionAssistantMessageParam.builder()
                .content("done")
                .toolCalls(List.of())
                .build();

        JsonNode json = mapper.valueToTree(message);

        assertFalse(json.has("tool_calls"));
    }

    @Test
    void tc_m03_emptyReasoningContentIsOmitted() {
        var message = ChatCompletionAssistantMessageParam.builder()
                .content("done")
                .reasoningContent(null)
                .build();

        JsonNode json = mapper.valueToTree(message);

        assertFalse(json.has("reasoning_content"));
    }

    @Test
    void tc_m04_toolMessageRejectsBlankId() {
        var missing = assertThrows(AgentException.class,
                () -> ChatCompletionToolMessageParam.builder().content("x").build());
        assertEquals(1002, missing.getErrorCode().code());

        var blank = assertThrows(AgentException.class,
                () -> ChatCompletionToolMessageParam.builder().content("x").toolCallId("  ").build());
        assertEquals(1002, blank.getErrorCode().code());
    }

    @Test
    void tc_m05_deserializeToolCallOnlyResponse() throws Exception {
        String json = """
                {
                  "id": "chatcmpl-1",
                  "object": "chat.completion",
                  "created": 1730000000,
                  "model": "deepseek-chat",
                  "choices": [
                    {
                      "index": 0,
                      "finish_reason": "tool_calls",
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "reasoning_content": "需要读取文件",
                        "tool_calls": [
                          {
                            "id": "call_1",
                            "type": "function",
                            "function": {"name": "read_file", "arguments": "{\\"path\\":\\"a.txt\\"}"}
                          }
                        ]
                      }
                    }
                  ],
                  "unknown_field": true
                }
                """;

        ChatCompletionResponseParam response = mapper.readValue(json, ChatCompletionResponseParam.class);
        var message = response.choices().get(0).message();

        assertNull(message.content());
        assertEquals(1, message.toolCalls().size());
        assertEquals("read_file", message.toolCalls().get(0).function().name());
        assertEquals("{\"path\":\"a.txt\"}", message.toolCalls().get(0).function().arguments());
        assertEquals(FinishReason.TOOL_CALLS, FinishReason.from(response.choices().get(0).finishReason()));
    }

    @Test
    void tc_m06_toolMessageSerialization() throws Exception {
        var message = ChatCompletionToolMessageParam.builder()
                .toolCallId("c1")
                .content("x")
                .build();

        JsonNode expected = mapper.readTree("{\"role\":\"tool\",\"tool_call_id\":\"c1\",\"content\":\"x\"}");

        assertEquals(expected, mapper.valueToTree(message));
    }

    @Test
    void tc_m07_finishReasonMapping() {
        assertEquals(FinishReason.STOP, FinishReason.from("stop"));
        assertEquals(FinishReason.LENGTH, FinishReason.from("length"));
        assertEquals(FinishReason.TOOL_CALLS, FinishReason.from("tool_calls"));
        assertEquals(FinishReason.CONTENT_FILTER, FinishReason.from("content_filter"));
        assertEquals(FinishReason.INSUFFICIENT_SYSTEM_RESOURCE, FinishReason.from("insufficient_system_resource"));
        assertEquals(FinishReason.UNKNOWN, FinishReason.from("weird"));
        assertEquals(FinishReason.UNKNOWN, FinishReason.from(null));
    }

    private ChatCompletionToolCall toolCall() {
        return ChatCompletionToolCall.builder()
                .id("call_1")
                .type("function")
                .function(ChatCompletionToolCall.Function.builder()
                        .name("read_file")
                        .arguments("{\"path\":\"a.txt\"}")
                        .build())
                .build();
    }
}
