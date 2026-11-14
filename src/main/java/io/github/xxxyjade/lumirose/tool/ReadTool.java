package io.github.xxxyjade.lumirose.tool;

import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.exception.ToolException;
import io.github.xxxyjade.lumirose.tool.args.ReadToolParameters;
import io.github.xxxyjade.lumirose.tool.args.ToolParameters;
import io.github.xxxyjade.lumirose.tool.fs.Workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取工作区内的 UTF-8 文本文件。
 */
public final class ReadTool implements Tool {

    private static final String NAME = "read_file";

    private final Workspace workspace;

    public ReadTool(Path workspace) {
        this.workspace = new Workspace(workspace);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "读取文件内容";
    }

    @Override
    public ToolParameters parameters() {
        return ReadToolParameters.builder().build();
    }

    @Override
    public Class<? extends ToolParameters> parametersClass() {
        return ReadToolParameters.class;
    }

    @Override
    public String execute(ToolParameters parameters) {
        if (!(parameters instanceof ReadToolParameters readParameters)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, NAME, "参数类型不正确");
        }
        if (readParameters.path() == null || readParameters.path().isBlank()) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, NAME, "path 不能为空");
        }

        Path file = workspace.existingFile(readParameters.path());
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, NAME, "文件读取失败", e);
        }
    }
}
