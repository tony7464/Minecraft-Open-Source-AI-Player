package com.joserayos.aiplayers.llm;

/** Token usage reported by a provider (or estimated when the provider does not report it). */
public record Usage(int inputTokens, int outputTokens) {
    public static final Usage ZERO = new Usage(0, 0);
}
