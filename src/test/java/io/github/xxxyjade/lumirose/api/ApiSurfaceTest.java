package io.github.xxxyjade.lumirose.api;

import io.github.xxxyjade.lumirose.agent.LumiRoseAgent;
import io.github.xxxyjade.lumirose.common.result.LumiRoseResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用反射和源码树扫描固定 v1.1 的公共 API 形状，防止旧 API 回流。
 */
class ApiSurfaceTest {

    @Test
    void agentEntryPointsTakeLongConversationId() throws NoSuchMethodException {
        Method run = LumiRoseAgent.class.getMethod("run", long.class, String.class);
        Method streamRun = LumiRoseAgent.class.getMethod("streamRun", long.class, String.class);

        assertEquals(long.class, run.getParameterTypes()[0]);
        assertEquals(long.class, streamRun.getParameterTypes()[0]);
        assertEquals(LumiRoseResult.class, run.getReturnType());
        assertEquals(LumiRoseResult.class, streamRun.getReturnType());
        assertEquals(1, Arrays.stream(LumiRoseAgent.class.getMethods())
                .filter(method -> method.getName().equals("run"))
                .count());
        assertEquals(1, Arrays.stream(LumiRoseAgent.class.getMethods())
                .filter(method -> method.getName().equals("streamRun"))
                .count());
    }

    @Test
    void resultIsInterfaceWithoutClose() {
        assertTrue(LumiRoseResult.class.isInterface());
        assertFalse(AutoCloseable.class.isAssignableFrom(LumiRoseResult.class));
        assertFalse(Arrays.stream(LumiRoseResult.class.getMethods())
                .anyMatch(method -> method.getName().equals("close")));
        assertThrows(NoSuchMethodException.class, () -> LumiRoseResult.class.getMethod("close"));
    }

    @Test
    void removedMethodsAreAbsent() {
        assertFalse(Arrays.stream(LumiRoseAgent.class.getMethods())
                .anyMatch(method -> method.getName().equals("runResult")
                        || method.getName().equals("runStream")));
        assertFalse(Arrays.stream(LumiRoseAgent.Builder.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("compressionSummaryMaxTokens")));
        assertFalse(Arrays.stream(LumiRoseAgent.Builder.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("compressionSummaryMaxTokens")));
    }

    @Test
    void removedTypesAreAbsent() throws IOException {
        Set<String> names = sourceFileNames();
        for (String forbidden : List.of("RunResult", "ResultChannel", "MetricsRecorder",
                "StreamListener", "StreamControl", "MessageRepository",
                "ConversationLocks", "ContextTrimmer")) {
            assertFalse(names.contains(forbidden), "不应存在类型 " + forbidden);
        }
    }

    @Test
    void nestedAgentResultIsPrivate() {
        Class<?> nested = Arrays.stream(LumiRoseAgent.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("AgentResult"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 AgentResult"));

        assertTrue(Modifier.isPrivate(nested.getModifiers()));
        assertTrue(Modifier.isStatic(nested.getModifiers()));
        assertTrue(Arrays.stream(LumiRoseAgent.class.getMethods())
                .noneMatch(method -> method.getReturnType() == nested
                        || Arrays.asList(method.getParameterTypes()).contains(nested)));
    }

    @Test
    void agentIsFinal() {
        assertTrue(Modifier.isFinal(LumiRoseAgent.class.getModifiers()));
    }

    private static Set<String> sourceFileNames() throws IOException {
        Set<String> names = new HashSet<>();
        for (String root : List.of("src/main/java", "src/test/java")) {
            Path path = Path.of(root);
            if (!Files.isDirectory(path)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(path)) {
                files.filter(file -> file.toString().endsWith(".java"))
                        .forEach(file -> names.add(file.getFileName().toString()
                                .replaceFirst("\\.java$", "")));
            }
        }
        return names;
    }
}
