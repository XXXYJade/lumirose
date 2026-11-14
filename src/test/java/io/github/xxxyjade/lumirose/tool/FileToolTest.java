package io.github.xxxyjade.lumirose.tool;

import io.github.xxxyjade.lumirose.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.exception.ToolException;
import io.github.xxxyjade.lumirose.tool.args.ReadToolParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileToolTest {

    @TempDir
    Path workspace;

    private ReadTool tool;

    @BeforeEach
    void setUp() {
        tool = new ReadTool(workspace);
    }

    @Test
    void tc_f01_workspaceMustBeDirectory() throws Exception {
        Path file = Files.writeString(workspace.resolve("a.txt"), "x");

        var ex = assertThrows(ConfigurationException.class, () -> new ReadTool(file));

        assertEquals(1001, ex.getErrorCode().code());
    }

    @Test
    void tc_f02_readsUtf8Content() throws Exception {
        Files.writeString(workspace.resolve("a.txt"), "第一行\n第二行");

        assertEquals("第一行\n第二行", read("a.txt"));
    }

    @Test
    void tc_f03_missingFileRejected() {
        var ex = assertThrows(ToolException.class, () -> read("missing.txt"));

        assertEquals(4005, ex.getErrorCode().code());
    }

    @Test
    void tc_f04_directoryRejected() throws Exception {
        Files.createDirectory(workspace.resolve("dir"));

        var ex = assertThrows(ToolException.class, () -> read("dir"));

        assertEquals(4004, ex.getErrorCode().code());
    }

    @Test
    void tc_f05_parentTraversalRejected() {
        var ex = assertThrows(ToolException.class, () -> read("../a.txt"));

        assertEquals(4004, ex.getErrorCode().code());
    }

    @Test
    void tc_f06_absolutePathRejected() {
        String absolute = workspace.resolve("a.txt").toAbsolutePath().toString();

        var ex = assertThrows(ToolException.class, () -> read(absolute));

        assertEquals(4004, ex.getErrorCode().code());
    }

    @Test
    void tc_f07_symlinkEscapeRejected() throws Exception {
        Path outside = Files.createTempFile("lumirose-outside", ".txt");
        try {
            Files.writeString(outside, "secret");
            Path link = workspace.resolve("link.txt");
            try {
                Files.createSymbolicLink(link, outside);
            } catch (IOException | UnsupportedOperationException e) {
                assumeTrue(false, "当前环境无法创建符号链接: " + e.getMessage());
            }

            var ex = assertThrows(ToolException.class, () -> read("link.txt"));

            assertEquals(4004, ex.getErrorCode().code());
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void tc_f19_missingPathRejected() {
        var nullPath = assertThrows(ToolException.class,
                () -> tool.execute(ReadToolParameters.builder().path(null).build()));
        assertEquals(4002, nullPath.getErrorCode().code());

        var blankPath = assertThrows(ToolException.class,
                () -> tool.execute(ReadToolParameters.builder().path("  ").build()));
        assertEquals(4002, blankPath.getErrorCode().code());
    }

    @Test
    void tc_f20_windowsAbsoluteAndUncRejected() {
        for (String path : List.of("C:\\x.txt", "\\\\server\\share\\x.txt")) {
            var ex = assertThrows(ToolException.class, () -> read(path));
            assertEquals(4004, ex.getErrorCode().code());
        }
    }

    private String read(String path) {
        return tool.execute(ReadToolParameters.builder().path(path).build());
    }
}
