package io.github.xxxyjade.lumirose.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionChoice;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionMessage;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionRequest;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionResponse;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionTool;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionToolCall;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * chat DTO 的 JSON 字段形状测试。
 */
class DtoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void assistantSerializesContentNullReasoningAndToolCalls() {
        ChatCompletionToolCall toolCall = new ChatCompletionToolCall(
                "call_1",
                "function",
                new ChatCompletionToolCall.Function("read_file", "{\"path\":\"a.txt\"}"));

        ChatCompletionMessage message = ChatCompletionMessage.assistant(
                null, "thinking", List.of(toolCall));
        JsonNode json = mapper.valueToTree(message);

        assertEquals("assistant", json.get("role").asText());
        assertTrue(json.get("content").isNull());
        assertEquals("thinking", json.get("reasoning_content").asText());
        assertEquals(1, json.get("tool_calls").size());
        assertEquals("call_1", json.get("tool_calls").get(0).get("id").asText());
        assertEquals("function", json.get("tool_calls").get(0).get("type").asText());
        assertEquals("read_file", json.get("tool_calls").get(0).get("function").get("name").asText());
        assertEquals("{\"path\":\"a.txt\"}",
                json.get("tool_calls").get(0).get("function").get("arguments").asText());
    }

    @Test
    void toolMessageSerializesToolCallIdAndOmitsEmptyOptionalFields() {
        ChatCompletionMessage message = ChatCompletionMessage.tool("call_1", "result");
        JsonNode json = mapper.valueToTree(message);

        assertEquals("tool", json.get("role").asText());
        assertEquals("call_1", json.get("tool_call_id").asText());
        assertEquals("result", json.get("content").asText());
        assertFalse(json.has("reasoning_content"));
        assertFalse(json.has("tool_calls"));
    }

    @Test
    void assistantOmitsEmptyToolCallsReasoningAndToolCallId() {
        ChatCompletionMessage message = ChatCompletionMessage.assistant("hi", null, List.of());
        JsonNode json = mapper.valueToTree(message);

        assertEquals("hi", json.get("content").asText());
        assertFalse(json.has("reasoning_content"));
        assertFalse(json.has("tool_calls"));
        assertFalse(json.has("tool_call_id"));
    }

    @Test
    void toolDefinitionSerializesFunctionSchema() {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", "object");
        ChatCompletionTool tool = new ChatCompletionTool(
                "function",
                new ChatCompletionTool.Function("read_file", "Read a file", parameters));
        JsonNode json = mapper.valueToTree(tool);

        assertEquals("function", json.get("type").asText());
        assertEquals("read_file", json.get("function").get("name").asText());
        assertEquals("Read a file", json.get("function").get("description").asText());
        assertEquals("object", json.get("function").get("parameters").get("type").asText());
    }

    @Test
    void requestSerializesMessagesToolsAndMaxTokens() {
        ChatCompletionRequest request = new ChatCompletionRequest(
                List.of(ChatCompletionMessage.user("hi")), List.of(), 100);
        JsonNode json = mapper.valueToTree(request);

        assertEquals(1, json.get("messages").size());
        assertEquals("hi", json.get("messages").get(0).get("content").asText());
        assertTrue(json.get("tools").isArray());
        assertEquals(0, json.get("tools").size());
        assertEquals(100, json.get("max_tokens").asInt());
    }

    @Test
    void responseSerializesChoiceAndUsage() {
        ChatCompletionResponse response = new ChatCompletionResponse(
                List.of(new ChatCompletionChoice(
                        0,
                        ChatCompletionMessage.assistant("hi", null, List.of()),
                        "stop")),
                new ChatCompletionUsage(1, 2, 3));
        JsonNode json = mapper.valueToTree(response);

        assertEquals(0, json.get("choices").get(0).get("index").asInt());
        assertEquals("hi", json.get("choices").get(0).get("message").get("content").asText());
        assertEquals("stop", json.get("choices").get(0).get("finish_reason").asText());
        assertEquals(1, json.get("usage").get("prompt_tokens").asInt());
        assertEquals(2, json.get("usage").get("completion_tokens").asInt());
        assertEquals(3, json.get("usage").get("total_tokens").asInt());
    }
}
