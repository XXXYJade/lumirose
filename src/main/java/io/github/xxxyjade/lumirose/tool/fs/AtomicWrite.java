package io.github.xxxyjade.lumirose.tool.fs;

import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.exception.ToolException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 以临时文件 + 原子替换的方式写入文本，避免失败时破坏原文件。
 */
public final class AtomicWrite {

    private AtomicWrite() {
    }

    public static void write(Path target, String content) {
        Path directory = target.getParent();
        Path temp = null;
        try {
            temp = Files.createTempFile(directory, ".lumirose-" + target.getFileName() + "-", ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        } catch (IOException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, "文件写入失败", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // 清理失败不覆盖原始异常
                }
            }
        }
    }
}
