package com.joserayos.aiplayers.llm;

import com.google.gson.JsonObject;

/** A tool (function) call requested by the model. Arguments are raw JSON and must be validated before use. */
public record ToolCall(String id, String name, JsonObject arguments) {
    public ToolCall {
        id = id == null || id.isBlank() ? "call_" + Long.toHexString(System.nanoTime()) : id;
        arguments = arguments == null ? new JsonObject() : arguments;
    }
}
