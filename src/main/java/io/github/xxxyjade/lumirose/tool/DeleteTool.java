package io.github.xxxyjade.lumirose.tool;

import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.exception.ToolException;
import io.github.xxxyjade.lumirose.tool.args.DeleteToolParameters;
import io.github.xxxyjade.lumirose.tool.args.ToolParameters;
import io.github.xxxyjade.lumirose.tool.fs.Workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 删除工作区内的普通文件。
 */
public final class DeleteTool implements Tool {

    private static final String NAME = "delete_file";

    private final Workspace workspace;

    public DeleteTool(Path workspace) {
        this.workspace = new Workspace(workspace);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "删除文件";
    }

    @Override
    public ToolParameters parameters() {
        return DeleteToolParameters.builder().build();
    }

    @Override
    public Class<? extends ToolParameters> parametersClass() {
        return DeleteToolParameters.class;
    }

    @Override
    public String execute(ToolParameters parameters) {
        if (!(parameters instanceof DeleteToolParameters deleteParameters)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, NAME, "参数类型不正确");
        }
        if (deleteParameters.path() == null || deleteParameters.path().isBlank()) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, NAME, "path 不能为空");
        }

        Path file = workspace.existingFile(deleteParameters.path());
        try {
            Files.delete(file);
        } catch (IOException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, NAME, "文件删除失败", e);
        }

        return "Deleted " + deleteParameters.path() + ".";
    }
}
