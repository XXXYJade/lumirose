package io.github.xxxyjade.lumirose.agent;

/**
 * 一次工具执行的记录。
 */
public record ToolCallRecord(String toolCallId, String toolName, long elapsedMillis) {
}
