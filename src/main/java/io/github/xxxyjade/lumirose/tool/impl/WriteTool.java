package io.github.xxxyjade.lumirose.tool.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.exception.ToolException;
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.ToolParameters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 写入工作区内 UTF-8 文本文件的内置工具。
 *
 * <p>默认不覆盖已有文件，调用方必须显式传入 {@code overwrite=true}。
 * 写入通过 {@link AtomicWrite} 完成，失败时不会留下部分目标文件。
 */
public final class WriteTool implements Tool {
    private final Workspace workspace;

    /**
     * 创建写入工具。
     *
     * @param workspace 工作区根目录，必须是已存在的目录
     */
    public WriteTool(Path workspace) {
        this.workspace = new Workspace(workspace);
    }

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "写入 UTF-8 文本文件；默认不覆盖";
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
        if (params.content == null) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, name(), "content 不能为空");
        }
        Path path = workspace.resolve(params.path, name());
        if (Files.exists(path) && Files.isDirectory(path)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, name(), "目标是目录");
        }
        if (Files.exists(path) && !params.overwrite) {
            throw new ToolException(LumiRoseErrorCode.TOOL_FILE_EXISTS, name(), "文件已存在");
        }
        AtomicWrite.write(path, params.content, params.overwrite, name());
        int bytes = params.content.getBytes(StandardCharsets.UTF_8).length;
        return "Wrote " + bytes + " bytes to " + params.path + ".";
    }

    /**
     * {@code write_file} 的参数。
     */
    public static final class Params implements ToolParameters {
        @JsonProperty(required = true)
        @JsonPropertyDescription("相对于工作区的文件路径")
        public String path;

        @JsonProperty(required = true)
        @JsonPropertyDescription("要写入的文本")
        public String content;

        @JsonProperty("overwrite")
        @JsonPropertyDescription("是否覆盖已存在文件，默认 false")
        public boolean overwrite;
    }
}
