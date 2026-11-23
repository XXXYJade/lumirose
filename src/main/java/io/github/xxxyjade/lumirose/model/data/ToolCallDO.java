package io.github.xxxyjade.lumirose.model.data;

import java.time.Instant;

public final class ToolCallDO {
    private final long id;
    private final long messageId;
    private final int callIndex;
    private final String callId;
    private final String callType;
    private final String functionName;
    private final String arguments;
    private final Instant createdAt;

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
