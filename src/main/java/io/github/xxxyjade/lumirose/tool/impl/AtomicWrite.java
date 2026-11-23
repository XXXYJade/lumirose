package io.github.xxxyjade.lumirose.tool.impl;

import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.exception.ToolException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 在同目录临时文件上完成写入，再用 move 替换目标。
 *
 * <p>这样写入中途失败时不会留下半截目标文件；临时文件在失败路径上会被清理。
 * 文件系统不支持原子 move 时退化为普通 move，仍保证目标要么是旧内容、
 * 要么是完整的新内容。
 */
final class AtomicWrite {
    private AtomicWrite() {
    }

    /**
     * 写入 UTF-8 文本。
     *
     * @param target    目标文件
     * @param content   文本内容
     * @param overwrite 是否允许替换已存在的目标
     * @param toolName  出错时写入异常的工具名
     * @throws ToolException 目标已存在且不允许覆盖（4006），或写入失败（4003）
     */
    static void write(Path target, String content, boolean overwrite, String toolName) {
        Path parent = target.getParent();
        Path temp = null;
        try {
            temp = Files.createTempFile(parent, ".lumirose-", ".tmp");
            Files.write(temp, content.getBytes(StandardCharsets.UTF_8));
            if (overwrite) {
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException | UnsupportedOperationException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException | UnsupportedOperationException e) {
                    Files.move(temp, target);
                }
            }
            temp = null;
        } catch (FileAlreadyExistsException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_FILE_EXISTS, toolName, "文件已存在", e);
        } catch (IOException | SecurityException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, toolName, "文件写入失败", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // 临时文件清理失败不覆盖原始错误。
                }
            }
        }
    }
}
