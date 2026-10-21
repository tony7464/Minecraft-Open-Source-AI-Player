package com.joserayos.aiplayers.command;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;

/** Hooks for commands that depend on model profiles (filled in by later milestones). */
final class AiCommandExtensions {
    private AiCommandExtensions() {}

    static void extend(LiteralArgumentBuilder<CommandSourceStack> root) {}

    static List<String> profileNames() {
        return List.of(defaultProfile());
    }

    static String defaultProfile() {
        return AiPlayersConfig.get(AiPlayersConfig.MODEL);
    }

    static void onSpawned(AgentEntity agent) {}
}
