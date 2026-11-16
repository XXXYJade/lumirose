package io.github.xxxyjade.lumirose.agent;

import java.util.List;

/**
 * 一次运行的不可变结果快照。
 */
public final class RunResult {

    private final String content;
    private final int turns;
    private final long elapsedMillis;
    private final int promptTokens;
    private final int completionTokens;
    private final List<ToolCallRecord> toolCalls;

    RunResult(String content,
              int turns,
              long elapsedMillis,
              int promptTokens,
              int completionTokens,
              List<ToolCallRecord> toolCalls) {
        this.content = content;
        this.turns = turns;
        this.elapsedMillis = elapsedMillis;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.toolCalls = List.copyOf(toolCalls);
    }

    public String content() {
        return content;
    }

    public int turns() {
        return turns;
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    public int promptTokens() {
        return promptTokens;
    }

    public int completionTokens() {
        return completionTokens;
    }

    public List<ToolCallRecord> toolCalls() {
        return toolCalls;
    }
}
