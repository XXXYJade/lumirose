package io.github.xxxyjade.lumirose.tool;

import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseException;
import io.github.xxxyjade.lumirose.common.exception.ToolException;
import io.github.xxxyjade.lumirose.tool.impl.DeleteTool;
import io.github.xxxyjade.lumirose.tool.impl.EditTool;
import io.github.xxxyjade.lumirose.tool.impl.ReadTool;
import io.github.xxxyjade.lumirose.tool.impl.WriteTool;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置文件工具在临时工作区中的成功路径、错误码和越界防护测试。
 */
class FileToolTest {

    @TempDir
    Path tempDir;

    private Path workspace;
    private Path outside;

    @BeforeEach
    void setUp() throws IOException {
        workspace = Files.createDirectories(tempDir.resolve("workspace"));
        outside = Files.createDirectories(tempDir.resolve("outside"));
    }

    @Test
    void toolsExposeStableNamesAndParameterTypes() {
        assertEquals("read_file", readTool().name());
        assertEquals("write_file", writeTool().name());
        assertEquals("edit_file", editTool().name());
        assertEquals("delete_file", deleteTool().name());

        assertEquals(ReadTool.Params.class, readTool().parametersClass());
        assertEquals(WriteTool.Params.class, writeTool().parametersClass());
        assertEquals(EditTool.Params.class, editTool().parametersClass());
        assertEquals(DeleteTool.Params.class, deleteTool().parametersClass());
    }

    @Test
    void readReturnsUtf8Content() throws IOException {
        Files.writeString(workspace.resolve("note.txt"), "你好\nworld", StandardCharsets.UTF_8);

        assertEquals("你好\nworld", readTool().execute(readParams("note.txt")));
    }

    @Test
    void readRejectsMissingFile() {
        assertToolError(LumiRoseErrorCode.TOOL_FILE_NOT_FOUND,
                () -> readTool().execute(readParams("missing.txt")));
    }

    @Test
    void readRejectsDirectory() throws IOException {
        Files.createDirectories(workspace.resolve("dir"));

        assertToolError(LumiRoseErrorCode.TOOL_PATH_INVALID,
                () -> readTool().execute(readParams("dir")));
    }

    @Test
    void readRejectsBlankPath() {
        assertToolError(LumiRoseErrorCode.TOOL_PATH_INVALID,
                () -> readTool().execute(readParams("  ")));
        assertToolError(LumiRoseErrorCode.TOOL_PATH_INVALID,
                () -> readTool().execute(readParams(null)));
    }

    @Test
    void writeCreatesUtf8File() throws IOException {
        String content = "你好 lumirose";

        String message = writeTool().execute(writeParams("a.txt", content, false));

        assertEquals(content, Files.readString(workspace.resolve("a.txt"), StandardCharsets.UTF_8));
        int bytes = content.getBytes(StandardCharsets.UTF_8).length;
        assertEquals("Wrote " + bytes + " bytes to a.txt.", message);
    }

    @Test
    void writeRefusesExistingFileWithoutOverwrite() throws IOException {
        Files.writeString(workspace.resolve("a.txt"), "old");

        assertToolError(LumiRoseErrorCode.TOOL_FILE_EXISTS,
                () -> writeTool().execute(writeParams("a.txt", "new", false)));

        assertEquals("old", Files.readString(workspace.resolve("a.txt")));
        assertEquals(List.of(), temporaryFiles());
    }

    @Test
    void writeOverwritesWhenRequested() throws IOException {
        Files.writeString(workspace.resolve("a.txt"), "old");

        writeTool().execute(writeParams("a.txt", "new", true));

        assertEquals("new", Files.readString(workspace.resolve("a.txt")));
    }

    @Test
    void writeRejectsDirectoryTarget() throws IOException {
        Files.createDirectories(workspace.resolve("dir"));

        assertToolError(LumiRoseErrorCode.TOOL_PATH_INVALID,
                () -> writeTool().execute(writeParams("dir", "x", true)));
    }

    @Test
    void writeRejectsNullContent() {
        assertToolError(LumiRoseErrorCode.TOOL_ARGS_INVALID,
                () -> writeTool().execute(writeParams("a.txt", null, false)));
    }

    @Test
    void editReplacesSingleOccurrence() throws IOException {
        Files.writeString(workspace.resolve("a.txt"), "hello world");

        String message = editTool().execute(editParams("a.txt", "world", "lumirose"));

        assertEquals("hello lumirose", Files.readString(workspace.resolve("a.txt")));
        assertEquals("Replaced 1 occurrence in a.txt.", message);
    }

    @Test
    void editRejectsNoMatch() throws IOException {
        Files.writeString(workspace.resolve("a.txt"), "hello world");

        assertToolError(LumiRoseErrorCode.TOOL_EDIT_NO_MATCH,
                () -> editTool().execute(editParams("a.txt", "absent", "x")));

        assertEquals("hello world", Files.readString(workspace.resolve("a.txt")));
    }

    @Test
    void editRejectsMultipleMatches() throws IOException {
        Files.writeString(workspace.resolve("a.txt"), "ab ab");

        assertToolError(LumiRoseErrorCode.TOOL_EDIT_NOT_UNIQUE,
                () -> editTool().execute(editParams("a.txt", "ab", "x")));

        assertEquals("ab ab", Files.readString(workspace.resolve("a.txt")));
    }

    @Test
    void editRejectsMissingFile() {
        assertToolError(LumiRoseErrorCode.TOOL_FILE_NOT_FOUND,
                () -> editTool().execute(editParams("missing.txt", "a", "b")));
    }

