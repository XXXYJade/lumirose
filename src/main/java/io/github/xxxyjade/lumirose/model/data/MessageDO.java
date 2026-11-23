package io.github.xxxyjade.lumirose.model.data;

import io.github.xxxyjade.lumirose.common.enums.MessageRole;

import java.time.Instant;

/**
 * 消息行的持久化形状。
 *
 * <p>{@code active} 表示逻辑可见性；压缩只把旧消息置为 inactive，
 * 不物理删除。{@code toolCallId} 仅对 tool 消息有意义。
 */
public final class MessageDO {
    private final long id;
    private final long conversationId;
    private final MessageRole role;
    private final String content;
    private final String reasoningContent;
    private final String toolCallId;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建消息 DO。
     *
     * @param id               消息 id
     * @param conversationId   所属会话 id
     * @param role             消息角色
     * @param content          正文
     * @param reasoningContent 思考内容
     * @param toolCallId       工具调用 id
     * @param active           是否 active
     * @param createdAt        创建时间
     * @param updatedAt        最后更新时间
     */
    public MessageDO(long id, long conversationId, MessageRole role, String content,
                     String reasoningContent, String toolCallId, boolean active,
                     Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.reasoningContent = reasoningContent;
        this.toolCallId = toolCallId;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long id() {
        return id;
    }

    public long conversationId() {
        return conversationId;
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

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
