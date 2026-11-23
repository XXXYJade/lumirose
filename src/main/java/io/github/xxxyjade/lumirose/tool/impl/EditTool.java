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
 * 按精确文本替换文件内容的内置工具。
 *
 * <p>{@code old_string} 必须在文件中恰好出现一次：未匹配返回 4007，
 * 多次匹配返回 4008，避免模型在歧义位置做出意外修改。
 * 替换通过 {@link AtomicWrite} 写回，失败时保留原文件。
 */
public final class EditTool implements Tool {
    private final Workspace workspace;

    /**
     * 创建编辑工具。
     *
     * @param workspace 工作区根目录，必须是已存在的目录
     */
    public EditTool(Path workspace) {
        this.workspace = new Workspace(workspace);
    }

    @Override
    public String name() {
        return "edit_file";
    }

    @Override
    public String description() {
        return "替换文件中恰好出现一次的文本";
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
        if (params.oldString == null || params.oldString.isEmpty()
                || params.newString == null) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, name(), "编辑参数不能为空");
        }
        Path path = workspace.resolve(params.path, name());
        if (!Files.exists(path)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_FILE_NOT_FOUND, name(), "文件不存在");
        }
        if (Files.isDirectory(path)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, name(), "目标是目录");
        }
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, name(), "文件读取失败", e);
        }
        int first = content.indexOf(params.oldString);
        if (first < 0) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EDIT_NO_MATCH, name(), "编辑目标未匹配");
        }
        int second = content.indexOf(params.oldString, first + params.oldString.length());
        if (second >= 0) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EDIT_NOT_UNIQUE, name(), "编辑目标不唯一");
        }
        String updated = content.substring(0, first) + params.newString
                + content.substring(first + params.oldString.length());
        AtomicWrite.write(path, updated, true, name());
        return "Replaced 1 occurrence in " + params.path + ".";
    }

    /**
     * {@code edit_file} 的参数。
     */
    public static final class Params implements ToolParameters {
        @JsonProperty(required = true)
        @JsonPropertyDescription("相对于工作区的文件路径")
        public String path;

        @JsonProperty(value = "old_string", required = true)
        @JsonPropertyDescription("要替换的原文本，必须恰好出现一次")
        public String oldString;

        @JsonProperty(value = "new_string", required = true)
        @JsonPropertyDescription("替换后的文本")
        public String newString;
    }
}
