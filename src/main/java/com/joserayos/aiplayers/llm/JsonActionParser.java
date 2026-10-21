package com.joserayos.aiplayers.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fallback for models without native tool calling. Parses replies shaped like
 * {@code {"say": "...", "actions": [{"tool": "go_to", "args": {"x": 1}}]}}, tolerating code fences,
 * surrounding prose, and a few alternative key names.
 */
public final class JsonActionParser {
    public record Parsed(String say, List<ToolCall> calls) {}

    private JsonActionParser() {}

    /** True if the text looks like it was meant to be a JSON action object. */
    public static boolean looksLikeJson(String text) {
        String t = text == null ? "" : text.strip();
        return t.startsWith("{") || t.startsWith("```") || t.contains("\"actions\"") || t.contains("\"say\"");
    }

    public static Optional<Parsed> parse(String text) {
        if (text == null) return Optional.empty();
        String t = text.replaceAll("(?s)<think>.*?</think>", "").strip();
        t = t.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)\\s*```\\s*$", "");
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) return Optional.empty();
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(t.substring(start, end + 1));
            if (!el.isJsonObject()) return Optional.empty();
            root = el.getAsJsonObject();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        String say = firstString(root, "say", "reply", "message", "text");
        List<ToolCall> calls = new ArrayList<>();
        JsonElement actions = first(root, "actions", "tool_calls", "tools", "calls");
        if (actions != null && actions.isJsonArray()) {
            for (JsonElement a : actions.getAsJsonArray()) {
                if (a.isJsonObject()) toCall(a.getAsJsonObject()).ifPresent(calls::add);
            }
        } else if (actions != null && actions.isJsonObject()) {
            toCall(actions.getAsJsonObject()).ifPresent(calls::add);
        } else if (root.has("tool") || root.has("name")) {
            toCall(root).ifPresent(calls::add);
        }
        return Optional.of(new Parsed(say == null ? "" : say, calls));
    }

    private static Optional<ToolCall> toCall(JsonObject a) {
        String name = firstString(a, "tool", "name", "function", "action");
        if (name == null || name.isBlank()) return Optional.empty();
        JsonElement args = first(a, "args", "arguments", "parameters", "input");
        JsonObject argObj;
        if (args != null && args.isJsonObject()) {
            argObj = args.getAsJsonObject();
        } else if (args != null && args.isJsonPrimitive()) {
            try {
                JsonElement p = JsonParser.parseString(args.getAsString());
                argObj = p.isJsonObject() ? p.getAsJsonObject() : new JsonObject();
            } catch (RuntimeException e) {
                argObj = new JsonObject();
            }
        } else {
            // Arguments written inline next to "tool".
            argObj = a.deepCopy();
            for (String k : new String[]{"tool", "name", "function", "action"}) argObj.remove(k);
        }
        return Optional.of(new ToolCall(null, name.strip(), argObj));
    }

    private static JsonElement first(JsonObject o, String... keys) {
        for (String k : keys) if (o.has(k) && !o.get(k).isJsonNull()) return o.get(k);
        return null;
    }

    private static String firstString(JsonObject o, String... keys) {
        JsonElement e = first(o, keys);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    /** Instructions appended to the system prompt in json_prompt mode. */
    public static String instructions(List<ToolSpec> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nRespond ONLY with one JSON object and nothing else, in this exact shape:\n");
        sb.append("{\"say\": \"<short reply, may be empty>\", \"actions\": [{\"tool\": \"<tool name>\", \"args\": {<arguments>}}]}\n");
        sb.append("Use an empty actions list if no action is needed. Available tools:\n");
        for (ToolSpec t : tools) {
            sb.append("- ").append(t.name()).append(": ").append(t.description());
            JsonObject props = t.parameters().has("properties") ? t.parameters().getAsJsonObject("properties") : new JsonObject();
            if (!props.isEmpty()) {
                List<String> args = new ArrayList<>();
                JsonArray req = t.parameters().has("required") ? t.parameters().getAsJsonArray("required") : new JsonArray();
                for (String k : props.keySet()) {
                    String type = props.getAsJsonObject(k).has("type") ? props.getAsJsonObject(k).get("type").getAsString() : "any";
                    boolean required = false;
                    for (JsonElement r : req) if (r.getAsString().equals(k)) required = true;
                    args.add(k + ":" + type + (required ? "" : "?"));
                }
                sb.append(" Args: ").append(String.join(", ", args));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
