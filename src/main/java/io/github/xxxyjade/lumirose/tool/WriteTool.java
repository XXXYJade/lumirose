package io.github.xxxyjade.lumirose.tool;

import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.exception.ToolException;
import io.github.xxxyjade.lumirose.tool.args.ToolParameters;
import io.github.xxxyjade.lumirose.tool.args.WriteToolParameters;
import io.github.xxxyjade.lumirose.tool.fs.AtomicWrite;
import io.github.xxxyjade.lumirose.tool.fs.Workspace;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 创建工作区内文件，已存在时需要显式覆盖。
 */
public final class WriteTool implements Tool {

    private static final String NAME = "write_file";

    private final Workspace workspace;

    public WriteTool(Path workspace) {
        this.workspace = new Workspace(workspace);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "创建或覆盖文件";
    }

    @Override
    public ToolParameters parameters() {
        return WriteToolParameters.builder().build();
    }

    @Override
    public Class<? extends ToolParameters> parametersClass() {
        return WriteToolParameters.class;
    }

    @Override
    public String execute(ToolParameters parameters) {
        if (!(parameters instanceof WriteToolParameters writeParameters)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, NAME, "参数类型不正确");
        }
        if (writeParameters.path() == null || writeParameters.path().isBlank()) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, NAME, "path 不能为空");
        }
        if (writeParameters.content() == null) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, NAME, "content 不能为空");
        }

        Path target = workspace.writeTarget(writeParameters.path());
        if (Files.exists(target) && !writeParameters.overwrite()) {
            throw new ToolException(LumiRoseErrorCode.TOOL_FILE_EXISTS, NAME, "文件已存在");
        }

        AtomicWrite.write(target, writeParameters.content());

        int bytes = writeParameters.content().getBytes(StandardCharsets.UTF_8).length;
        return "Wrote " + bytes + " bytes to " + writeParameters.path() + ".";
    }
}
