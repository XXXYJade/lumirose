package io.github.xxxyjade.lumirose.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xxxyjade.lumirose.common.enums.MessageRole;
import io.github.xxxyjade.lumirose.common.exception.ClientException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionChoice;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionMessage;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionResponse;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionToolCall;
import io.github.xxxyjade.lumirose.model.dto.chat.ChatCompletionUsage;
import okio.BufferedSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * 把 SSE 事件流装配成一个完整的 chat completion 响应。
 *
 * <p>只实现 OpenAI 兼容的 {@code data:} 事件：多行 data 以换行拼接，
 * 空行派发；注释、{@code event}、{@code id} 等其他字段被忽略。
 * {@code [DONE]} 之后停止读取。正文和 reasoning 直接拼接，tool call 按
 * {@code index} 分桶合并，因此分片顺序不影响最终结果。
 *
 * <p>任何结构问题都映射为 {@link LumiRoseErrorCode#CLIENT_RESPONSE_INVALID}；
 * 底层读取失败仍以 {@link IOException} 抛给客户端，由客户端映射为网络错误。
 */
final class SseAccumulator {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SseAccumulator() {
    }

    /**
     * 读取并装配 SSE 流。
     *
     * @param source    响应体数据源
     * @param onContent 每收到一段非空正文时的回调，可为 {@code null}
     * @return 装配后的响应
     * @throws IOException    读取响应体失败
     * @throws ClientException 流结构非法或缺少结束标记
     */
    static ChatCompletionResponse consume(BufferedSource source, Consumer<String> onContent)
            throws IOException {
        State state = new State(onContent == null ? ignored -> { } : onContent);
        StringBuilder data = null;
        while (true) {
            String line = source.readUtf8Line();
            if (line == null) {
                break;
            }
            // okio 可能保留行尾 \r；SSE 允许 CRLF，统一去掉。
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                if (data != null) {
                    process(state, data.toString());
                    data = null;
                    if (state.done) {
                        break;
                    }
                }
                continue;
            }
            if (line.startsWith(":")) {
                // 注释帧，通常用作 keep-alive。
                continue;
            }
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            if ("data".equals(field)) {
                // 同一事件的多行 data 用换行连接，空行时整体派发。
                if (data == null) {
                    data = new StringBuilder();
                } else {
                    data.append('\n');
                }
                data.append(value);
            }
        }
        if (!state.done && data != null) {
            process(state, data.toString());
        }
        if (!state.done || state.finishReason == null || state.finishReason.isBlank()) {
            throw invalid("流式响应缺少结束标记或 finish_reason");
        }
        List<ChatCompletionToolCall> calls = new ArrayList<>();
        for (ToolCallBuilder builder : state.toolCalls.values()) {
            calls.add(builder.build());
        }
        String content = state.content.isEmpty() ? null : state.content.toString();
        String reasoning = state.reasoning.isEmpty() ? null : state.reasoning.toString();
        ChatCompletionMessage message = new ChatCompletionMessage(
                MessageRole.ASSISTANT, content, reasoning, null, calls);
        ChatCompletionChoice choice = new ChatCompletionChoice(0, message, state.finishReason);
        return new ChatCompletionResponse(List.of(choice), state.usage);
    }

    private static void process(State state, String data) {
        if ("[DONE]".equals(data)) {
            state.done = true;
            return;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(data);
        } catch (JsonProcessingException e) {
            throw invalid("流式响应 JSON 非法", e);
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw invalid("流式响应缺少 choices");
        }
        JsonNode first = choices.get(0);
        JsonNode delta = first.path("delta");
        if (delta.isObject()) {
            JsonNode content = delta.get("content");
            if (content != null && !content.isNull()) {
                String text = content.asText();
                state.content.append(text);
                if (!text.isEmpty()) {
                    state.onContent.accept(text);
                }
            }
            JsonNode reasoning = delta.get("reasoning_content");
            if (reasoning != null && !reasoning.isNull()) {
                state.reasoning.append(reasoning.asText());
            }
            JsonNode toolCalls = delta.get("tool_calls");
            if (toolCalls != null && toolCalls.isArray()) {
                for (JsonNode toolCall : toolCalls) {
                    mergeToolCall(state, toolCall);
                }
            }
        }
        JsonNode finishReason = first.get("finish_reason");
        if (finishReason != null && !finishReason.isNull()) {
            state.finishReason = finishReason.asText();
        }
        JsonNode usage = root.get("usage");
        if (usage != null && !usage.isNull()) {
            // usage 可能出现在任意帧，后到的值覆盖先到的值。
            state.usage = parseUsage(usage);
        }
    }

    private static void mergeToolCall(State state, JsonNode node) {
        int index = node.path("index").asInt();
        ToolCallBuilder builder = state.toolCalls.computeIfAbsent(index, ignored -> new ToolCallBuilder());
        String id = text(node, "id");
        if (id != null) {
            if (builder.id != null && !builder.id.equals(id)) {
                throw invalid("tool call id 冲突");
            }
            builder.id = id;
        }
        String type = text(node, "type");
        if (type != null) {
            builder.type = type;
        }
        JsonNode function = node.get("function");
        if (function != null && function.isObject()) {
            String name = text(function, "name");
            if (name != null) {
                if (builder.name != null && !builder.name.equals(name)) {
                    throw invalid("tool call name 冲突");
                }
                builder.name = name;
            }
            String arguments = text(function, "arguments");
            if (arguments != null) {
                // 参数 JSON 被拆成多个片段，必须严格按到达顺序拼接。
                builder.arguments.append(arguments);
            }
        }
    }

    private static ChatCompletionUsage parseUsage(JsonNode usage) {
        return new ChatCompletionUsage(
                integer(usage, "prompt_tokens"),
                integer(usage, "completion_tokens"),
                integer(usage, "total_tokens"));
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static ClientException invalid(String message) {
        return new ClientException(LumiRoseErrorCode.CLIENT_RESPONSE_INVALID, message);
    }

    private static ClientException invalid(String message, Throwable cause) {
        return new ClientException(LumiRoseErrorCode.CLIENT_RESPONSE_INVALID, message, cause);
    }

    private static final class State {
        private final Consumer<String> onContent;
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        // TreeMap 保证最终 tool calls 按 index 升序。
        private final Map<Integer, ToolCallBuilder> toolCalls = new TreeMap<>();
        private ChatCompletionUsage usage;
        private String finishReason;
        private boolean done;

        private State(Consumer<String> onContent) {
            this.onContent = onContent;
        }
    }

    private static final class ToolCallBuilder {
        private String id;
        private String type;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private ChatCompletionToolCall build() {
            if (id == null || id.isBlank() || name == null || name.isBlank()) {
                throw invalid("tool call 结构不完整");
            }
            return new ChatCompletionToolCall(id, type, new ChatCompletionToolCall.Function(
                    name, arguments.toString()));
        }
    }
}
