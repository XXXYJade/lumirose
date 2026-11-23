package io.github.xxxyjade.lumirose.tool.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.exception.ToolException;
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.ToolParameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 删除工作区内普通文件的内置工具。
 *
 * <p>只删除文件，目录会返回 4004；删除不存在的路径返回 4005，
 * 不把 {@code deleteIfExists} 的静默语义暴露给模型。
 */
public final class DeleteTool implements Tool {
    private final Workspace workspace;

    /**
     * 创建删除工具。
     *
     * @param workspace 工作区根目录，必须是已存在的目录
     */
    public DeleteTool(Path workspace) {
        this.workspace = new Workspace(workspace);
    }

    @Override
    public String name() {
        return "delete_file";
    }

    @Override
    public String description() {
        return "删除普通文件";
    }

    @Override
    public ToolParameters parameters() {
        return new Params();
    }

    @Override
    public Class<? extends ToolParameters> parametersClass() {
        return Params.class;
    }

    @Override
    public String execute(ToolParameters parameters) {
        Params params = (Params) parameters;
        Path path = workspace.resolve(params.path, name());
        if (!Files.exists(path)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_FILE_NOT_FOUND, name(), "文件不存在");
        }
        if (Files.isDirectory(path)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, name(), "目标是目录");
        }
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, name(), "文件删除失败", e);
        }
        return "Deleted " + params.path + ".";
    }

    /**
     * {@code delete_file} 的参数。
     */
    public static final class Params implements ToolParameters {
        @JsonProperty(required = true)
        @JsonPropertyDescription("相对于工作区的文件路径")
        public String path;
    }
}
