package io.github.xxxyjade.lumirose.tool.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.exception.ToolException;
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.ToolParameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取工作区内 UTF-8 文本文件的内置工具。
 *
 * <p>整文件读取，不提供行号或分页；路径必须位于构造时指定的工作区内。
 */
public final class ReadTool implements Tool {
    private final Workspace workspace;

    /**
     * 创建读取工具。
     *
     * @param workspace 工作区根目录，必须是已存在的目录
     */
    public ReadTool(Path workspace) {
        this.workspace = new Workspace(workspace);
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "读取 UTF-8 文本文件";
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
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, name(), "文件读取失败", e);
        }
    }

    /**
     * {@code read_file} 的参数。
     */
    public static final class Params implements ToolParameters {
        @JsonProperty(required = true)
        @JsonPropertyDescription("相对于工作区的文件路径")
        public String path;
    }
}
