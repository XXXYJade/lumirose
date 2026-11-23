package io.github.xxxyjade.lumirose.model.data;

import java.time.Instant;

public final class ConversationDO {
    private final long id;
    private final int contextTokens;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ConversationDO(long id, int contextTokens, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.contextTokens = contextTokens;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long id() {
        return id;
    }

    public int contextTokens() {
        return contextTokens;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
