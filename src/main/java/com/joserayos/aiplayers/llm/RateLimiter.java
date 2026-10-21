package com.joserayos.aiplayers.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Token bucket per key (profile): {@code perMinute} requests per minute, bursting up to {@code perMinute}. */
public final class RateLimiter {
    private final LongSupplier clockMillis;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final class Bucket {
        double tokens;
        long last;
    }

    public RateLimiter(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    /**
     * Try to take one token. Returns 0 if allowed, otherwise the number of milliseconds to wait.
     * A non-positive {@code perMinute} means unlimited.
     */
    public long tryAcquire(String key, int perMinute) {
        if (perMinute <= 0) return 0;
        Bucket b = buckets.computeIfAbsent(key, k -> {
            Bucket nb = new Bucket();
            nb.tokens = perMinute;
            nb.last = clockMillis.getAsLong();
            return nb;
        });
        synchronized (b) {
            long now = clockMillis.getAsLong();
            double ratePerMs = perMinute / 60_000.0;
            b.tokens = Math.min(perMinute, b.tokens + (now - b.last) * ratePerMs);
            b.last = now;
            if (b.tokens >= 1.0) {
                b.tokens -= 1.0;
                return 0;
            }
            return (long) Math.ceil((1.0 - b.tokens) / ratePerMs);
        }
    }
}
