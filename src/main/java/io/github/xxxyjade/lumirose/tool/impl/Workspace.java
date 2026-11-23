package io.github.xxxyjade.lumirose.tool.impl;

import io.github.xxxyjade.lumirose.common.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.exception.ToolException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * 文件工具的工作区根目录与路径解析。
 *
 * <p>所有工具路径都相对于构造时传入的根目录。解析分两步防护：先对规范化后的
 * 相对路径做前缀检查，再对已存在的目标（或最近存在的父目录）取 real path 复查，
 * 因此 {@code ..} 越界和指向根目录之外的符号链接都会被拒绝。
 */
final class Workspace {
    private final Path root;

    /**
     * 创建并规范化工作区。
     *
     * @param workspace 必须已存在的目录
     * @throws ConfigurationException 路径为空、不是目录或无法解析
     */
    Workspace(Path workspace) {
        if (workspace == null) {
            throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "workspace 不能为空");
        }
        try {
            Path absolute = workspace.toAbsolutePath().normalize();
            if (!Files.isDirectory(absolute)) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID,
                        "workspace 必须是已存在的目录");
            }
            // 保存 real path，后续所有目标都以它为前缀基准。
            this.root = absolute.toRealPath();
        } catch (IOException e) {
            throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID,
                    "workspace 无法解析", e);
        }
    }

    /**
     * 把模型给出的相对路径解析为工作区内的绝对路径。
     *
     * @param rawPath  相对路径
     * @param toolName 出错时写入异常的工具名
     * @return 工作区内的绝对路径
     * @throws ToolException 路径为空、绝对、非法或越出工作区
     */
    Path resolve(String rawPath, String toolName) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, toolName, "路径不能为空");
        }
        Path relative;
        try {
            relative = Path.of(rawPath);
        } catch (InvalidPathException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, toolName, "路径非法", e);
        }
        if (relative.isAbsolute()) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, toolName, "不允许绝对路径");
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, toolName, "路径越界");
        }
        try {
            if (Files.exists(resolved)) {
                Path real = resolved.toRealPath();
                if (!real.startsWith(root)) {
                    throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, toolName, "路径越界");
                }
                return real;
            }
            // 目标尚不存在时，检查最近存在的父目录，防止通过符号链接目录逃逸。
            Path parent = resolved.getParent();
            if (parent != null && Files.exists(parent)) {
                Path realParent = parent.toRealPath();
                if (!realParent.startsWith(root)) {
                    throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, toolName, "路径越界");
                }
            }
            return resolved;
        } catch (IOException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_PATH_INVALID, toolName, "路径非法", e);
        }
    }
}
