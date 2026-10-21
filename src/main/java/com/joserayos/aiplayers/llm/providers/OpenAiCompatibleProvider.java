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

/**
 * Adapter for any server that speaks the OpenAI {@code /chat/completions} API:
 * Ollama, LM Studio, llama.cpp, vLLM, OpenAI, xAI, OpenRouter, Groq, Gemini (OpenAI endpoint), ...
 */
public final class OpenAiCompatibleProvider implements LlmProvider {
    private final String baseUrl;
    private final String apiKey;

    public OpenAiCompatibleProvider(String baseUrl, String apiKey) {
        this.baseUrl = HttpSupport.trimSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public String id() { return "openai_compat"; }

    @Override
    public boolean supportsTools() { return true; }

    @Override
    public CompletableFuture<LlmResponse> chat(LlmRequest request) {
        HttpRequest.Builder http = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(request.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(request).toString()));
        if (!apiKey.isBlank()) http.header("Authorization", "Bearer " + apiKey);
        return HttpSupport.CLIENT.sendAsync(http.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpSupport::requireSuccess)
                .thenApply(OpenAiCompatibleProvider::parseResponse);
    }

    /** Converts the internal request into an OpenAI-style JSON body. */
    public static JsonObject buildBody(LlmRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("model", request.model());
        body.addProperty("temperature", request.temperature());
        if (request.maxOutputTokens() > 0) body.addProperty("max_tokens", request.maxOutputTokens());
        body.addProperty("stream", false);
        JsonArray messages = new JsonArray();
        for (ChatMessage m : request.messages()) messages.add(toJson(m));
        body.add("messages", messages);
        if (!request.tools().isEmpty()) {
            JsonArray tools = new JsonArray();
            for (ToolSpec t : request.tools()) {
                JsonObject fn = new JsonObject();
                fn.addProperty("name", t.name());
                fn.addProperty("description", t.description());
                fn.add("parameters", t.parameters());
                JsonObject tool = new JsonObject();
                tool.addProperty("type", "function");
                tool.add("function", fn);
                tools.add(tool);
            }
            body.add("tools", tools);
            body.addProperty("tool_choice", "auto");
        }
        return body;
    }

    private static JsonObject toJson(ChatMessage m) {
        JsonObject o = new JsonObject();
        switch (m.role()) {
            case SYSTEM -> o.addProperty("role", "system");
            case USER -> o.addProperty("role", "user");
            case ASSISTANT -> o.addProperty("role", "assistant");
            case TOOL -> o.addProperty("role", "tool");
        }
        o.addProperty("content", m.content());
        if (m.role() == ChatMessage.Role.TOOL) {
            o.addProperty("tool_call_id", m.toolCallId());
            if (m.toolName() != null) o.addProperty("name", m.toolName());
        }
        if (!m.toolCalls().isEmpty()) {
            JsonArray calls = new JsonArray();
            for (ToolCall c : m.toolCalls()) {
                JsonObject fn = new JsonObject();
                fn.addProperty("name", c.name());
                fn.addProperty("arguments", c.arguments().toString());
                JsonObject call = new JsonObject();
                call.addProperty("id", c.id());
                call.addProperty("type", "function");
                call.add("function", fn);
                calls.add(call);
            }
            o.add("tool_calls", calls);
        }
        return o;
    }

    /** Parses an OpenAI-style response body. */
    public static LlmResponse parseResponse(String json) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new LlmException("invalid JSON from provider", 502, -1, e);
        }
        JsonArray choices = root.has("choices") ? root.getAsJsonArray("choices") : new JsonArray();
        if (choices.isEmpty()) throw new LlmException("provider returned no choices", 502);
        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject msg = choice.has("message") ? choice.getAsJsonObject("message") : new JsonObject();
        String text = msg.has("content") && !msg.get("content").isJsonNull() ? msg.get("content").getAsString() : "";
        List<ToolCall> calls = new ArrayList<>();
        if (msg.has("tool_calls") && msg.get("tool_calls").isJsonArray()) {
            for (JsonElement el : msg.getAsJsonArray("tool_calls")) {
                JsonObject call = el.getAsJsonObject();
                JsonObject fn = call.getAsJsonObject("function");
                if (fn == null || !fn.has("name")) continue;
                String id = call.has("id") && !call.get("id").isJsonNull() ? call.get("id").getAsString() : null;
                calls.add(new ToolCall(id, fn.get("name").getAsString(), parseArguments(fn.get("arguments"))));
            }
        }
        Usage usage = Usage.ZERO;
        if (root.has("usage") && root.get("usage").isJsonObject()) {
            JsonObject u = root.getAsJsonObject("usage");
            usage = new Usage(intOr(u, "prompt_tokens"), intOr(u, "completion_tokens"));
        }
        String finish = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull() ? choice.get("finish_reason").getAsString() : "";
        return new LlmResponse(stripThinking(text), calls, usage, finish);
    }

    /** Arguments may arrive as a JSON string (OpenAI) or an object (some local servers). */
    static JsonObject parseArguments(JsonElement el) {
        if (el == null || el.isJsonNull()) return new JsonObject();
        if (el.isJsonObject()) return el.getAsJsonObject();
        try {
            JsonElement parsed = JsonParser.parseString(el.getAsString());
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    /** Reasoning models (qwen3, deepseek-r1) may wrap their reasoning in think tags; drop it from chat. */
    public static String stripThinking(String text) {
        return text.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    private static int intOr(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : 0;
    }
}
