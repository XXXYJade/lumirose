package io.github.xxxyjade.lumirose.common.result;

public interface LumiRoseResult {
    String content();

    int promptTokens();

    int completionTokens();

    Iterable<String> deltas();

    void cancel();
}
