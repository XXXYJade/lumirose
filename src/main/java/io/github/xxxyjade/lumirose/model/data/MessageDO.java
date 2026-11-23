package io.github.xxxyjade.lumirose.model.data;

import io.github.xxxyjade.lumirose.common.enums.MessageRole;

import java.time.Instant;

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
