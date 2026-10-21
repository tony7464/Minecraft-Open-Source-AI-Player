package com.joserayos.aiplayers.llm;

/** A failed model call. {@code statusCode} is the HTTP status, or -1 for connection errors. */
public class LlmException extends RuntimeException {
    private final int statusCode;
    private final long retryAfterMillis;

    public LlmException(String message, int statusCode, long retryAfterMillis, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryAfterMillis = retryAfterMillis;
    }

    public LlmException(String message, int statusCode) {
        this(message, statusCode, -1, null);
    }

    public int statusCode() { return statusCode; }

    public long retryAfterMillis() { return retryAfterMillis; }

    /** 429, 5xx and connection failures are worth retrying; 4xx (bad key, bad request) are not. */
    public boolean isRetryable() {
        return statusCode == -1 || statusCode == 429 || statusCode >= 500;
    }

    /** A short, player-friendly description. */
    public String friendlyMessage() {
        if (statusCode == -1) return "couldn't reach the model server (" + getMessage() + "). Is it running?";
        if (statusCode == 401 || statusCode == 403) return "the API key was rejected (HTTP " + statusCode + ")";
        if (statusCode == 404) return "model or endpoint not found (HTTP 404). Check base_url and model";
        if (statusCode == 429) return "rate limited by the provider (HTTP 429)";
        return "model error: " + getMessage();
    }

    /** Unwrap CompletionException layers to find an LlmException, or wrap the throwable. */
    public static LlmException from(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof LlmException e) return e;
            if (cur instanceof java.util.concurrent.TimeoutException || cur instanceof java.net.http.HttpTimeoutException) {
                return new LlmException("timed out", -1, -1, cur);
            }
            if (cur.getCause() == null || cur.getCause() == cur) break;
            cur = cur.getCause();
        }
        String msg = cur == null ? "unknown error" : (cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage());
        return new LlmException(msg, -1, -1, t);
    }
}
