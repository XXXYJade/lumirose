package io.github.xxxyjade.lumirose.testing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 测试用的本地 HTTP 服务。
 *
 * <p>监听 {@code 127.0.0.1} 的随机端口，记录最近一次请求，并按当前模式
 * 返回 SSE、JSON 错误，或直接中止连接以模拟网络故障。
 */
public final class FakeServer implements AutoCloseable {

    private final HttpServer server;
    private volatile Mode mode = Mode.JSON;
    private volatile int status = 200;
    private volatile String body = "";
    private volatile RecordedRequest lastRequest;

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
     * 让后续请求返回指定状态码和 JSON 响应体。
     *
     * @param status HTTP 状态码
     * @param body   响应体
     * @return 当前夹具，便于链式调用
     */
    public FakeServer respondJson(int status, String body) {
        this.mode = Mode.JSON;
        this.status = status;
        this.body = body;
        return this;
    }

    /**
     * 让后续请求返回 SSE 响应。
     *
     * @param body 完整 SSE 文本
     * @return 当前夹具
     */
    public FakeServer respondSse(String body) {
        this.mode = Mode.SSE;
        this.status = 200;
        this.body = body;
        return this;
    }

    /**
     * 让后续请求在读完请求体后直接断开连接，不发送任何响应。
     *
     * @return 当前夹具
     */
    public FakeServer abort() {
        this.mode = Mode.ABORT;
        return this;
    }

    /**
     * 返回最近一次收到的请求；尚未收到请求时为 {@code null}。
     *
     * @return 请求快照
     */
    public RecordedRequest lastRequest() {
        return lastRequest;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) ->
                    headers.put(name.toLowerCase(Locale.ROOT), String.join(",", values)));
            lastRequest = new RecordedRequest(exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(), headers,
                    new String(requestBody, StandardCharsets.UTF_8));
            if (mode == Mode.ABORT) {
                // try-with-resources 关闭连接但不发送响应，客户端会读到 EOF。
                return;
            }
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type",
                    mode == Mode.SSE ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(status, response.length == 0 ? -1 : response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        }
    }

    private enum Mode {
        SSE, JSON, ABORT
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
