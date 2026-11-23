package io.github.xxxyjade.lumirose.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v1.1 约束：实体、DTO、DO 和测试夹具必须是 final class，不使用 {@code record}。
 */
class NoRecordTest {

    private static final Pattern RECORD_DECLARATION = Pattern.compile(
            "(?m)^\\s*(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed)\\s+)*"
                    + "record\\s+[A-Za-z_$][\\w$]*");

    @Test
    void noRecordDeclarationsInMainOrTestSources() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String root : List.of("src/main/java", "src/test/java")) {
            Path path = Path.of(root);
            if (!Files.isDirectory(path)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(path)) {
                for (Path file : files.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                    if (file.getFileName().toString().equals("NoRecordTest.java")) {
                        continue;
                    }
                    findViolations(file, violations);
                }
            }
        }

        assertTrue(violations.isEmpty(), "发现 record 声明: " + violations);
    }

    private static void findViolations(Path file, List<String> violations) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Matcher matcher = RECORD_DECLARATION.matcher(text);
        while (matcher.find()) {
            violations.add(file + ":" + lineNumber(text, matcher.start()) + " " + firstLine(text, matcher.start()));
        }
    }

    private static int lineNumber(String text, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) {
            if (text.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String firstLine(String text, int offset) {
        int end = text.indexOf('\n', offset);
        return end < 0 ? text.substring(offset) : text.substring(offset, end).trim();
    }
}
