package com.joserayos.aiplayers.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.joserayos.aiplayers.llm.providers.OpenAiCompatibleProvider;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleProviderTest {
    private static LlmRequest request(List<ToolSpec> tools) {
        return new LlmRequest("qwen3:8b", List.of(ChatMessage.system("sys"), ChatMessage.user("hi")), tools, 0.4, 128, Duration.ofSeconds(5));
    }

    @Test
    void textReply() throws Exception {
        try (FakeHttpServer fake = new FakeHttpServer()) {
            fake.respond(200, """
                    {"choices":[{"message":{"role":"assistant","content":"<think>hmm</think>Hello there!"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":12,"completion_tokens":3}}""");
            var provider = new OpenAiCompatibleProvider(fake.baseUrl() + "/v1/", "secret");
            LlmResponse r = provider.chat(request(List.of())).join();
            assertEquals("Hello there!", r.text());
            assertEquals(12, r.usage().inputTokens());
            assertEquals(3, r.usage().outputTokens());

            var rec = fake.requests.getFirst();
            assertEquals("/v1/chat/completions", rec.path());
            assertEquals("Bearer secret", rec.headers().get("Authorization").getFirst());
            JsonObject body = JsonParser.parseString(rec.body()).getAsJsonObject();
            assertEquals("qwen3:8b", body.get("model").getAsString());
            assertEquals(2, body.getAsJsonArray("messages").size());
            assertFalse(body.has("tools"));
        }
    }

    @Test
    void toolCallsParsed() throws Exception {
        try (FakeHttpServer fake = new FakeHttpServer()) {
            fake.respond(200, """
                    {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
                      {"id":"c1","type":"function","function":{"name":"go_to","arguments":"{\\"x\\":1,\\"y\\":64,\\"z\\":-3}"}},
                      {"id":"c2","type":"function","function":{"name":"say","arguments":{"text":"ok"}}}
                    ]},"finish_reason":"tool_calls"}]}""");
            JsonObject schema = JsonParser.parseString("{\"type\":\"object\",\"properties\":{}}").getAsJsonObject();
            var provider = new OpenAiCompatibleProvider(fake.baseUrl(), "");
            LlmResponse r = provider.chat(request(List.of(new ToolSpec("go_to", "Walk somewhere", schema)))).join();
            assertEquals(2, r.toolCalls().size());
            assertEquals("go_to", r.toolCalls().get(0).name());
            assertEquals(64, r.toolCalls().get(0).arguments().get("y").getAsInt());
            assertEquals("ok", r.toolCalls().get(1).arguments().get("text").getAsString());
            assertNull(fake.requests.getFirst().headers().get("Authorization"));
            JsonObject body = JsonParser.parseString(fake.requests.getFirst().body()).getAsJsonObject();
            assertEquals("go_to", body.getAsJsonArray("tools").get(0).getAsJsonObject().getAsJsonObject("function").get("name").getAsString());
        }
    }

    @Test
    void httpErrorBecomesLlmException() throws Exception {
        try (FakeHttpServer fake = new FakeHttpServer()) {
            fake.respond(401, "{\"error\":\"bad key\"}");
            var provider = new OpenAiCompatibleProvider(fake.baseUrl(), "nope");
            CompletionException ex = assertThrows(CompletionException.class, () -> provider.chat(request(List.of())).join());
            LlmException e = LlmException.from(ex);
            assertEquals(401, e.statusCode());
            assertFalse(e.isRetryable());
            assertTrue(e.friendlyMessage().contains("API key"));
        }
    }

    @Test
    void connectionRefusedIsFriendly() {
        var provider = new OpenAiCompatibleProvider("http://127.0.0.1:1", "");
        CompletionException ex = assertThrows(CompletionException.class, () -> provider.chat(request(List.of())).join());
        LlmException e = LlmException.from(ex);
        assertEquals(-1, e.statusCode());
        assertTrue(e.isRetryable());
        assertTrue(e.friendlyMessage().contains("running"));
    }
}
