package com.joserayos.aiplayers.llm.providers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.joserayos.aiplayers.llm.ChatMessage;
import com.joserayos.aiplayers.llm.LlmProvider;
import com.joserayos.aiplayers.llm.LlmRequest;
import com.joserayos.aiplayers.llm.LlmResponse;
import com.joserayos.aiplayers.llm.ToolCall;
import com.joserayos.aiplayers.llm.Usage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline provider for tests and demos. By default it echoes the last user message, and any
 * {@code !tool_name {json}} directives in it become tool calls, e.g. "@Ava !go_to {"x":1,"y":64,"z":2}".
 * Tests can install a custom script with {@link #setScript}.
 */
public final class MockProvider implements LlmProvider {
    private static final Pattern DIRECTIVE = Pattern.compile("!([a-z_]+)\\s*(\\{[^}]*})?");
    private static volatile Function<LlmRequest, LlmResponse> script = MockProvider::defaultScript;

    @Override
    public String id() { return "mock"; }

    @Override
    public boolean supportsTools() { return true; }

    @Override
    public CompletableFuture<LlmResponse> chat(LlmRequest request) {
        try {
            return CompletableFuture.completedFuture(script.apply(request));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public static void setScript(Function<LlmRequest, LlmResponse> newScript) {
        script = newScript == null ? MockProvider::defaultScript : newScript;
    }

    public static LlmResponse defaultScript(LlmRequest request) {
        String last = "";
        for (ChatMessage m : request.messages()) if (m.role() == ChatMessage.Role.USER) last = m.content();
        // Only react to the newest user turn if it is the final message (not after tool results).
        ChatMessage tail = request.messages().getLast();
        if (tail.role() == ChatMessage.Role.TOOL) {
            return new LlmResponse("Done: " + tail.content(), List.of(), new Usage(10, 5), "stop");
        }
        String plain = last.replaceAll("<[^>]+>", "").trim();
        List<ToolCall> calls = new ArrayList<>();
        Matcher m = DIRECTIVE.matcher(plain);
        while (m.find()) {
            JsonObject args = new JsonObject();
            if (m.group(2) != null) {
                try { args = JsonParser.parseString(m.group(2)).getAsJsonObject(); } catch (RuntimeException ignored) {}
            }
            calls.add(new ToolCall(null, m.group(1), args));
        }
        String text = calls.isEmpty() ? "Mock reply to: " + plain : "On it!";
        return new LlmResponse(text, calls, new Usage(request.estimatedInputTokens(), 8), calls.isEmpty() ? "stop" : "tool_calls");
    }
}
