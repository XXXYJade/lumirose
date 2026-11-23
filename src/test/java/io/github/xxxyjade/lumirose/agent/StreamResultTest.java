package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.result.LumiRoseResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * streamRun 的惰性启动、增量顺序、单次消费和取消唤醒测试。
 */
class StreamResultTest {

    private AgentFixture fixture;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new AgentFixture();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void doesNotStartBeforeIteration() {
        fixture.server.enqueueSse(AgentFixture.textReply("hello"));
        LumiRoseResult result = fixture.agent().build().streamRun(1L, "hi");

        assertEquals(0, fixture.server.requests().size());
        assertThrows(IllegalStateException.class, result::content);

        List<String> deltas = new ArrayList<>();
        result.deltas().forEach(deltas::add);

        assertEquals(List.of("hello"), deltas);
        assertEquals("hello", result.content());
        assertEquals(1, fixture.server.requests().size());
    }

    @Test
    void deltasPreserveOrderAndJoinToContent() {
        fixture.server.enqueueSse(
                AgentFixture.chunk(AgentFixture.content("你"), null)
                        + AgentFixture.chunk(AgentFixture.content("好"), null)
                        + AgentFixture.chunk(AgentFixture.emptyDelta(), "stop")
                        + AgentFixture.DONE);
        LumiRoseResult result = fixture.agent().build().streamRun(1L, "hi");

        List<String> deltas = new ArrayList<>();
        result.deltas().forEach(deltas::add);

        assertEquals(List.of("你", "好"), deltas);
        assertEquals("你好", result.content());
    }

    @Test
    void deltasCanOnlyBeConsumedOnce() {
        fixture.server.enqueueSse(AgentFixture.textReply("x"));
        LumiRoseResult result = fixture.agent().build().streamRun(1L, "hi");

        result.deltas();

        assertThrows(IllegalStateException.class, result::deltas);
    }

    @Test
    void uncompletedResultRejectsContentAndTokens() {
        LumiRoseResult result = fixture.agent().build().streamRun(1L, "hi");

        assertThrows(IllegalStateException.class, result::content);
        assertThrows(IllegalStateException.class, result::promptTokens);
        assertThrows(IllegalStateException.class, result::completionTokens);
        assertEquals(0, fixture.server.requests().size());
    }

    @Test
    void runExposesEmptyDeltaStream() {
        fixture.server.enqueueSse(AgentFixture.textReply("hello"));
        LumiRoseResult result = fixture.agent().build().run(1L, "hi");

        assertFalse(result.deltas().iterator().hasNext());
        assertEquals("hello", result.content());
    }

    @Test
    void cancelIsIdempotentAndWakesBlockedConsumer() {
        String first = AgentFixture.chunk(AgentFixture.content("a"), null);
        String rest = AgentFixture.chunk(AgentFixture.emptyDelta(), "stop") + AgentFixture.DONE;
        fixture.server.enqueueSlowSse(first, 400, rest);
        LumiRoseResult result = fixture.agent().build().streamRun(1L, "hi");

        Iterator<String> iterator = result.deltas().iterator();
        assertTrue(iterator.hasNext());
        assertEquals("a", iterator.next());

        result.cancel();
        result.cancel();

        long started = System.nanoTime();
        AgentException error = assertThrows(AgentException.class, iterator::hasNext);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        assertEquals(LumiRoseErrorCode.AGENT_CANCELLED, error.getErrorCode());
        assertTrue(elapsedMillis < 300, "cancel 应立即唤醒 consumer，实际耗时 " + elapsedMillis + "ms");

        AgentFixture.sleep(700);
        assertEquals(0, fixture.repository.load(1L).messages().size());
    }
}
