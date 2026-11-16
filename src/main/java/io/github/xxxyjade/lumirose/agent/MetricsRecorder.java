package io.github.xxxyjade.lumirose.agent;

/**
 * 遥测 SPI。全部回调同步执行，默认实现为空；实现抛出的运行时异常不会影响运行。
 */
public interface MetricsRecorder {

    MetricsRecorder NOOP = new MetricsRecorder() {
    };

    default void onRequest(int turn, long elapsedMillis, boolean success) {
    }

    default void onRetry(int attempt, long delayMillis, String errorCode) {
    }

    default void onUsage(int promptTokens, int completionTokens) {
    }

    default void onTool(String toolName, long elapsedMillis, boolean success) {
    }
}
