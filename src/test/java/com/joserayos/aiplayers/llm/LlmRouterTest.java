package com.joserayos.aiplayers.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.joserayos.aiplayers.llm.config.ModelProfiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlmRouterTest {
    private static final String OK = """
            {"choices":[{"message":{"content":"hi"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1000,"completion_tokens":1000}}""";

    private LlmRouter router(String url, Path dir, String extra) {
        ModelProfiles profiles = ModelProfiles.parse("""
                {"default_profile":"local","profiles":{
                  "local":{"provider":"openai_compat","base_url":"%s","model":"m"},
                  "paid":{"provider":"openai_compat","base_url":"%s","model":"p","api_key":"k",
                          "daily_budget_usd":0.01,"price_per_million_input":5,"price_per_million_output":5 %s}}}"""
                .formatted(url, url, extra), Map.<String, String>of()::get);
        LlmRouter r = new LlmRouter(profiles, new RateLimiter(System::currentTimeMillis), new BudgetGuard(Clock.systemUTC()),
                new UsageLog(dir.resolve("usage.csv")), LlmRouter::defaultProvider);
        r.setBaseBackoffMs(10);
        return r;
    }

    @Test
    void retriesServerErrorsThenSucceeds(@TempDir Path dir) throws Exception {
        try (FakeHttpServer fake = new FakeHttpServer()) {
            fake.respond(503, "{}").respond(500, "{}").respond(200, OK);
            LlmRouter r = router(fake.baseUrl(), dir, "");
            var res = r.chat("Ava", "local", List.of(ChatMessage.user("hi")), List.of()).join();
            assertEquals("hi", res.response().text());
            assertEquals(3, fake.requests.size());
            List<String> csv = Files.readAllLines(dir.resolve("usage.csv"));
            assertEquals(2, csv.size());
            assertTrue(csv.get(1).contains(",Ava,local,m,1000,1000,"));
        }
    }

    @Test
    void doesNotRetryAuthErrors(@TempDir Path dir) throws Exception {
        try (FakeHttpServer fake = new FakeHttpServer()) {
            fake.respond(401, "{}");
            LlmRouter r = router(fake.baseUrl(), dir, "");
            var ex = assertThrows(CompletionException.class, () -> r.chat("Ava", "local", List.of(ChatMessage.user("hi")), List.of()).join());
            assertEquals(401, LlmException.from(ex).statusCode());
            assertEquals(1, fake.requests.size());
        }
    }

    @Test
    void budgetStopsHostedProfile(@TempDir Path dir) throws Exception {
        try (FakeHttpServer fake = new FakeHttpServer()) {
            fake.respond(200, OK);
            LlmRouter r = router(fake.baseUrl(), dir, "");
            r.chat("Bo", "paid", List.of(ChatMessage.user("hi")), List.of()).join(); // costs $0.01
            var ex = assertThrows(CompletionException.class, () -> r.chat("Bo", "paid", List.of(ChatMessage.user("hi")), List.of()).join());
            assertEquals(402, LlmException.from(ex).statusCode());
            assertEquals(0.01, r.budget().todayTotals().get("paid").costUsd(), 1e-9);
        }
    }

    @Test
    void budgetFallsBackToLocalProfile(@TempDir Path dir) throws Exception {
        try (FakeHttpServer fake = new FakeHttpServer()) {
            fake.respond(200, OK);
            LlmRouter r = router(fake.baseUrl(), dir, ",\"fallback_profile\":\"local\"");
            r.chat("Bo", "paid", List.of(ChatMessage.user("hi")), List.of()).join();
            var res = r.chat("Bo", "paid", List.of(ChatMessage.user("hi")), List.of()).join();
            assertEquals("local", res.profile().id());
        }
    }

    @Test
    void mockProfileWorksOffline(@TempDir Path dir) {
        ModelProfiles profiles = ModelProfiles.parse("{\"profiles\":{\"mock\":{\"provider\":\"mock\"}}}", k -> null);
        LlmRouter r = new LlmRouter(profiles, new RateLimiter(System::currentTimeMillis), new BudgetGuard(Clock.systemUTC()),
                new UsageLog(dir.resolve("u.csv")), LlmRouter::defaultProvider);
        var res = r.chat("Ava", "mock", List.of(ChatMessage.user("!go_to {\"x\":1,\"y\":2,\"z\":3} please")), List.of()).join();
        assertEquals("go_to", res.response().toolCalls().getFirst().name());
        assertEquals(3, res.response().toolCalls().getFirst().arguments().get("z").getAsInt());
    }

    @Test
    void rateLimiterBuckets() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter rl = new RateLimiter(now::get);
        assertEquals(0, rl.tryAcquire("p", 2));
        assertEquals(0, rl.tryAcquire("p", 2));
        long wait = rl.tryAcquire("p", 2);
        assertTrue(wait > 29_000 && wait <= 30_000, "wait " + wait);
        now.addAndGet(30_000);
        assertEquals(0, rl.tryAcquire("p", 2));
        assertEquals(0, rl.tryAcquire("other", 0), "0 means unlimited");
    }
}
