package io.github.xxxyjade.lumirose.tool;

import io.github.xxxyjade.lumirose.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.exception.ToolException;
import io.github.xxxyjade.lumirose.tool.args.DeleteToolParameters;
import io.github.xxxyjade.lumirose.tool.args.EditToolParameters;
import io.github.xxxyjade.lumirose.tool.args.ReadToolParameters;
import io.github.xxxyjade.lumirose.tool.args.WriteToolParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void tc_f08_writesNewFile() throws Exception {
        String result = write("b.txt", "hello", false);

        assertEquals("hello", Files.readString(workspace.resolve("b.txt")));
        assertEquals("Wrote 5 bytes to b.txt.", result);
    }

    @Test
    void tc_f09_existingFileWithoutOverwriteRejected() throws Exception {
        Files.writeString(workspace.resolve("a.txt"), "old");

        var ex = assertThrows(ToolException.class, () -> write("a.txt", "new", false));

        assertEquals(4006, ex.getErrorCode().code());
        assertEquals("old", Files.readString(workspace.resolve("a.txt")));
    }

    @Test
    void tc_f10_overwriteReplacesContent() throws Exception {
        Files.writeString(workspace.resolve("a.txt"), "old");

        write("a.txt", "new", true);

        assertEquals("new", Files.readString(workspace.resolve("a.txt")));
    }

    @Test
    void tc_f11_missingParentRejected() {
        var ex = assertThrows(ToolException.class, () -> write("no/dir/b.txt", "x", false));

        assertEquals(4004, ex.getErrorCode().code());
    }

    @Test
    void tc_f18_noTempFileLeftBehind() throws Exception {
        write("b.txt", "x", true);

        try (var stream = Files.list(workspace)) {
            assertEquals(List.of("b.txt"),
                    stream.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void tc_f12_noMatchRejected() throws Exception {
        Files.writeString(workspace.resolve("a.txt"), "foo bar");

        var ex = assertThrows(ToolException.class, () -> edit("a.txt", "baz", "x"));

        assertEquals(4007, ex.getErrorCode().code());
    }

    @Test
    void tc_f13_multipleMatchesRejected() throws Exception {
        Files.writeString(workspace.resolve("a.txt"), "foo foo");

        var ex = assertThrows(ToolException.class, () -> edit("a.txt", "foo", "bar"));

        assertEquals(4008, ex.getErrorCode().code());
        assertEquals("foo foo", Files.readString(workspace.resolve("a.txt")));
    }

    @Test
    void tc_f14_singleMatchReplaced() throws Exception {
        Files.writeString(workspace.resolve("a.txt"), "foo bar baz");

        String result = edit("a.txt", "bar", "qux");

        assertEquals("Replaced 1 occurrence in a.txt.", result);
        assertEquals("foo qux baz", Files.readString(workspace.resolve("a.txt")));
    }

    @Test
    void tc_f15_deletesFile() throws Exception {
        Path file = Files.writeString(workspace.resolve("a.txt"), "x");

        String result = delete("a.txt");

        assertFalse(Files.exists(file));
        assertEquals("Deleted a.txt.", result);
    }

    @Test
    void tc_f16_directoryRejected() throws Exception {
        Files.createDirectory(workspace.resolve("dir"));

        var ex = assertThrows(ToolException.class, () -> delete("dir"));

        assertEquals(4004, ex.getErrorCode().code());
    }

    @Test
    void tc_f17_missingFileRejected() {
        var ex = assertThrows(ToolException.class, () -> delete("missing.txt"));

        assertEquals(4005, ex.getErrorCode().code());
    }

    private String delete(String path) {
        return new DeleteTool(workspace).execute(DeleteToolParameters.builder().path(path).build());
    }

    private String edit(String path, String oldString, String newString) {
        return new EditTool(workspace).execute(EditToolParameters.builder()
                .path(path)
                .oldString(oldString)
                .newString(newString)
                .build());
    }

    private String write(String path, String content, boolean overwrite) {
        return new WriteTool(workspace).execute(WriteToolParameters.builder()
                .path(path)
                .content(content)
                .overwrite(overwrite)
                .build());
    }

    private String read(String path) {
        return tool.execute(ReadToolParameters.builder().path(path).build());
    }
}
