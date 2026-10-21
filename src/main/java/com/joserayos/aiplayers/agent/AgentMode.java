package com.joserayos.aiplayers.agent;

import java.util.Locale;
import java.util.Optional;

/** High-level behavior mode of an agent. */
public enum AgentMode {
    /** Follow the owner around. */
    FOLLOW,
    /** Stand still and wait for commands. */
    STAY,
    /** Look around, wander a little, wait for commands. */
    IDLE,
    /** Stay near a player or spot and fight hostile mobs that come close. */
    GUARD,
    /** Act on its own toward a goal (later milestone). */
    AUTONOMOUS;

    /** Modes in which the agent walks after its follow target. */
    public boolean follows() { return this == FOLLOW || this == GUARD; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Optional<AgentMode> parse(String s) {
        for (AgentMode m : values()) if (m.id().equalsIgnoreCase(s)) return Optional.of(m);
        return Optional.empty();
    }
}
