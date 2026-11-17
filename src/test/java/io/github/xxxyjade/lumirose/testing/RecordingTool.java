package io.github.xxxyjade.lumirose.testing;

import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.args.EmptyParameters;
import io.github.xxxyjade.lumirose.tool.args.ToolParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 测试用工具：记录每次调用，并可按配置返回或抛出。
 */
public final class RecordingTool implements Tool {

    private final String name;
    private final String description;
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());

    private volatile String result = "ok";
    private volatile RuntimeException failure;
    private volatile Runnable beforeExecute;

    public RecordingTool(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void willReturn(String result) {
        this.result = result;
        this.failure = null;
    }

    public void willThrow(RuntimeException failure) {
        this.failure = failure;
    }

    public void beforeExecute(Runnable action) {
        this.beforeExecute = action;
    }

    public List<String> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public ToolParameters parameters() {
        return new EmptyParameters();
    }

    @Override
    public Class<? extends ToolParameters> parametersClass() {
        return EmptyParameters.class;
    }

    @Override
    public String execute(ToolParameters parameters) {
        calls.add(name);
        if (beforeExecute != null) {
            beforeExecute.run();
        }
        if (failure != null) {
            throw failure;
        }
        return result;
    }
}
