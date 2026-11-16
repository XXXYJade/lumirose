package io.github.xxxyjade.lumirose.testing;

import io.github.xxxyjade.lumirose.agent.StreamControl;
import io.github.xxxyjade.lumirose.agent.StreamListener;
import io.github.xxxyjade.lumirose.model.chat.completion.FinishReason;

import java.util.ArrayList;
import java.util.List;

/**
 * 记录运行事件，并可按测试需要触发取消。
 */
public class StreamRecorder implements StreamListener {

    public final List<String> events = new ArrayList<>();
    public StreamControl control;
    public boolean cancelOnStart;
    public boolean cancelOnContent;
    public boolean cancelOnToolStart;
    public boolean cancelOnTurnEnd;

    @Override
    public void onStart(StreamControl control) {
        this.control = control;
        events.add("start");
        if (cancelOnStart) {
            cancel();
        }
    }

    @Override
    public void onReasoningDelta(String delta) {
        events.add("reasoning:" + delta);
    }

    @Override
    public void onContentDelta(String delta) {
        events.add("content:" + delta);
        if (cancelOnContent) {
            cancel();
        }
    }

    @Override
    public void onToolStart(String toolCallId, String toolName) {
        events.add("toolStart:" + toolCallId + ":" + toolName);
        if (cancelOnToolStart) {
            cancel();
        }
    }

    @Override
    public void onToolEnd(String toolCallId, String toolName, long elapsedMillis, boolean success) {
        events.add("toolEnd:" + toolCallId + ":" + toolName + ":" + success);
    }

    @Override
    public void onTurnEnd(int turn, FinishReason finishReason, int promptTokens, int completionTokens) {
        events.add("turnEnd:" + turn + ":" + finishReason);
        if (cancelOnTurnEnd) {
            cancel();
        }
    }

    protected void cancel() {
        control.cancel();
    }
}
