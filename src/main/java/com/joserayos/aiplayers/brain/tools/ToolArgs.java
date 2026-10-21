package com.joserayos.aiplayers.brain.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Validating accessors for model-provided tool arguments. Throws {@link ToolArgException} with a model-readable message. */
public final class ToolArgs {
    private final JsonObject json;

    public ToolArgs(JsonObject json) {
        this.json = json == null ? new JsonObject() : json;
    }

    public static final class ToolArgException extends RuntimeException {
        public ToolArgException(String message) { super(message); }
    }

    public boolean has(String key) {
        return json.has(key) && !json.get(key).isJsonNull() && !(json.get(key).isJsonPrimitive() && json.get(key).getAsString().isBlank());
    }

    public String str(String key) {
        if (!has(key)) throw new ToolArgException("missing required argument '" + key + "'");
        JsonElement e = json.get(key);
        return e.isJsonPrimitive() ? e.getAsString().strip() : e.toString();
    }

    public String str(String key, String def) {
        return has(key) ? str(key) : def;
    }

    public int integer(String key, int min, int max) {
        if (!has(key)) throw new ToolArgException("missing required argument '" + key + "'");
        return clamp(parseInt(key), min, max);
    }

    public int integer(String key, int min, int max, int def) {
        return has(key) ? clamp(parseInt(key), min, max) : def;
    }

    private int parseInt(String key) {
        JsonElement e = json.get(key);
        try {
            return (int) Math.round(e.isJsonPrimitive() ? Double.parseDouble(e.getAsString().strip()) : Double.NaN);
        } catch (NumberFormatException ex) {
            throw new ToolArgException("argument '" + key + "' must be a number, got " + e);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public String toString() { return json.toString(); }
}
