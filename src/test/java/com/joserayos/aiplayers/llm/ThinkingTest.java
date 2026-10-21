package com.joserayos.aiplayers.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.joserayos.aiplayers.llm.providers.OpenAiCompatibleProvider;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThinkingTest {
    @Test
    void stripsAllThinkShapes() {
        assertEquals("Hi", OpenAiCompatibleProvider.stripThinking("<think>plan plan</think>\nHi"));
        assertEquals("", OpenAiCompatibleProvider.stripThinking("<think>this got cut off because max_tokens"));
        assertEquals("On it", OpenAiCompatibleProvider.stripThinking("reasoning without opening tag</think>On it"));
        assertEquals("plain", OpenAiCompatibleProvider.stripThinking("plain"));
    }

    static LlmRequest req(String model) {
        return new LlmRequest(model, List.of(ChatMessage.system("sys"), ChatMessage.user("hi")), List.of(), 0.5, 0, Duration.ofSeconds(5));
    }

    @Test
    void qwen3OnLocalServerTurnsThinkingOff() {
        var body = OpenAiCompatibleProvider.buildBody(req("qwen3:8b"), true);
        assertFalse(body.get("think").getAsBoolean());
        assertEquals("none", body.get("reasoning_effort").getAsString());
        assertTrue(body.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString().endsWith("/no_think"));
        var hosted = OpenAiCompatibleProvider.buildBody(req("qwen3-32b"), false);
        assertFalse(hosted.has("think"));
        assertFalse(hosted.has("reasoning_effort"));
        var other = OpenAiCompatibleProvider.buildBody(req("llama3.1:8b"), true);
        assertFalse(other.has("think"));
        assertEquals("sys", other.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
    }

    @Test
    void localDetection() {
        assertTrue(HttpSupport.isLocal("http://127.0.0.1:11434/v1"));
        assertTrue(HttpSupport.isLocal("http://localhost:1234/v1"));
        assertFalse(HttpSupport.isLocal("https://api.x.ai/v1"));
    }
}