    @Test
    void editRejectsEmptyOldString() throws IOException {
        Files.writeString(workspace.resolve("a.txt"), "content");

        assertToolError(LumiRoseErrorCode.TOOL_ARGS_INVALID,
                () -> editTool().execute(editParams("a.txt", "", "b")));

        assertEquals("content", Files.readString(workspace.resolve("a.txt")));
    }

    @Test
    void deleteRemovesFile() throws IOException {
        Files.writeString(workspace.resolve("a.txt"), "x");

        String message = deleteTool().execute(deleteParams("a.txt"));

        assertEquals("Deleted a.txt.", message);
        assertFalse(Files.exists(workspace.resolve("a.txt")));
    }

    @Test
    void deleteRejectsMissingFile() {
        assertToolError(LumiRoseErrorCode.TOOL_FILE_NOT_FOUND,
                () -> deleteTool().execute(deleteParams("missing.txt")));
    }

    @Test
    void deleteRejectsDirectory() throws IOException {
        Files.createDirectories(workspace.resolve("dir"));

        assertToolError(LumiRoseErrorCode.TOOL_PATH_INVALID,
                () -> deleteTool().execute(deleteParams("dir")));
    }

    @Test
    void rejectsAbsolutePathOutsideWorkspace() throws IOException {
        Path target = outside.resolve("secret.txt");
        Files.writeString(target, "secret");

        assertToolError(LumiRoseErrorCode.TOOL_PATH_INVALID,
                () -> readTool().execute(readParams(target.toString())));
        assertToolError(LumiRoseErrorCode.TOOL_PATH_INVALID,
                () -> writeTool().execute(writeParams(target.toString(), "x", true)));
    }

    @Test
    void rejectsParentTraversal() {
        assertToolError(LumiRoseErrorCode.TOOL_PATH_INVALID,
                () -> readTool().execute(readParams("../outside/secret.txt")));
        assertToolError(LumiRoseErrorCode.TOOL_PATH_INVALID,
                () -> writeTool().execute(writeParams("nested/../../escape.txt", "x", false)));
        assertFalse(Files.exists(tempDir.resolve("escape.txt")));
    }

    @Test
    void rejectsSymlinkEscapingWorkspace() throws IOException {
        Files.writeString(outside.resolve("secret.txt"), "secret");
        Path link = workspace.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException e) {
            // Windows 上创建符号链接需要开发者模式或管理员权限；无法创建时跳过该条。
            Assumptions.assumeTrue(false, "当前环境无法创建符号链接: " + e);
            return;
        }

        assertToolError(LumiRoseErrorCode.TOOL_PATH_INVALID,
                () -> readTool().execute(readParams("link/secret.txt")));
        assertToolError(LumiRoseErrorCode.TOOL_PATH_INVALID,
                () -> writeTool().execute(writeParams("link/new.txt", "x", false)));
    }

    @Test
    void failedWriteLeavesNoPartialTargetOrTempFile() throws IOException {
        Files.writeString(workspace.resolve("blocker"), "not a directory");

        assertToolError(LumiRoseErrorCode.TOOL_EXECUTION_FAILED,
                () -> writeTool().execute(writeParams("blocker/child.txt", "data", false)));

        assertFalse(Files.exists(workspace.resolve("blocker/child.txt")));
        assertEquals(List.of(), temporaryFiles());
    }

    @Test
    void rejectsWorkspaceThatIsNotADirectory() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "x");

        assertConfigInvalid(() -> new ReadTool(null));
        assertConfigInvalid(() -> new ReadTool(file));
        assertConfigInvalid(() -> new ReadTool(tempDir.resolve("missing")));
    }

    private ReadTool readTool() {
        return new ReadTool(workspace);
    }

    private WriteTool writeTool() {
        return new WriteTool(workspace);
    }

    private EditTool editTool() {
        return new EditTool(workspace);
    }

    private DeleteTool deleteTool() {
        return new DeleteTool(workspace);
    }

    private static ReadTool.Params readParams(String path) {
        ReadTool.Params parameters = new ReadTool.Params();
        parameters.path = path;
        return parameters;
    }

    private static WriteTool.Params writeParams(String path, String content, boolean overwrite) {
        WriteTool.Params parameters = new WriteTool.Params();
        parameters.path = path;
        parameters.content = content;
        parameters.overwrite = overwrite;
        return parameters;
    }

    private static EditTool.Params editParams(String path, String oldString, String newString) {
        EditTool.Params parameters = new EditTool.Params();
        parameters.path = path;
        parameters.oldString = oldString;
        parameters.newString = newString;
        return parameters;
    }

    private static DeleteTool.Params deleteParams(String path) {
        DeleteTool.Params parameters = new DeleteTool.Params();
        parameters.path = path;
        return parameters;
    }

    /**
     * 返回工作区根目录下的原子写临时文件；成功后不应有任何残留。
     */
    private List<String> temporaryFiles() throws IOException {
        try (Stream<Path> files = Files.list(workspace)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(".lumirose-"))
                    .toList();
        }
    }

    private static void assertToolError(LumiRoseErrorCode expected, Executable executable) {
        ToolException error = assertThrows(ToolException.class, executable);
        assertEquals(expected, error.getErrorCode());
        assertTrue(error.toolName() != null && !error.toolName().isBlank());
    }

    private static void assertConfigInvalid(Executable executable) {
        LumiRoseException error = assertThrows(LumiRoseException.class, executable);
        assertEquals(LumiRoseErrorCode.CONFIG_INVALID, error.getErrorCode());
    }
}
