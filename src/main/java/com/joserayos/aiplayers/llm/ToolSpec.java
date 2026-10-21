package com.joserayos.aiplayers.llm;

import com.google.gson.JsonObject;

/** A tool the model may call. {@code parameters} is a JSON Schema object. */
public record ToolSpec(String name, String description, JsonObject parameters) {}
