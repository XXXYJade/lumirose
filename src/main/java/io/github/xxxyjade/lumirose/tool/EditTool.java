package io.github.xxxyjade.lumirose.tool;

import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.exception.ToolException;
import io.github.xxxyjade.lumirose.tool.args.EditToolParameters;
import io.github.xxxyjade.lumirose.tool.args.ToolParameters;
import io.github.xxxyjade.lumirose.tool.fs.AtomicWrite;
import io.github.xxxyjade.lumirose.tool.fs.Workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 在工作区内做精确文本替换，要求原文本唯一匹配。
 */
public final class EditTool implements Tool {

    private static final String NAME = "edit_file";

    private final Workspace workspace;

    public EditTool(Path workspace) {
        this.workspace = new Workspace(workspace);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "精确替换文件中的文本";
    }

    @Override
    public ToolParameters parameters() {
        return EditToolParameters.builder().build();
    }

    @Override
    public Class<? extends ToolParameters> parametersClass() {
        return EditToolParameters.class;
    }

    @Override
    public String execute(ToolParameters parameters) {
        if (!(parameters instanceof EditToolParameters editParameters)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, NAME, "参数类型不正确");
        }
        if (editParameters.path() == null || editParameters.path().isBlank()) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, NAME, "path 不能为空");
        }
        if (editParameters.oldString() == null || editParameters.oldString().isEmpty()) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, NAME, "old_string 不能为空");
        }
        if (editParameters.newString() == null) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, NAME, "new_string 不能为 null");
        }

        Path file = workspace.existingFile(editParameters.path());
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, NAME, "文件读取失败", e);
        }

        int matches = countOccurrences(content, editParameters.oldString());
        if (matches == 0) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EDIT_NO_MATCH, NAME, "未找到匹配文本");
        }
        if (matches > 1) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EDIT_NOT_UNIQUE, NAME, "匹配文本不唯一");
        }

        AtomicWrite.write(file, content.replace(editParameters.oldString(), editParameters.newString()));

        return "Replaced 1 occurrence in " + editParameters.path() + ".";
    }

    private static int countOccurrences(String content, String target) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }
}
