package io.github.xxxyjade.lumirose.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.xxxyjade.lumirose.common.enums.MessageRole;

import java.util.List;

/**
 * chat completions 协议中的单条消息。
 *
 * <p>{@code content} 始终参与序列化，assistant 只有 tool calls 时会输出
 * {@code "content": null}；reasoning、tool_call_id 和 tool_calls 为空时省略。
 */
public final class ChatCompletionMessage {
    private final MessageRole role;
    private final String content;
    private final String reasoningContent;
    private final String toolCallId;
    private final List<ChatCompletionToolCall> toolCalls;

    /**
     * 创建任意角色的消息。
     *
     * <p>字段组合的语义由角色决定；静态工厂是更安全的入口。
     *
     * @param role             消息角色
     * @param content          正文，可为 {@code null}
     * @param reasoningContent 思考内容，可为 {@code null}
     * @param toolCallId       工具调用 id，可为 {@code null}
     * @param toolCalls        工具调用列表，{@code null} 视为空列表
     */
    public ChatCompletionMessage(MessageRole role, String content, String reasoningContent,
                                 String toolCallId, List<ChatCompletionToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.reasoningContent = reasoningContent;
        this.toolCallId = toolCallId;
        this.toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /**
     * 创建 system 消息。
     *
     * @param content 正文
     * @return system 消息
     */
    public static ChatCompletionMessage system(String content) {
        return new ChatCompletionMessage(MessageRole.SYSTEM, content, null, null, List.of());
    }

    /**
     * 创建 user 消息。
     *
     * @param content 正文
     * @return user 消息
     */
    public static ChatCompletionMessage user(String content) {
        return new ChatCompletionMessage(MessageRole.USER, content, null, null, List.of());
    }

    /**
     * 创建 assistant 消息。
     *
     * @param content          正文，可为 {@code null}
     * @param reasoningContent 思考内容，可为 {@code null}
     * @param toolCalls        工具调用，可为 {@code null}
     * @return assistant 消息
     */
    public static ChatCompletionMessage assistant(String content, String reasoningContent,
                                                  List<ChatCompletionToolCall> toolCalls) {
        return new ChatCompletionMessage(MessageRole.ASSISTANT, content, reasoningContent, null, toolCalls);
    }

    /**
     * 创建 tool 结果消息。
     *
     * @param toolCallId 对应的工具调用 id
     * @param content    工具结果
     * @return tool 消息
     */
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
