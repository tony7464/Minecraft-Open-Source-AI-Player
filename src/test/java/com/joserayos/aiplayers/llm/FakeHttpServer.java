package com.joserayos.aiplayers.llm;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Tiny fake provider server built on the JDK's HttpServer. */
public final class FakeHttpServer implements AutoCloseable {
    public record Recorded(String path, Map<String, List<String>> headers, String body) {}

    private final HttpServer server;
    public final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private final List<int[]> statusQueue = new ArrayList<>();
    private final List<String> bodyQueue = new ArrayList<>();
    private final Map<String, String> extraHeaders = new ConcurrentHashMap<>();

    public FakeHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Recorded(exchange.getRequestURI().getPath(), Map.copyOf(exchange.getRequestHeaders()), body));
            int status;
            String reply;
            synchronized (statusQueue) {
                status = statusQueue.isEmpty() ? 200 : (statusQueue.size() > 1 ? statusQueue.removeFirst()[0] : statusQueue.getFirst()[0]);
                reply = bodyQueue.isEmpty() ? "{}" : (bodyQueue.size() > 1 ? bodyQueue.removeFirst() : bodyQueue.getFirst());
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            extraHeaders.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();
    }

    /** Queue a response; the last queued response repeats forever. */
    public FakeHttpServer respond(int status, String body) {
        synchronized (statusQueue) {
            statusQueue.add(new int[]{status});
            bodyQueue.add(body);
        }
        return this;
    }

    public FakeHttpServer header(String k, String v) {
        extraHeaders.put(k, v);
        return this;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
