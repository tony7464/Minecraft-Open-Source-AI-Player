package com.joserayos.aiplayers.perception;

import java.util.List;

/**
 * Immutable, read-only summary of what an agent knows right now. Built on the server thread,
 * then handed to the brain thread. Contains only plain data (no Minecraft objects).
 */
public record AgentSnapshot(
        String agentName,
        String ownerName,
        String mode,
        float health,
        float maxHealth,
        int x, int y, int z,
        String dimension,
        String biome,
        String timeOfDay,
        String weather,
        String ownerInfo,
        List<String> nearbyPlayers,
        List<String> nearbyAgents,
        List<String> threats,
        List<String> inventory,
        String heldItem,
        String currentTask,
        String lastResult,
        List<String> recentEvents,
        List<String> resources,
        List<String> structures) {

    public AgentSnapshot {
        nearbyPlayers = List.copyOf(nearbyPlayers);
        nearbyAgents = List.copyOf(nearbyAgents);
        threats = List.copyOf(threats);
        inventory = List.copyOf(inventory);
        recentEvents = List.copyOf(recentEvents);
        resources = List.copyOf(resources);
        structures = List.copyOf(structures);
    }

    /** Compact text form for the prompt (small models handle this well). */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[SELF] ").append(agentName).append(" | health ").append(Math.round(health)).append('/').append(Math.round(maxHealth))
                .append(" | pos (").append(x).append(", ").append(y).append(", ").append(z).append(") ").append(dimension)
                .append(" | biome ").append(biome).append(" | mode ").append(mode).append('\n');
        sb.append("[TIME] ").append(timeOfDay).append(" | weather ").append(weather).append('\n');
        sb.append("[OWNER] ").append(ownerName).append(": ").append(ownerInfo).append('\n');
        sb.append("[HELD] ").append(heldItem).append('\n');
        sb.append("[INVENTORY] ").append(inventory.isEmpty() ? "empty" : String.join(", ", inventory)).append('\n');
        sb.append("[NEARBY PLAYERS] ").append(nearbyPlayers.isEmpty() ? "none" : String.join(", ", nearbyPlayers)).append('\n');
        sb.append("[NEARBY AGENTS] ").append(nearbyAgents.isEmpty() ? "none" : String.join(", ", nearbyAgents)).append('\n');
        sb.append("[THREATS] ").append(threats.isEmpty() ? "none seen" : String.join(", ", threats)).append('\n');
        sb.append("[RESOURCES <=24m] ").append(resources.isEmpty() ? "nothing notable seen yet" : String.join(", ", resources)).append('\n');
        if (!structures.isEmpty()) sb.append("[STRUCTURES] ").append(String.join(", ", structures)).append('\n');
        sb.append("[CURRENT TASK] ").append(currentTask).append(" | LAST RESULT: ").append(lastResult).append('\n');
        if (!recentEvents.isEmpty()) sb.append("[RECENT EVENTS] ").append(String.join("; ", recentEvents)).append('\n');
        return sb.toString();
    }
}
