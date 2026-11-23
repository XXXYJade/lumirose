package io.github.xxxyjade.lumirose.model.entity;

import io.github.xxxyjade.lumirose.common.enums.MessageRole;
import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;

import java.util.List;

/**
 * 会话中的一条消息。
 *
 * <p>消息不可变，列表字段复制为不可变视图。assistant 消息允许
 * {@code content} 为 {@code null}，但正文和 tool calls 不能同时为空；
 * tool 消息必须带 {@code toolCallId}。
 */
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

    /**
     * 创建 system 消息。
     *
     * @param content 消息正文，不能为空白
     * @return system 消息
     */
    public static Message system(String content) {
        return new Message(MessageRole.SYSTEM, requireText(content), null, null, List.of());
    }

    /**
     * 创建 user 消息。
     *
     * @param content 消息正文，不能为空白
     * @return user 消息
     */
    public static Message user(String content) {
        return new Message(MessageRole.USER, requireText(content), null, null, List.of());
    }

    /**
     * 创建 assistant 消息。
     *
     * @param content          正文，空白值按 {@code null} 处理
     * @param reasoningContent 思考内容，空白值按 {@code null} 处理
     * @param toolCalls        工具调用列表，{@code null} 视为空列表
     * @return assistant 消息
     * @throws AgentException 正文和 tool calls 同时为空时抛出 {@code 1002}
     */
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

    /**
     * 创建 tool 结果消息。
     *
     * @param toolCallId 对应的工具调用 id，不能为空
     * @param content    工具结果，{@code null} 转为空字符串
     * @return tool 消息
     * @throws AgentException id 为空时抛出 {@code 1002}
     */
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
