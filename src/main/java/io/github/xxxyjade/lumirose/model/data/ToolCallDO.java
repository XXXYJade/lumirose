package io.github.xxxyjade.lumirose.model.data;

import java.time.Instant;

/**
 * 工具调用行的持久化形状。
 *
 * <p>通过 {@code messageId} 挂到 assistant 消息，{@code callIndex}
 * 保留模型返回的顺序。
 */
public final class ToolCallDO {
    private final long id;
    private final long messageId;
    private final int callIndex;
    private final String callId;
    private final String callType;
    private final String functionName;
    private final String arguments;
    private final Instant createdAt;

    /**
     * 创建工具调用 DO。
     *
     * @param id           行 id
     * @param messageId    所属消息 id
     * @param callIndex    调用顺序
     * @param callId       模型返回的调用 id
     * @param callType     调用类型
     * @param functionName 函数名
     * @param arguments    原始 JSON 参数
     * @param createdAt    创建时间
     */
    public ToolCallDO(long id, long messageId, int callIndex, String callId,
                      String callType, String functionName, String arguments, Instant createdAt) {
        this.id = id;
        this.messageId = messageId;
        this.callIndex = callIndex;
        this.callId = callId;
        this.callType = callType;
        this.functionName = functionName;
        this.arguments = arguments;
        this.createdAt = createdAt;
    }

    public long id() {
        return id;
    }

    public long messageId() {
        return messageId;
    }

    public int callIndex() {
        return callIndex;
    }

    public String callId() {
        return callId;
    }

    public String callType() {
        return callType;
    }

    public String functionName() {
        return functionName;
    }

    public String arguments() {
        return arguments;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
