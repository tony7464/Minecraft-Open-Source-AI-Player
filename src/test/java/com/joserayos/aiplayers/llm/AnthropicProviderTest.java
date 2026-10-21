package com.joserayos.aiplayers.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.joserayos.aiplayers.llm.providers.AnthropicProvider;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnthropicProviderTest {
    @Test
    void roundTrip() throws Exception {
        try (FakeHttpServer fake = new FakeHttpServer()) {
            fake.respond(200, """
                    {"content":[{"type":"text","text":"Coming!"},
                                {"type":"tool_use","id":"tu1","name":"go_to_player","input":{"player":"Jose"}}],
                     "stop_reason":"tool_use","usage":{"input_tokens":50,"output_tokens":9}}""");
            JsonObject schema = JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject();
            ToolCall prior = new ToolCall("tu0", "get_status", new JsonObject());
            LlmRequest req = new LlmRequest("claude-x", List.of(
                    ChatMessage.system("be brief"),
                    ChatMessage.user("status?"),
                    ChatMessage.assistant("", List.of(prior)),
                    ChatMessage.toolResult("tu0", "get_status", "health 20"),
                    ChatMessage.user("come here")),
                    List.of(new ToolSpec("go_to_player", "walk to a player", schema)), 0.2, 256, Duration.ofSeconds(5));
            LlmResponse r = new AnthropicProvider(fake.baseUrl(), "ak").chat(req).join();
            assertEquals("Coming!", r.text());
            assertEquals("go_to_player", r.toolCalls().getFirst().name());
            assertEquals("Jose", r.toolCalls().getFirst().arguments().get("player").getAsString());
            assertEquals(50, r.usage().inputTokens());

            var rec = fake.requests.getFirst();
            assertEquals("/v1/messages", rec.path());
            assertEquals("ak", rec.headers().get("X-api-key").getFirst());
            assertEquals(AnthropicProvider.API_VERSION, rec.headers().get("Anthropic-version").getFirst());
            JsonObject body = JsonParser.parseString(rec.body()).getAsJsonObject();
            assertEquals("be brief", body.get("system").getAsString());
            JsonArray msgs = body.getAsJsonArray("messages");
            // user, assistant(tool_use), user(tool_result + text) -> roles alternate
            assertEquals(3, msgs.size());
            assertEquals("assistant", msgs.get(1).getAsJsonObject().get("role").getAsString());
            JsonArray last = msgs.get(2).getAsJsonObject().getAsJsonArray("content");
            assertEquals("tool_result", last.get(0).getAsJsonObject().get("type").getAsString());
            assertEquals("text", last.get(1).getAsJsonObject().get("type").getAsString());
            assertEquals("go_to_player", body.getAsJsonArray("tools").get(0).getAsJsonObject().get("name").getAsString());
        }
    }
}
