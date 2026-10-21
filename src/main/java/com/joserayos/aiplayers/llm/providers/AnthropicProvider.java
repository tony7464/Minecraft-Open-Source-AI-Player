package com.joserayos.aiplayers.llm.providers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.joserayos.aiplayers.llm.ChatMessage;
import com.joserayos.aiplayers.llm.HttpSupport;
import com.joserayos.aiplayers.llm.LlmException;
import com.joserayos.aiplayers.llm.LlmProvider;
import com.joserayos.aiplayers.llm.LlmRequest;
import com.joserayos.aiplayers.llm.LlmResponse;
import com.joserayos.aiplayers.llm.ToolCall;
import com.joserayos.aiplayers.llm.ToolSpec;
import com.joserayos.aiplayers.llm.Usage;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Adapter for Anthropic's native Messages API ({@code /v1/messages}). */
public final class AnthropicProvider implements LlmProvider {
    public static final String API_VERSION = "2023-06-01";
    private final String baseUrl;
    private final String apiKey;

    public AnthropicProvider(String baseUrl, String apiKey) {
        this.baseUrl = HttpSupport.trimSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public String id() { return "anthropic"; }

    @Override
    public boolean supportsTools() { return true; }

    @Override
    public CompletableFuture<LlmResponse> chat(LlmRequest request) {
        String url = baseUrl.endsWith("/v1") ? baseUrl + "/messages" : baseUrl + "/v1/messages";
        HttpRequest http = HttpRequest.newBuilder(URI.create(url))
                .timeout(request.timeout())
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(request).toString()))
                .build();
        return HttpSupport.CLIENT.sendAsync(http, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpSupport::requireSuccess)
                .thenApply(AnthropicProvider::parseResponse);
    }

    public static JsonObject buildBody(LlmRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("model", request.model());
        body.addProperty("max_tokens", request.maxOutputTokens() > 0 ? request.maxOutputTokens() : 512);
        body.addProperty("temperature", request.temperature());
        StringBuilder system = new StringBuilder();
        JsonArray messages = new JsonArray();
        for (ChatMessage m : request.messages()) {
            switch (m.role()) {
                case SYSTEM -> system.append(system.isEmpty() ? "" : "\n\n").append(m.content());
                case USER -> append(messages, "user", textBlock(m.content()));
                case ASSISTANT -> {
                    JsonArray blocks = new JsonArray();
                    if (!m.content().isBlank()) blocks.add(textBlock(m.content()));
                    for (ToolCall c : m.toolCalls()) {
                        JsonObject b = new JsonObject();
                        b.addProperty("type", "tool_use");
                        b.addProperty("id", c.id());
                        b.addProperty("name", c.name());
                        b.add("input", c.arguments());
                        blocks.add(b);
                    }
                    if (blocks.isEmpty()) blocks.add(textBlock("..."));
                    for (JsonElement b : blocks) append(messages, "assistant", b.getAsJsonObject());
                }
                case TOOL -> {
                    JsonObject b = new JsonObject();
                    b.addProperty("type", "tool_result");
                    b.addProperty("tool_use_id", m.toolCallId());
                    b.addProperty("content", m.content());
                    append(messages, "user", b);
                }
            }
        }
        if (!system.isEmpty()) body.addProperty("system", system.toString());
        body.add("messages", messages);
        if (!request.tools().isEmpty()) {
            JsonArray tools = new JsonArray();
            for (ToolSpec t : request.tools()) {
                JsonObject tool = new JsonObject();
                tool.addProperty("name", t.name());
                tool.addProperty("description", t.description());
                tool.add("input_schema", t.parameters());
                tools.add(tool);
            }
            body.add("tools", tools);
        }
        return body;
    }

    /** Anthropic requires alternating roles, so consecutive blocks of the same role are merged into one message. */
    private static void append(JsonArray messages, String role, JsonObject block) {
        if (!messages.isEmpty()) {
            JsonObject last = messages.get(messages.size() - 1).getAsJsonObject();
            if (role.equals(last.get("role").getAsString())) {
                last.getAsJsonArray("content").add(block);
                return;
            }
        }
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        JsonArray content = new JsonArray();
        content.add(block);
        msg.add("content", content);
        messages.add(msg);
    }

    private static JsonObject textBlock(String text) {
        JsonObject b = new JsonObject();
        b.addProperty("type", "text");
        b.addProperty("text", text.isEmpty() ? "..." : text);
        return b;
    }

    public static LlmResponse parseResponse(String json) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new LlmException("invalid JSON from provider", 502, -1, e);
        }
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        if (root.has("content") && root.get("content").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("content")) {
                JsonObject b = el.getAsJsonObject();
                String type = b.has("type") ? b.get("type").getAsString() : "";
                if ("text".equals(type)) {
                    if (!text.isEmpty()) text.append(' ');
                    text.append(b.get("text").getAsString());
                } else if ("tool_use".equals(type)) {
                    JsonObject input = b.has("input") && b.get("input").isJsonObject() ? b.getAsJsonObject("input") : new JsonObject();
                    calls.add(new ToolCall(b.get("id").getAsString(), b.get("name").getAsString(), input));
                }
            }
        }
        Usage usage = Usage.ZERO;
        if (root.has("usage") && root.get("usage").isJsonObject()) {
            JsonObject u = root.getAsJsonObject("usage");
            usage = new Usage(u.has("input_tokens") ? u.get("input_tokens").getAsInt() : 0,
                    u.has("output_tokens") ? u.get("output_tokens").getAsInt() : 0);
        }
        String stop = root.has("stop_reason") && !root.get("stop_reason").isJsonNull() ? root.get("stop_reason").getAsString() : "";
        return new LlmResponse(text.toString().trim(), calls, usage, stop);
    }
}
