package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.exception.AgentException;
import io.github.xxxyjade.lumirose.repository.MessageRepository;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import io.github.xxxyjade.lumirose.testing.RecordingRepository;
import io.github.xxxyjade.lumirose.testing.RecordingTool;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyTest {

    private FakeServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void tc_lk01_sameConversationRunsSerially() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        RecordingTool tool = blockingTool(gate);
        RecordingRepository repository = new RecordingRepository();
        server.enqueue(200, toolCalls("slow"));
        server.enqueue(200, stopJson("done"));
        server.enqueue(200, stopJson("second"));

        Thread first = new Thread(() -> agent(tool, repository).run(1L, "a"));
        first.start();
        awaitUntil(() -> repository.count("load:1") == 1);

        Thread second = new Thread(() -> agent(tool, repository).run(1L, "b"));
        second.start();
        gate.countDown();
        first.join(5000);
        second.join(5000);

        assertEquals(List.of("load:1", "append:1", "load:1", "append:1"), repository.events());
    }

    @Test
    void tc_lk02_serialResultKeepsOrder() {
        RecordingRepository repository = new RecordingRepository();
        server.enqueue(200, stopJson("first"));
        server.enqueue(200, stopJson("second"));

        assertEquals("first", agent(repository).run(1L, "a"));
        assertEquals("second", agent(repository).run(1L, "b"));

        var history = repository.load(1L);
        assertEquals(5, history.size());
        assertEquals("system", history.get(0).role());
        assertEquals("user", history.get(1).role());
        assertEquals("assistant", history.get(2).role());
        assertEquals("user", history.get(3).role());
        assertEquals("assistant", history.get(4).role());
    }

    @Test
    void tc_lk03_differentConversationNotBlocked() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        RecordingTool tool = blockingTool(gate);
        RecordingRepository repository = new RecordingRepository();
        server.enqueue(200, toolCalls("slow"));
        server.enqueue(200, stopJson("two"));
        server.enqueue(200, stopJson("one"));

        Thread first = new Thread(() -> agent(tool, repository).run(1L, "a"));
        first.start();
        awaitUntil(() -> repository.count("load:1") == 1);

        Thread second = new Thread(() -> agent(tool, repository).run(2L, "b"));
        second.start();
        second.join(5000);

        assertEquals(List.of("load:1", "load:2", "append:2"), repository.events());
        assertTrue(repository.count("append:1") == 0);

        gate.countDown();
        first.join(5000);
        assertEquals(1, repository.count("append:1"));
    }

    @Test
    void tc_lk04_interruptedWhileWaitingCancelled() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        RecordingTool tool = blockingTool(gate);
        RecordingRepository repository = new RecordingRepository();
        server.enqueue(200, toolCalls("slow"));
        server.enqueue(200, stopJson("done"));

        Thread first = new Thread(() -> agent(tool, repository).run(1L, "a"));
        first.start();
        awaitUntil(() -> repository.count("load:1") == 1);

        AtomicReference<AgentException> error = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        Thread second = new Thread(() -> {
            started.countDown();
            try {
                agent(tool, repository).run(1L, "b");
            } catch (AgentException e) {
                interrupted.set(Thread.currentThread().isInterrupted());
                error.set(e);
            }
        });
        second.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        second.interrupt();
        second.join(5000);

        assertNotNull(error.get());
        assertEquals(3004, error.get().getErrorCode().code());
        assertTrue(interrupted.get());

        gate.countDown();
        first.join(5000);
    }

    private LumiRoseAgent agent(RecordingTool tool, MessageRepository repository) {
        return LumiRoseAgent.builder()
                .client(LumiRoseClient.builder()
                        .baseUrl(server.baseUrl())
                        .apiKey("test-key")
                        .model("deepseek-chat")
                        .build())
                .toolContext(ToolContext.builder().register(tool).build())
                .repository(repository)
                .build();
    }

    private LumiRoseAgent agent(MessageRepository repository) {
        return LumiRoseAgent.builder()
                .client(LumiRoseClient.builder()
                        .baseUrl(server.baseUrl())
                        .apiKey("test-key")
                        .model("deepseek-chat")
                        .build())
                .toolContext(ToolContext.builder().build())
                .repository(repository)
                .build();
    }

    private static RecordingTool blockingTool(CountDownLatch gate) {
        RecordingTool tool = new RecordingTool("slow", "慢");
        tool.willReturn("ok");
        tool.beforeExecute(() -> {
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
        return tool;
    }

    private static void awaitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("condition not met in time");
    }

    private String toolCalls(String... names) {
        StringBuilder json = new StringBuilder("{\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\","
                + "\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"id\":\"c").append(i + 1)
                    .append("\",\"type\":\"function\",\"function\":{\"name\":\"")
                    .append(names[i])
                    .append("\",\"arguments\":\"{}\"}}");
        }
        json.append("]}}]}");
        return json.toString();
    }

    private String stopJson(String content) {
        return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}]}";
    }
}
