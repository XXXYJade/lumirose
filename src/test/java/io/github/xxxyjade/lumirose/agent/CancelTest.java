package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.common.exception.AgentException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.result.LumiRoseResult;
import io.github.xxxyjade.lumirose.tool.EmptyParameters;
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 取消的检查点、幂等性、不写仓储和可再次运行测试。
 */
class CancelTest {

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
    void cancelBeforeStartSendsNoRequest() {
        LumiRoseResult result = fixture.agent().build().streamRun(1L, "hi");

        result.cancel();
        AgentException error = assertThrows(AgentException.class,
                () -> result.deltas().forEach(ignored -> { }));

        assertEquals(LumiRoseErrorCode.AGENT_CANCELLED, error.getErrorCode());
        assertEquals(0, fixture.server.requests().size());
        assertEquals(0, fixture.repository.load(1L).messages().size());
    }

    @Test
    void cancelDuringStreamDropsRemainingDeltasAndDoesNotWrite() {
        List<String> received = new ArrayList<>();
        AgentException failure = cancelAfterFirstDelta(received);

        assertEquals(List.of("a"), received);
        assertNotNull(failure);
        assertEquals(LumiRoseErrorCode.AGENT_CANCELLED, failure.getErrorCode());

        AgentFixture.sleep(600);
        assertEquals(0, fixture.repository.load(1L).messages().size());
    }

    @Test
    void cancelDuringBackoffStopsRetrying() throws InterruptedException {
        fixture.server.enqueueJson(500, "{}");
        LumiRoseResult result = fixture.agent().maxRetries(5)
                .retryBaseDelayMillis(1000).retryMaxDelayMillis(1000)
                .build().streamRun(1L, "hi");
        AtomicReference<AgentException> failure = new AtomicReference<>();
        Thread consumer = Thread.ofVirtual().start(() -> {
            try {
                result.deltas().forEach(ignored -> { });
            } catch (AgentException e) {
                failure.set(e);
            }
        });

        AgentFixture.awaitRequests(fixture.server, 1, 2000);
        result.cancel();
        consumer.join(2000);

        assertNotNull(failure.get());
        assertEquals(LumiRoseErrorCode.AGENT_CANCELLED, failure.get().getErrorCode());
        assertEquals(1, fixture.server.requests().size());
        assertEquals(0, fixture.repository.load(1L).messages().size());
    }

    @Test
    void cancelInsideToolStopsBeforeCommit() {
        AtomicReference<LumiRoseResult> resultRef = new AtomicReference<>();
        ToolContext tools = ToolContext.builder()
                .register(Tool.of("stop", "停止", new EmptyParameters(), parameters -> {
                    resultRef.get().cancel();
                    return "x";
                }))
                .build();
        fixture.server.enqueueSse(AgentFixture.toolReply(null,
                AgentFixture.ToolCallSpec.of("call_1", "stop", "{}")));

        LumiRoseResult result = fixture.agent(tools).build().streamRun(1L, "hi");
        resultRef.set(result);

        AgentException error = assertThrows(AgentException.class,
                () -> result.deltas().forEach(ignored -> { }));

        assertEquals(LumiRoseErrorCode.AGENT_CANCELLED, error.getErrorCode());
        assertEquals(1, fixture.server.requests().size());
        assertEquals(0, fixture.repository.load(1L).messages().size());
    }

    @Test
    void sameConversationCanRunAgainAfterCancel() {
        List<String> received = new ArrayList<>();
        AgentException failure = cancelAfterFirstDelta(received);
        assertNotNull(failure);
        AgentFixture.sleep(600);

        fixture.server.enqueueSse(AgentFixture.textReply("again", 1, 1, 2));
        LumiRoseResult second = fixture.agent().build().run(1L, "again");

        assertEquals("again", second.content());
        assertEquals(2, fixture.repository.load(1L).messages().size());
    }

    /**
     * 启动慢速流，消费第一个增量后取消，返回随后由迭代器抛出的取消异常。
     */
    private AgentException cancelAfterFirstDelta(List<String> received) {
        String first = AgentFixture.chunk(AgentFixture.content("a"), null);
        String rest = AgentFixture.chunk(AgentFixture.content("b"), "stop") + AgentFixture.DONE;
        fixture.server.enqueueSlowSse(first, 300, rest);
        LumiRoseResult result = fixture.agent().build().streamRun(1L, "hi");

        Iterator<String> iterator = result.deltas().iterator();
        AgentException failure = null;
        while (true) {
            try {
                if (!iterator.hasNext()) {
                    break;
                }
            } catch (AgentException e) {
                failure = e;
                break;
            }
            received.add(iterator.next());
            if (received.size() == 1) {
                result.cancel();
                result.cancel();
            }
        }
        return failure;
    }
}
