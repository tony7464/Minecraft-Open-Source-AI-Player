package com.joserayos.aiplayers.team;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Server-wide shared state for agents: block claims (so two agents don't mine the same block)
 * and shared named locations. Claims expire automatically. Server thread only.
 */
public final class Blackboard {
    private record Claim(UUID agent, long expiresAtTick) {}

    private static final Map<GlobalPos, Claim> CLAIMS = new ConcurrentHashMap<>();
    private static final Map<String, GlobalPos> LOCATIONS = new ConcurrentHashMap<>();

    private Blackboard() {}

    /** Try to claim a block for {@code ticks}. Returns false if another agent holds a live claim. */
    public static boolean claim(ResourceKey<Level> dim, BlockPos pos, UUID agent, long nowTick, int ticks) {
        GlobalPos key = GlobalPos.of(dim, pos.immutable());
        Claim existing = CLAIMS.get(key);
        if (existing != null && !existing.agent().equals(agent) && existing.expiresAtTick() > nowTick) return false;
        CLAIMS.put(key, new Claim(agent, nowTick + ticks));
        return true;
    }

    public static boolean isClaimedByOther(ResourceKey<Level> dim, BlockPos pos, UUID agent, long nowTick) {
        Claim c = CLAIMS.get(GlobalPos.of(dim, pos));
        return c != null && !c.agent().equals(agent) && c.expiresAtTick() > nowTick;
    }

    public static void release(ResourceKey<Level> dim, BlockPos pos, UUID agent) {
        CLAIMS.computeIfPresent(GlobalPos.of(dim, pos), (k, c) -> c.agent().equals(agent) ? null : c);
    }

    public static void releaseAll(UUID agent) {
        CLAIMS.values().removeIf(c -> c.agent().equals(agent));
    }

    public static void prune(long nowTick) {
        CLAIMS.values().removeIf(c -> c.expiresAtTick() <= nowTick);
    }

    // ---- shared locations ----

    public static void setLocation(String name, GlobalPos pos) { LOCATIONS.put(name.toLowerCase(java.util.Locale.ROOT), pos); }

    public static GlobalPos getLocation(String name) { return LOCATIONS.get(name.toLowerCase(java.util.Locale.ROOT)); }

    public static Map<String, GlobalPos> locations() { return Map.copyOf(LOCATIONS); }

    public static void clearLocations() { LOCATIONS.clear(); }

    /** Reset everything (server stop). */
    public static void reset() {
        CLAIMS.clear();
        LOCATIONS.clear();
    }
}
