package com.joserayos.aiplayers.agent;

import com.joserayos.aiplayers.config.AiPlayersConfig;
import com.joserayos.aiplayers.registry.ModEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

/** Spawn, despawn and lookup of agents. Server thread only. */
public final class AgentManager {
    private AgentManager() {}

    /** All loaded agents in every dimension, sorted by name. */
    public static List<AgentEntity> all(MinecraftServer server) {
        List<AgentEntity> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            out.addAll(level.getEntities(ModEntities.AGENT.get(), e -> e.isAlive()));
        }
        out.sort(Comparator.comparing(AgentEntity::getAgentName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public static Optional<AgentEntity> byName(MinecraftServer server, String name) {
        return all(server).stream().filter(a -> a.getAgentName().equalsIgnoreCase(name)).findFirst();
    }

    public static List<AgentEntity> ownedBy(MinecraftServer server, ServerPlayer player) {
        return all(server).stream().filter(a -> a.isOwnedBy(player)).toList();
    }

    public static boolean isValidName(String name) {
        return name.matches("[A-Za-z0-9_]{1,16}") && !name.equalsIgnoreCase("all");
    }

    public sealed interface SpawnResult {
        record Ok(AgentEntity agent) implements SpawnResult {}
        record Error(String message) implements SpawnResult {}
    }

    public static SpawnResult spawn(ServerPlayer owner, String name, String profileId) {
        MinecraftServer server = owner.level().getServer();
        if (!isValidName(name)) return new SpawnResult.Error("Names must be 1-16 letters, digits or _ (and not 'all').");
        if (byName(server, name).isPresent()) return new SpawnResult.Error("An agent named " + name + " already exists.");
        int max = AiPlayersConfig.get(AiPlayersConfig.MAX_AGENTS);
        if (all(server).size() >= max) return new SpawnResult.Error("Agent limit reached (" + max + "). Change maxAgents in aiplayers-common.toml.");
        ServerLevel level = owner.level();
        AgentEntity agent = ModEntities.AGENT.get().create(level, EntitySpawnReason.COMMAND);
        if (agent == null) return new SpawnResult.Error("Could not create the agent entity.");
        agent.snapTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0.0F);
        agent.setOwnerId(owner.getUUID());
        agent.setAgentName(name);
        agent.setProfileId(profileId);
        level.addFreshEntity(agent);
        return new SpawnResult.Ok(agent);
    }
}
