package io.github.xxxyjade.lumirose.tool.fs;

import io.github.xxxyjade.lumirose.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.exception.ToolException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * 文件工具的工作区边界：把模型给的相对路径解析到工作区内的真实路径。
 */
public final class Workspace {

    private final Path root;

    public Workspace(Path root) {
        if (root == null) {
            throw configurationError("工作区不能为空");
        }
        if (!Files.isDirectory(root)) {
            throw configurationError("工作区不存在或不是目录");
        }
        try {
            this.root = root.toRealPath();
        } catch (IOException e) {
            throw configurationError("工作区不可访问");
        }
    }

    /**
     * 解析一个必须已存在的普通文件，并确认真实路径仍在工作区内。
     */
    public Path existingFile(String relative) {
        Path resolved = resolve(relative);
        if (!Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_FILE_NOT_FOUND, "文件不存在");
        }
        if (!Files.isRegularFile(resolved)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, "目标不是普通文件");
        }
        return realPathInside(resolved);
    }

    /**
     * 解析一个写入目标；目标不存在时要求父目录存在且真实路径仍在工作区内。
     */
    public Path writeTarget(String relative) {
        Path resolved = resolve(relative);
        if (Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(resolved)) {
                throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, "目标不是普通文件");
            }
            return realPathInside(resolved);
        }
        Path parent = resolved.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, "父目录不存在");
        }
        realPathInside(parent);
        return resolved;
    }

    private Path resolve(String relative) {
        if (relative == null || relative.isBlank()) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, "路径不能为空");
        }

        Path path;
        try {
            path = Path.of(relative);
        } catch (InvalidPathException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, "路径格式非法", e);
        }

        if (path.isAbsolute()) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, "不允许绝对路径");
        }

        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, "路径越界");
        }
        return resolved;
    }

    private Path realPathInside(Path path) {
        try {
            Path real = path.toRealPath();
            if (!real.startsWith(root)) {
                throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, "路径越界");
            }
            return real;
        } catch (IOException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, "路径不可访问", e);
        }
    }

    private static ConfigurationException configurationError(String message) {
        return new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, message);
    }
}
