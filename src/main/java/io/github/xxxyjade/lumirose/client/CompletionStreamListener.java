package io.github.xxxyjade.lumirose.client;

/**
 * 流式响应的增量回调。所有回调在调用线程上串行执行，默认实现为空。
 */
public interface CompletionStreamListener {

    CompletionStreamListener NOOP = new CompletionStreamListener() {
    };

    default void onReasoningDelta(String delta) {
    }

    default void onContentDelta(String delta) {
    }
}
