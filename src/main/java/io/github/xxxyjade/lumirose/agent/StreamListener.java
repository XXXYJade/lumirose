package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.model.chat.completion.FinishReason;

/**
 * 流式运行的事件回调。所有回调在调用线程上按发生顺序串行执行。
 */
public interface StreamListener {

    default void onStart(StreamControl control) {
    }

    default void onReasoningDelta(String delta) {
    }

    default void onContentDelta(String delta) {
    }

    default void onToolStart(String toolCallId, String toolName) {
    }

    default void onToolEnd(String toolCallId, String toolName, long elapsedMillis, boolean success) {
    }

    default void onTurnEnd(int turn, FinishReason finishReason, int promptTokens, int completionTokens) {
    }
}
