package com.joserayos.aiplayers.llm;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks today's estimated spend and token counts per profile. Resets at local midnight. */
public final class BudgetGuard {
    public record Totals(int requests, long inputTokens, long outputTokens, double costUsd) {
        Totals add(int in, int out, double cost) {
            return new Totals(requests + 1, inputTokens + in, outputTokens + out, costUsd + cost);
        }
    }

    private final Clock clock;
    private LocalDate day;
    private final Map<String, Totals> today = new ConcurrentHashMap<>();

    public BudgetGuard(Clock clock) {
        this.clock = clock;
        this.day = LocalDate.now(clock);
    }

    private synchronized void rollover() {
        LocalDate now = LocalDate.now(clock);
        if (!now.equals(day)) {
            day = now;
            today.clear();
        }
    }

    /** True if the profile has a budget and today's spend has reached it. */
    public boolean isExhausted(String profile, double dailyBudgetUsd) {
        if (dailyBudgetUsd <= 0) return false;
        rollover();
        return today.getOrDefault(profile, new Totals(0, 0, 0, 0)).costUsd() >= dailyBudgetUsd;
    }

    public void record(String profile, int inputTokens, int outputTokens, double costUsd) {
        rollover();
        today.merge(profile, new Totals(0, 0, 0, 0).add(inputTokens, outputTokens, costUsd),
                (a, b) -> a.add(inputTokens, outputTokens, costUsd));
    }

    public Map<String, Totals> todayTotals() {
        rollover();
        return new TreeMap<>(today);
    }
}
