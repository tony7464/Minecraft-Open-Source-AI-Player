package com.joserayos.aiplayers.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;

/** Appends one CSV row per model call: time, agent, profile, model, tokens, cost estimate, latency, status. */
public final class UsageLog {
    private static final String HEADER = "time,agent,profile,model,input_tokens,output_tokens,cost_usd,latency_ms,status\n";
    private final Path file;

    public UsageLog(Path file) {
        this.file = file;
    }

    public Path file() { return file; }

    public synchronized void append(String agent, String profile, String model, int in, int out, double cost, long latencyMs, String status) {
        if (file == null) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            if (!Files.exists(file)) Files.writeString(file, HEADER, StandardCharsets.UTF_8);
            String row = String.format(Locale.ROOT, "%s,%s,%s,%s,%d,%d,%.6f,%d,%s%n",
                    Instant.now(), csv(agent), csv(profile), csv(model), in, out, cost, latencyMs, csv(status));
            Files.writeString(file, row, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Usage logging must never break the game.
        }
    }

    static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"").replace('\n', ' ') + "\"";
        return s;
    }
}
