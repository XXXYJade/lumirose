package io.github.xxxyjade.lumirose.model.data;

import java.time.Instant;

/**
 * 会话行的持久化形状。
 *
 * <p>只用于仓储实现，不参与业务逻辑；时间戳由仓储写入时生成。
 */
public final class ConversationDO {
    private final long id;
    private final int contextTokens;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建会话 DO。
     *
     * @param id            会话 id
     * @param contextTokens 上下文 token 数
     * @param createdAt     创建时间
     * @param updatedAt     最后更新时间
     */
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
