package io.github.xxxyjade.lumirose.testing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 测试用的本地 HTTP 服务。
 *
 * <p>监听 {@code 127.0.0.1} 的随机端口，按顺序消费预置响应，并记录每个请求。
 * 未预置响应时使用 {@code respond*} 设置的默认响应，两者都没有则返回空 500。
 *
 * <p>除了普通 SSE/JSON 响应，还支持两种异常流：
 * <ul>
 *   <li>慢速流：先写一段，停顿后再写剩余部分，用于在两次写出之间取消；</li>
 *   <li>截断流：声明的长度大于实际写出的字节数，用于模拟读到一半连接断开。</li>
 * </ul>
 */
public final class FakeServer implements AutoCloseable {

    private final HttpServer server;
    private final Deque<Reply> queue = new ArrayDeque<>();
    private final List<RecordedRequest> requests = new ArrayList<>();
    private volatile Reply defaultReply;

    /**
     * 启动服务。
     *
     * @throws IOException 端口绑定失败
     */
    public FakeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    /**
     * 返回可直接作为客户端 baseUrl 的前缀，例如 {@code http://127.0.0.1:12345}。
     *
     * @return 服务地址
     */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * 设置默认 JSON 响应，所有未预置的请求都会使用它。
     *
     * @param status HTTP 状态码
     * @param body   响应体
     * @return 当前夹具
     */
    public FakeServer respondJson(int status, String body) {
        this.defaultReply = new Reply(Mode.JSON, status, body, 0);
        return this;
    }

    /**
     * 设置默认 SSE 响应。
     *
     * @param body 完整 SSE 文本
     * @return 当前夹具
     */
    public FakeServer respondSse(String body) {
        this.defaultReply = new Reply(Mode.SSE, 200, body, 0);
        return this;
    }

    /**
     * 设置默认行为为读取请求体后直接断开连接。
     *
     * @return 当前夹具
     */
    public FakeServer abort() {
        this.defaultReply = new Reply(Mode.ABORT, 500, "", 0);
        return this;
    }

    /**
     * 预置一个只消费一次的 JSON 响应。
     *
     * @param status HTTP 状态码
     * @param body   响应体
     * @return 当前夹具
     */
    public FakeServer enqueueJson(int status, String body) {
        enqueue(new Reply(Mode.JSON, status, body, 0));
        return this;
    }

    /**
     * 预置一个只消费一次的 SSE 响应。
     *
     * @param body 完整 SSE 文本
     * @return 当前夹具
     */
    public FakeServer enqueueSse(String body) {
        enqueue(new Reply(Mode.SSE, 200, body, 0));
        return this;
    }

    /**
     * 预置一个慢速 SSE 响应：先写 {@code first}，停顿后再写 {@code rest}。
     *
     * @param first       先写出的部分
     * @param delayMillis 两次写出之间的停顿
     * @param rest        停顿后写出的部分
     * @return 当前夹具
     */
    public FakeServer enqueueSlowSse(String first, long delayMillis, String rest) {
        enqueue(new Reply(Mode.SLOW, 200, first, delayMillis, rest));
        return this;
    }

    /**
     * 预置一个截断的 SSE 响应：声明长度大于实际内容，写完 {@code partial} 后关闭连接。
     *
     * @param partial    实际写出的内容
     * @param extraBytes 额外声明的、永远不会到达的字节数
     * @return 当前夹具
     */
    public FakeServer enqueueTruncatedSse(String partial, int extraBytes) {
        enqueue(new Reply(Mode.TRUNCATED, 200, partial, extraBytes));
        return this;
    }

    /**
     * 返回最近一次收到的请求；尚未收到请求时为 {@code null}。
     *
     * @return 请求快照
     */
    public RecordedRequest lastRequest() {
        synchronized (requests) {
            return requests.isEmpty() ? null : requests.get(requests.size() - 1);
        }
    }

    /**
     * 返回按到达顺序排列的请求快照。
     *
     * @return 请求列表副本
     */
    public List<RecordedRequest> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void enqueue(Reply reply) {
        synchronized (queue) {
            queue.addLast(reply);
        }
    }

    private Reply nextReply() {
        synchronized (queue) {
            Reply reply = queue.pollFirst();
            if (reply != null) {
                return reply;
            }
        }
        Reply configured = defaultReply;
        return configured != null ? configured : new Reply(Mode.JSON, 500, "{\"error\":{}}", 0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) ->
                    headers.put(name.toLowerCase(Locale.ROOT), String.join(",", values)));
            RecordedRequest recorded = new RecordedRequest(exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(), headers,
                    new String(requestBody, StandardCharsets.UTF_8));
            synchronized (requests) {
                requests.add(recorded);
            }
            Reply reply = nextReply();
            switch (reply.mode) {
                case ABORT -> {
                    // try-with-resources 关闭连接但不发送响应，客户端会读到 EOF。
                }
                case TRUNCATED -> writeTruncated(exchange, reply);
                case SLOW -> writeSlow(exchange, reply);
                default -> write(exchange, reply.body.getBytes(StandardCharsets.UTF_8),
                        reply.mode == Mode.SSE ? "text/event-stream" : "application/json", reply.status);
            }
        }
    }

    private static void write(HttpExchange exchange, byte[] response,
                              String contentType, int status) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, response.length == 0 ? -1 : response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    private static void writeSlow(HttpExchange exchange, Reply reply) throws IOException {
        byte[] first = reply.body.getBytes(StandardCharsets.UTF_8);
        byte[] rest = reply.rest.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, first.length + rest.length);
        OutputStream out = exchange.getResponseBody();
        out.write(first);
        out.flush();
        try {
            Thread.sleep(reply.delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        out.write(rest);
        out.close();
    }

    private static void writeTruncated(HttpExchange exchange, Reply reply) throws IOException {
        byte[] partial = reply.body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        // 声明得比实际写出的多，客户端在读到 EOF 时会得到 IOException。
        exchange.sendResponseHeaders(200, partial.length + reply.extraBytes);
        OutputStream out = exchange.getResponseBody();
        out.write(partial);
        out.flush();
        // 故意不写满声明的长度；关闭 exchange 让连接提前结束。
        exchange.close();
    }

    private enum Mode {
        SSE, JSON, ABORT, SLOW, TRUNCATED
    }

    private static final class Reply {
        private final Mode mode;
        private final int status;
        private final String body;
        private final long delayMillis;
        private final String rest;
        private final int extraBytes;

        private Reply(Mode mode, int status, String body, int extraBytes) {
            this(mode, status, body, 0L, "", extraBytes);
        }

        private Reply(Mode mode, int status, String body, long delayMillis, String rest) {
            this(mode, status, body, delayMillis, rest, 0);
        }

        private Reply(Mode mode, int status, String body, long delayMillis, String rest, int extraBytes) {
            this.mode = mode;
            this.status = status;
            this.body = body;
            this.delayMillis = delayMillis;
            this.rest = rest;
            this.extraBytes = extraBytes;
        }
    }

    /**
     * 一次已记录请求的方法、路径、头和正文。
     */
    public static final class RecordedRequest {
        private final String method;
        private final String path;
        private final Map<String, String> headers;
        private final String body;

        private RecordedRequest(String method, String path, Map<String, String> headers, String body) {
            this.method = method;
            this.path = path;
            this.headers = Map.copyOf(headers);
            this.body = body;
        }

        public String method() {
            return method;
        }

        public String path() {
            return path;
        }

        /**
         * 按大小写不敏感的方式取请求头。
         *
         * @param name 头名
         * @return 头值；不存在时为 {@code null}
         */
        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        public String body() {
            return body;
        }
    }
}
