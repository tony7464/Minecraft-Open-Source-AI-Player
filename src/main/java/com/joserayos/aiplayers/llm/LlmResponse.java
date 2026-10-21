package com.joserayos.aiplayers.llm;

import java.util.List;

/** A provider-neutral chat response. */
public record LlmResponse(String text, List<ToolCall> toolCalls, Usage usage, String finishReason) {
    public LlmResponse {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? Usage.ZERO : usage;
        finishReason = finishReason == null ? "" : finishReason;
    }

    public static LlmResponse text(String text) {
        return new LlmResponse(text, List.of(), Usage.ZERO, "stop");
    }
}
