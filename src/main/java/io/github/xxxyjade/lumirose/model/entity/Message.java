package io.github.xxxyjade.lumirose.model.entity;

import io.github.xxxyjade.lumirose.common.enums.MessageRole;
import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;

import java.util.List;

public final class Message {
    private final MessageRole role;
    private final String content;
    private final String reasoningContent;
    private final String toolCallId;
    private final List<ToolCall> toolCalls;

    private Message(MessageRole role, String content, String reasoningContent,
                    String toolCallId, List<ToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.reasoningContent = reasoningContent;
        this.toolCallId = toolCallId;
        this.toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static Message system(String content) {
        return new Message(MessageRole.SYSTEM, requireText(content), null, null, List.of());
    }

    public static Message user(String content) {
        return new Message(MessageRole.USER, requireText(content), null, null, List.of());
    }

    public static Message assistant(String content, String reasoningContent, List<ToolCall> toolCalls) {
        List<ToolCall> calls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        String text = content == null || content.isBlank() ? null : content;
        if (text == null && calls.isEmpty()) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID,
                    "assistant 的 content 与 toolCalls 不能同时为空");
        }
        String reasoning = reasoningContent == null || reasoningContent.isBlank() ? null : reasoningContent;
        return new Message(MessageRole.ASSISTANT, text, reasoning, null, calls);
    }

    public static Message tool(String toolCallId, String content) {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "toolCallId 不能为空");
        }
        return new Message(MessageRole.TOOL, content == null ? "" : content, null, toolCallId, List.of());
    }

    private static String requireText(String content) {
        if (content == null || content.isBlank()) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "content 不能为空");
        }
        return content;
    }

    public MessageRole role() {
        return role;
    }

    public String content() {
        return content;
    }

    public String reasoningContent() {
        return reasoningContent;
    }

    public String toolCallId() {
        return toolCallId;
    }

    public List<ToolCall> toolCalls() {
        return toolCalls;
    }
}
