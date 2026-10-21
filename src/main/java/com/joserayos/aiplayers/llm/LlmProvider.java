package com.joserayos.aiplayers.llm;

import java.util.concurrent.CompletableFuture;

/** A model backend. Implementations must never block the caller; all I/O happens asynchronously. */
public interface LlmProvider {
    /** "openai_compat", "anthropic", "mock". */
    String id();

    CompletableFuture<LlmResponse> chat(LlmRequest request);

    /** Whether this provider supports native tool / function calling. */
    boolean supportsTools();
}
