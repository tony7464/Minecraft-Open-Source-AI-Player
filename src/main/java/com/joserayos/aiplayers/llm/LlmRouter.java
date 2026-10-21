package com.joserayos.aiplayers.llm;

import com.joserayos.aiplayers.llm.config.ModelProfile;
import com.joserayos.aiplayers.llm.config.ModelProfiles;
import com.joserayos.aiplayers.llm.providers.AnthropicProvider;
import com.joserayos.aiplayers.llm.providers.MockProvider;
import com.joserayos.aiplayers.llm.providers.OpenAiCompatibleProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Turns "agent X wants to chat using profile P" into a provider call, applying the
 * daily budget (with optional fallback profile), the per-profile rate limit, retries with backoff,
 * and usage logging.
 */
public final class LlmRouter {
    public static final int MAX_ATTEMPTS = 3;
    private static final long MAX_RATE_WAIT_MS = 15_000;

    private volatile ModelProfiles profiles;
    private final RateLimiter rateLimiter;
    private final BudgetGuard budget;
    private final UsageLog usageLog;
    private final Function<ModelProfile, LlmProvider> providerFactory;
    private final Map<String, LlmProvider> providers = new ConcurrentHashMap<>();
    private volatile long baseBackoffMs = 1000;

    public record Result(LlmResponse response, ModelProfile profile, long latencyMs) {}

    public LlmRouter(ModelProfiles profiles, RateLimiter rateLimiter, BudgetGuard budget, UsageLog usageLog,
                     Function<ModelProfile, LlmProvider> providerFactory) {
        this.profiles = profiles;
        this.rateLimiter = rateLimiter;
        this.budget = budget;
        this.usageLog = usageLog;
        this.providerFactory = providerFactory;
    }

    public static LlmProvider defaultProvider(ModelProfile p) {
        return switch (p.provider()) {
            case "anthropic" -> new AnthropicProvider(p.baseUrl(), p.apiKey());
            case "mock" -> new MockProvider();
            default -> new OpenAiCompatibleProvider(p.baseUrl(), p.apiKey());
        };
    }

    public ModelProfiles profiles() { return profiles; }

    public BudgetGuard budget() { return budget; }

    public UsageLog usageLog() { return usageLog; }

    public void setProfiles(ModelProfiles newProfiles) {
        this.profiles = newProfiles;
        providers.clear();
    }

    /** For tests: shorten retry backoff. */
    public void setBaseBackoffMs(long ms) { this.baseBackoffMs = ms; }

    /** Choose the profile to use right now, following the fallback chain when budgets are exhausted. */
    public ModelProfile select(String profileId) {
        ModelProfile p = profiles.resolve(profileId);
        for (int hops = 0; hops < 4 && budget.isExhausted(p.id(), p.dailyBudgetUsd()); hops++) {
            if (p.fallbackProfile().isEmpty() || profiles.get(p.fallbackProfile()).isEmpty()) {
                throw new LlmException("daily budget of $" + p.dailyBudgetUsd() + " for profile '" + p.id() + "' is used up", 402);
            }
            p = profiles.resolve(p.fallbackProfile());
        }
        return p;
    }

    public CompletableFuture<Result> chat(String agent, String profileId, List<ChatMessage> messages, List<ToolSpec> tools) {
        ModelProfile profile;
        try {
            profile = select(profileId);
        } catch (LlmException e) {
            return CompletableFuture.failedFuture(e);
        }
        if (!"mock".equals(profile.provider()) && profile.isHosted() && profile.apiKey().isBlank()) {
            return CompletableFuture.failedFuture(new LlmException(
                    "profile '" + profile.id() + "' has no API key. Set the environment variable named in models.json and restart", 401));
        }
        LlmRequest request = new LlmRequest(profile.model(), messages, tools, profile.temperature(), profile.maxOutputTokens(),
                Duration.ofSeconds(profile.timeoutSeconds()));
        LlmProvider provider = providers.computeIfAbsent(profile.id(), k -> providerFactory.apply(profiles.resolve(k)));
        long start = System.nanoTime();
        final ModelProfile used = profile;
        return attempt(provider, used, request, 1).handle((resp, err) -> {
            long latency = (System.nanoTime() - start) / 1_000_000;
            if (err != null) {
                LlmException e = LlmException.from(err);
                usageLog.append(agent, used.id(), used.model(), 0, 0, 0, latency, "error " + e.statusCode());
                throw e;
            }
            int in = resp.usage().inputTokens() > 0 ? resp.usage().inputTokens() : request.estimatedInputTokens();
            int out = resp.usage().outputTokens() > 0 ? resp.usage().outputTokens()
                    : (resp.text().length() + resp.toolCalls().toString().length()) / 4 + 1;
            double cost = used.costUsd(in, out);
            budget.record(used.id(), in, out, cost);
            usageLog.append(agent, used.id(), used.model(), in, out, cost, latency, "ok");
            return new Result(resp, used, latency);
        });
    }

    private CompletableFuture<LlmResponse> attempt(LlmProvider provider, ModelProfile profile, LlmRequest request, int attemptNo) {
        long wait = rateLimiter.tryAcquire(profile.id(), profile.requestsPerMinute());
        if (wait > MAX_RATE_WAIT_MS) {
            return CompletableFuture.failedFuture(new LlmException("local rate limit for profile '" + profile.id() + "' ("
                    + profile.requestsPerMinute() + "/min) reached", 429));
        }
        CompletableFuture<Void> gate = wait > 0
                ? CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(wait, TimeUnit.MILLISECONDS))
                : CompletableFuture.completedFuture(null);
        return gate.thenCompose(v -> provider.chat(request)).handle((resp, err) -> {
            if (err == null) return CompletableFuture.completedFuture(resp);
            LlmException e = LlmException.from(err);
            if (!e.isRetryable() || attemptNo >= MAX_ATTEMPTS || err instanceof java.util.concurrent.CancellationException) {
                return CompletableFuture.<LlmResponse>failedFuture(e);
            }
            long backoff = e.retryAfterMillis() > 0 ? Math.min(e.retryAfterMillis(), 20_000)
                    : baseBackoffMs * (1L << (attemptNo - 1)) + ThreadLocalRandom.current().nextLong(baseBackoffMs / 2 + 1);
            return CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(backoff, TimeUnit.MILLISECONDS))
                    .thenCompose(v -> attempt(provider, profile, request, attemptNo + 1));
        }).thenCompose(f -> f);
    }
}
