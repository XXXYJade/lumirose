package io.github.xxxyjade.lumirose.tool.impl;

import io.github.xxxyjade.lumirose.tool.EmptyParameters;
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.ToolParameters;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 返回当前本地时间的内置工具。
 *
 * <p>输出为带偏移量的 ISO-8601 时间并截断到秒，便于模型直接阅读和解析。
 * 该工具不接收参数。
 */
public final class TimeTool implements Tool {

    @Override
    public String name() {
        return "get_current_time";
    }

    @Override
    public String description() {
        return "返回当前时间";
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
        return ZonedDateTime.now().withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
