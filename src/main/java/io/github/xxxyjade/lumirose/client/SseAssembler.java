package io.github.xxxyjade.lumirose.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xxxyjade.lumirose.exception.ClientException;
import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessage;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionResponseParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionToolCall;
import okio.BufferedSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 逐行解析 SSE 响应，装配成与同步响应相同的对象。
 */
final class SseAssembler {

    private static final String INVALID = "流式响应结构非法";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SseAssembler() {
    }

    static ChatCompletionResponseParam consume(BufferedSource source, CompletionStreamListener listener)
            throws IOException {
        Accumulator accumulator = new Accumulator(listener);
        StringBuilder data = new StringBuilder();

        String line;
        boolean done = false;
        while (!done && (line = source.readUtf8Line()) != null) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                if (data.length() > 0) {
                    String payload = data.toString();
                    data.setLength(0);
                    if ("[DONE]".equals(payload)) {
                        done = true;
                    } else {
                        accumulator.apply(payload);
                    }
                }
                continue;
            }
            if (line.startsWith(":")) {
                continue;
            }
            if (line.startsWith("data:")) {
                String value = line.substring(5);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(value);
            }
        }

        if (!done && data.length() > 0) {
            String payload = data.toString();
            if (!"[DONE]".equals(payload)) {
                accumulator.apply(payload);
            }
        }
        return accumulator.build();
    }

    private static ClientException invalid() {
        return new ClientException(LumiRoseErrorCode.CLIENT_RESPONSE_INVALID, INVALID);
    }

    private static JsonNode read(String payload) {
        try {
            return MAPPER.readTree(payload);
        } catch (Exception e) {
            throw invalid();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static final class Accumulator {

        private final CompletionStreamListener listener;
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final Map<Integer, ToolCallSlot> slots = new TreeMap<>();
        private String finishReason;
        private ChatCompletionResponseParam.Usage usage;

        private Accumulator(CompletionStreamListener listener) {
            this.listener = listener;
        }

        private void apply(String payload) {
            JsonNode root = read(payload);

            JsonNode choices = root.path("choices");
            if (choices.isArray()) {
                for (JsonNode choice : choices) {
                    JsonNode index = choice.get("index");
                    if (index == null || !index.isInt() || index.asInt() != 0) {
                        continue;
                    }
                    JsonNode delta = choice.get("delta");
                    if (delta != null && delta.isObject()) {
                        String text = text(delta, "content");
                        if (text != null && !text.isEmpty()) {
                            content.append(text);
                            listener.onContentDelta(text);
                        }
                        String think = text(delta, "reasoning_content");
                        if (think != null && !think.isEmpty()) {
                            reasoning.append(think);
                            listener.onReasoningDelta(think);
                        }
                        JsonNode toolCalls = delta.get("tool_calls");
                        if (toolCalls != null && toolCalls.isArray()) {
                            for (JsonNode toolCall : toolCalls) {
                                merge(toolCall);
                            }
                        }
                    }
                    JsonNode reason = choice.get("finish_reason");
                    if (reason != null && !reason.isNull()) {
                        finishReason = reason.asText();
                    }
                }
            }

            JsonNode node = root.get("usage");
            if (node != null && !node.isNull()) {
                try {
                    usage = MAPPER.treeToValue(node, ChatCompletionResponseParam.Usage.class);
                } catch (Exception e) {
                    throw invalid();
                }
            }
        }

        private void merge(JsonNode toolCall) {
            JsonNode index = toolCall.get("index");
            if (index == null || !index.isInt()) {
                throw invalid();
            }
            ToolCallSlot slot = slots.computeIfAbsent(index.asInt(), key -> new ToolCallSlot());

            String id = text(toolCall, "id");
            if (id != null) {
                if (slot.id != null && !slot.id.equals(id)) {
                    throw invalid();
                }
                slot.id = id;
            }

            JsonNode function = toolCall.get("function");
            if (function != null && function.isObject()) {
                String name = text(function, "name");
                if (name != null) {
                    if (slot.name != null && !slot.name.equals(name)) {
                        throw invalid();
                    }
                    slot.name = name;
                }
                String arguments = text(function, "arguments");
                if (arguments != null) {
                    slot.arguments.append(arguments);
                }
            }
        }

        private ChatCompletionResponseParam build() {
            if (finishReason == null) {
                throw invalid();
            }

            List<ChatCompletionToolCall> toolCalls = new ArrayList<>();
            for (ToolCallSlot slot : slots.values()) {
                toolCalls.add(ChatCompletionToolCall.builder()
                        .id(slot.id)
                        .type("function")
                        .function(ChatCompletionToolCall.Function.builder()
                                .name(slot.name)
                                .arguments(slot.arguments.toString())
                                .build())
                        .build());
            }

            ChatCompletionMessage message = ChatCompletionMessage.builder()
                    .role("assistant")
                    .content(content.length() == 0 ? null : content.toString())
                    .reasoningContent(reasoning.length() == 0 ? null : reasoning.toString())
                    .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                    .build();

            ChatCompletionResponseParam.Choice choice = ChatCompletionResponseParam.Choice.builder()
                    .index(0)
                    .finishReason(finishReason)
                    .message(message)
                    .build();

            return ChatCompletionResponseParam.builder()
                    .choices(List.of(choice))
                    .usage(usage)
                    .build();
        }
    }

    private static final class ToolCallSlot {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }
}
