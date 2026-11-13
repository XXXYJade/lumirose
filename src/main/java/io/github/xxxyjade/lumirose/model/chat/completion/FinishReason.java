package io.github.xxxyjade.lumirose.model.chat.completion;

/**
 * 模型结束本轮生成的原因，对应响应中的 {@code finish_reason}。
 */
public enum FinishReason {

    STOP,
    LENGTH,
    TOOL_CALLS,
    CONTENT_FILTER,
    INSUFFICIENT_SYSTEM_RESOURCE,
    UNKNOWN;

    /**
     * 把线缆上的字符串映射为枚举，未知值与 null 归 {@link #UNKNOWN}。
     */
    public static FinishReason from(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        return switch (raw) {
            case "stop" -> STOP;
            case "length" -> LENGTH;
            case "tool_calls" -> TOOL_CALLS;
            case "content_filter" -> CONTENT_FILTER;
            case "insufficient_system_resource" -> INSUFFICIENT_SYSTEM_RESOURCE;
            default -> UNKNOWN;
        };
    }
}
