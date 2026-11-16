package io.github.xxxyjade.lumirose.testing;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 测试用假模型服务：预置响应队列并记录收到的请求。
 */
public final class FakeServer implements AutoCloseable {

    private final HttpServer server;
    private final ConcurrentLinkedDeque<Stub> responses = new ConcurrentLinkedDeque<>();
    private final List<RecordedRequest> requests = Collections.synchronizedList(new ArrayList<>());

    public FakeServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/chat/completions", exchange -> {
            try {
                byte[] received = exchange.getRequestBody().readAllBytes();

                Map<String, String> headers = new LinkedHashMap<>();
                exchange.getRequestHeaders().forEach((name, values) -> {
                    if (!values.isEmpty()) {
                        headers.put(name.toLowerCase(Locale.ROOT), values.get(0));
                    }
                });
                requests.add(new RecordedRequest(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        headers,
                        new String(received, StandardCharsets.UTF_8)));

                Stub stub = responses.pollFirst();
                int status = stub == null ? 500 : stub.status();
                String contentType = stub == null ? "application/json" : stub.contentType();
                byte[] body = (stub == null ? "{}" : stub.body()).getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().add("Content-Type", contentType);
                if (stub != null && stub.chunked()) {
                    exchange.sendResponseHeaders(status, 0);
                    try (var out = exchange.getResponseBody()) {
                        out.write(body);
                        out.flush();
                    }
                } else if (stub != null && stub.declaredLength() > 0) {
                    exchange.sendResponseHeaders(status, stub.declaredLength());
                    try (var out = exchange.getResponseBody()) {
                        out.write(body);
                        out.flush();
                    }
                } else {
                    exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                }
            } catch (IOException ignored) {
                // 模拟截断时关闭连接会抛 IOException，客户端侧才是断言目标
            }
        });
        this.server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void enqueue(int status, String body) {
        responses.addLast(new Stub(status, body, "application/json", false, 0));
    }

    public void enqueueSse(String body) {
        responses.addLast(new Stub(200, body, "text/event-stream", true, 0));
    }

    public void enqueueTruncatedSse(String body, int declaredLength) {
        responses.addLast(new Stub(200, body, "text/event-stream", false, declaredLength));
    }

    public int requestCount() {
        synchronized (requests) {
            return requests.size();
        }
    }

    public List<RecordedRequest> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    public RecordedRequest lastRequest() {
        synchronized (requests) {
            if (requests.isEmpty()) {
                throw new IllegalStateException("no request recorded yet");
            }
            return requests.get(requests.size() - 1);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public record RecordedRequest(String method, String path, Map<String, String> headers, String body) {

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private record Stub(int status, String body, String contentType, boolean chunked, int declaredLength) {
    }
}
