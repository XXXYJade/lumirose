package io.github.xxxyjade.lumirose.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.model.entity.Conversation;
import io.github.xxxyjade.lumirose.model.entity.Message;
import io.github.xxxyjade.lumirose.repository.ConversationRepository;
import io.github.xxxyjade.lumirose.repository.InMemoryConversationRepository;
import io.github.xxxyjade.lumirose.testing.FakeServer;
import io.github.xxxyjade.lumirose.tool.ToolContext;

import java.io.IOException;
import java.util.List;

/**
 * Agent 测试的共享装配：假服务、内存仓储、客户端和 SSE 脚本构造器。
 *
 * <p>测试不 mock {@link LumiRoseClient}；它保持 final，通过真实 HTTP 请求
 * 驱动，请求形状由 {@link FakeServer} 记录。
 */
final class AgentFixture {
    static final String API_KEY = "sk-agent-test";
    static final String MODEL = "test-model";
    static final String DONE = "data: [DONE]\n\n";

    private static final ObjectMapper JSON = new ObjectMapper();

    final FakeServer server;
    final InMemoryConversationRepository repository = new InMemoryConversationRepository();

    AgentFixture() throws IOException {
        this.server = new FakeServer();
    }

    LumiRoseClient client() {
        return LumiRoseClient.builder()
                .baseUrl(server.baseUrl())
                .apiKey(API_KEY)
                .model(MODEL)
                .build();
    }

    LumiRoseAgent.Builder agent() {
        return agent(ToolContext.builder().build());
    }

    LumiRoseAgent.Builder agent(ToolContext tools) {
        return agent(tools, repository);
    }

    LumiRoseAgent.Builder agent(ToolContext tools, ConversationRepository target) {
        return LumiRoseAgent.builder()
                .client(client())
                .toolContext(tools)
                .repository(target);
    }

    void close() {
        server.close();
    }

    static JsonNode body(FakeServer.RecordedRequest request) {
        try {
            return JSON.readTree(request.body());
        } catch (IOException e) {
            throw new AssertionError("请求体不是合法 JSON: " + request.body(), e);
        }
    }

    static String chunk(JsonNode delta, String finishReason) {
        return chunk(delta, finishReason, null, null, null);
    }

    static String chunk(JsonNode delta, String finishReason,
                        Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        ObjectNode choice = JSON.createObjectNode();
        choice.set("delta", delta);
        if (finishReason != null) {
            choice.put("finish_reason", finishReason);
        }
        ObjectNode root = JSON.createObjectNode();
        root.putArray("choices").add(choice);
        if (promptTokens != null || completionTokens != null || totalTokens != null) {
            ObjectNode usage = root.putObject("usage");
            if (promptTokens != null) {
                usage.put("prompt_tokens", promptTokens);
            }
            if (completionTokens != null) {
                usage.put("completion_tokens", completionTokens);
            }
            if (totalTokens != null) {
                usage.put("total_tokens", totalTokens);
            }
        }
        return "data: " + root + "\n\n";
    }

    static ObjectNode emptyDelta() {
        return JSON.createObjectNode();
    }

    static ObjectNode content(String text) {
        ObjectNode delta = JSON.createObjectNode();
        delta.put("content", text);
        return delta;
    }

    static ObjectNode reasoning(String text) {
        ObjectNode delta = JSON.createObjectNode();
        delta.put("reasoning_content", text);
        return delta;
    }

    /**
     * 一次性返回最终文本的完整 SSE 脚本。
     */
    static String textReply(String text) {
        return chunk(content(text), "stop") + DONE;
    }

    /**
     * 带 usage 的最终文本脚本。
     */
    static String textReply(String text, Integer promptTokens,
                            Integer completionTokens, Integer totalTokens) {
        return chunk(content(text), "stop", promptTokens, completionTokens, totalTokens) + DONE;
    }

    /**
     * 一轮工具调用脚本；{@code text} 为 {@code null} 时只发 tool_calls。
     */
    static String toolReply(String text, ToolCallSpec... calls) {
        ObjectNode delta = toolCalls(calls);
        if (text != null) {
            delta.put("content", text);
        }
        return chunk(delta, "tool_calls") + DONE;
    }

    static ObjectNode toolCalls(ToolCallSpec... calls) {
        ObjectNode delta = JSON.createObjectNode();
        ArrayNode array = delta.putArray("tool_calls");
        for (int index = 0; index < calls.length; index++) {
            ToolCallSpec call = calls[index];
            ObjectNode node = array.addObject();
            node.put("index", index);
            node.put("id", call.id);
            node.put("type", "function");
            ObjectNode function = node.putObject("function");
            function.put("name", call.name);
            function.put("arguments", call.arguments);
        }
        return delta;
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("测试等待被中断", e);
        }
    }

    static void awaitRequests(FakeServer server, int count, long timeoutMillis) {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (server.requests().size() < count) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("等待请求超时，目前只有 " + server.requests().size() + " 个");
            }
            sleep(5);
        }
    }

    static final class ToolCallSpec {
        final String id;
        final String name;
        final String arguments;

        private ToolCallSpec(String id, String name, String arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }

        static ToolCallSpec of(String id, String name, String arguments) {
            return new ToolCallSpec(id, name, arguments);
        }
    }

    /**
     * 统计写次数的仓储装饰器，用于断言“成功只提交一次”。
     */
    static final class CountingRepository implements ConversationRepository {
        private final ConversationRepository delegate;
        int appends;
        int compacts;

        CountingRepository(ConversationRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Conversation load(long conversationId) {
            return delegate.load(conversationId);
        }

        @Override
        public void append(long conversationId, List<Message> pending, int contextTokens) {
            appends++;
            delegate.append(conversationId, pending, contextTokens);
        }

        @Override
        public void compact(long conversationId, Message summary, List<Message> pending,
                            int contextTokens) {
            compacts++;
            delegate.compact(conversationId, summary, pending, contextTokens);
        }
    }
}
