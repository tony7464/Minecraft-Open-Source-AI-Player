package com.joserayos.aiplayers.llm;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;

/** Shared JDK HTTP client running on virtual threads. */
public final class HttpSupport {
    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1) // Ollama/llama.cpp drop h2c upgrade requests
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    private HttpSupport() {}

    /** Throws a {@link LlmException} for non-2xx responses. */
    public static String requireSuccess(HttpResponse<String> response) {
        int code = response.statusCode();
        if (code >= 200 && code < 300) return response.body();
        long retryAfter = response.headers().firstValue("retry-after").map(v -> {
            try { return (long) (Double.parseDouble(v) * 1000); } catch (NumberFormatException e) { return -1L; }
        }).orElse(-1L);
        String body = response.body() == null ? "" : response.body();
        if (body.length() > 300) body = body.substring(0, 300) + "...";
        throw new LlmException("HTTP " + code + ": " + body, code, retryAfter, null);
    }

    public static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Mask an API key for logs: "sk-abc...xyz". */
    public static String mask(String key) {
        if (key == null || key.isEmpty()) return "(none)";
        if (key.length() <= 8) return "****";
        return key.substring(0, 3) + "..." + key.substring(key.length() - 3);
    }

    /** Base URL points at this machine. */
    public static boolean isLocal(String baseUrl) {
        String u = baseUrl.toLowerCase(java.util.Locale.ROOT);
        return u.contains("://127.0.0.1") || u.contains("://localhost") || u.contains("://[::1]") || u.contains("://0.0.0.0");
    }
}
