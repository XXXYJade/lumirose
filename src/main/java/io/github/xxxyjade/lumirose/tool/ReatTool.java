package io.github.xxxyjade.lumirose.tool;

import io.github.xxxyjade.lumirose.tool.args.ReadToolParameters;
import io.github.xxxyjade.lumirose.tool.args.ToolParameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReatTool implements Tool {

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "读取文件内容";
    }

    @Override
    public ReadToolParameters parameters() {
        return ReadToolParameters.builder().build();
    }

    @Override
    public Class<ReadToolParameters> parametersClass() {
        return ReadToolParameters.class;
    }

    @Override
    public String execute(ToolParameters parameters) throws IOException {
        if (parameters instanceof ReadToolParameters readToolParameters) {
            return Files.readString(Path.of(readToolParameters.path()));
        }
        // TODO 抛出自定义异常
        throw new IOException("");
    }
}
