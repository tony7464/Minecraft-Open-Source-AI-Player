package com.joserayos.aiplayers.llm.config;

/**
 * One named model configuration from models.json. Secrets are already resolved from ${env:...}.
 */
public record ModelProfile(
        String id,
        String provider,
        String baseUrl,
        String model,
        String apiKey,
        ToolMode toolMode,
        double temperature,
        int maxOutputTokens,
        int timeoutSeconds,
        int requestsPerMinute,
        double dailyBudgetUsd,
        double pricePerMillionInput,
        double pricePerMillionOutput,
        String fallbackProfile) {

    public enum ToolMode {
        /** Provider-native tool / function calling. */
        NATIVE,
        /** Ask for a JSON object in plain text (for models without tool calling). */
        JSON_PROMPT
    }

    /** True when requests leave this machine (used for the privacy notice). */
    public boolean isHosted() {
        if ("mock".equals(provider)) return false;
        String u = baseUrl.toLowerCase(java.util.Locale.ROOT);
        return !(u.contains("://localhost") || u.contains("://127.") || u.contains("://0.0.0.0") || u.contains("://[::1]")
                || u.contains("://192.168.") || u.contains("://10.") || u.contains(".local"));
    }

    public double costUsd(int inputTokens, int outputTokens) {
        return inputTokens / 1_000_000.0 * pricePerMillionInput + outputTokens / 1_000_000.0 * pricePerMillionOutput;
    }

    /** Short label for name tags, e.g. "qwen3:8b". */
    public String shortModel() {
        String m = model == null ? id : model;
        int slash = m.lastIndexOf('/');
        return slash >= 0 ? m.substring(slash + 1) : m;
    }
}
