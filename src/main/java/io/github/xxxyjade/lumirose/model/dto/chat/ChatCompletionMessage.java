package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.xxxyjade.lumirose.common.enums.MessageRole;

import java.util.List;

public final class ChatCompletionMessage {
    private final MessageRole role;
    private final String content;
    private final String reasoningContent;
    private final String toolCallId;
    private final List<ChatCompletionToolCall> toolCalls;

    public ChatCompletionMessage(MessageRole role, String content, String reasoningContent,
                                 String toolCallId, List<ChatCompletionToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.reasoningContent = reasoningContent;
        this.toolCallId = toolCallId;
        this.toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static ChatCompletionMessage system(String content) {
        return new ChatCompletionMessage(MessageRole.SYSTEM, content, null, null, List.of());
    }

    public static ChatCompletionMessage user(String content) {
        return new ChatCompletionMessage(MessageRole.USER, content, null, null, List.of());
    }

    public static ChatCompletionMessage assistant(String content, String reasoningContent,
                                                  List<ChatCompletionToolCall> toolCalls) {
        return new ChatCompletionMessage(MessageRole.ASSISTANT, content, reasoningContent, null, toolCalls);
    }

    public static ChatCompletionMessage tool(String toolCallId, String content) {
        return new ChatCompletionMessage(MessageRole.TOOL, content, null, toolCallId, List.of());
    }

    @JsonProperty("role")
    public MessageRole role() {
        return role;
    }

    @JsonProperty("content")
    public String content() {
        return content;
    }

    @JsonProperty("reasoning_content")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String reasoningContent() {
        return reasoningContent;
    }

    @JsonProperty("tool_call_id")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String toolCallId() {
        return toolCallId;
    }

    @JsonProperty("tool_calls")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ChatCompletionToolCall> toolCalls() {
        return toolCalls;
    }
}
