package com.joserayos.aiplayers.safety;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** Who may command which agent. Owner only by default, plus ops if enabled. */
public final class Permissions {
    private Permissions() {}

    public static boolean isOp(ServerPlayer player) {
        return player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    public static boolean isOp(CommandSourceStack source) {
        return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    public static boolean canCommand(ServerPlayer player, AgentEntity agent) {
        if (agent.isOwnedBy(player)) return true;
        return AiPlayersConfig.get(AiPlayersConfig.OPS_CAN_COMMAND_ANY_AGENT) && isOp(player);
    }

    /** Console / command blocks (no player) are trusted like ops. */
    public static boolean canCommand(CommandSourceStack source, AgentEntity agent) {
        ServerPlayer p = source.getPlayer();
        return p == null ? isOp(source) : canCommand(p, agent);
    }
}
