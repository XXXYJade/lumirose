package io.github.xxxyjade.lumirose.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 写出部分 SSE 后强制断开连接的假服务，用于覆盖流中途断开的分支。
 */
public final class BrokenSseServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final String body;
    private final int declaredLength;
    private final AtomicInteger connections = new AtomicInteger();
    private final Thread thread;

    public BrokenSseServer(String body, int declaredLength) throws IOException {
        this.body = body;
        this.declaredLength = declaredLength;
        this.serverSocket = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
        this.thread = new Thread(this::serve, "broken-sse");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + serverSocket.getLocalPort();
    }

    public int connectionCount() {
        return connections.get();
    }

    private void serve() {
        while (!serverSocket.isClosed()) {
            try (Socket socket = serverSocket.accept()) {
                connections.incrementAndGet();
                drainHeaders(socket.getInputStream());

                OutputStream out = socket.getOutputStream();
                String headers = "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/event-stream\r\n"
                        + "Content-Length: " + declaredLength + "\r\n"
                        + "Connection: close\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.UTF_8));
                out.write(body.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {
                // 客户端提前断开或服务端关闭
            }
        }
    }

    private static void drainHeaders(InputStream in) throws IOException {
        StringBuilder seen = new StringBuilder();
        int value;
        while ((value = in.read()) != -1) {
            seen.append((char) value);
            if (seen.length() >= 4 && seen.substring(seen.length() - 4).equals("\r\n\r\n")) {
                return;
            }
            if (seen.length() > 65_536) {
                return;
            }
        }
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // 已关闭
        }
    }
}
