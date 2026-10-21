package com.joserayos.aiplayers.llm;

import java.time.Duration;
import java.util.List;

/** A provider-neutral chat request. */
public record LlmRequest(String model, List<ChatMessage> messages, List<ToolSpec> tools, double temperature, int maxOutputTokens, Duration timeout) {
    public LlmRequest {
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }

    public LlmRequest withTools(List<ToolSpec> newTools) {
        return new LlmRequest(model, messages, newTools, temperature, maxOutputTokens, timeout);
    }

    public LlmRequest withMessages(List<ChatMessage> newMessages) {
        return new LlmRequest(model, newMessages, tools, temperature, maxOutputTokens, timeout);
    }

    /** Rough input-size estimate (about 4 characters per token). */
    public int estimatedInputTokens() {
        int chars = 0;
        for (ChatMessage m : messages) chars += m.content().length() + 8;
        for (ToolSpec t : tools) chars += t.description().length() + t.parameters().toString().length() + t.name().length();
        return chars / 4 + 1;
    }
}
