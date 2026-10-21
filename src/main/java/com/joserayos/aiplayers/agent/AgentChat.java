package com.joserayos.aiplayers.agent;

import com.joserayos.aiplayers.config.AiPlayersConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Sends agent speech as system messages (not signed player chat). */
public final class AgentChat {
    private AgentChat() {}

    public static void say(AgentEntity agent, String text) {
        agent.serverLevel().ifPresent(level -> {
            String clean = sanitize(text, AiPlayersConfig.get(AiPlayersConfig.MAX_REPLY_CHARS));
            if (clean.isEmpty()) return;
            Component msg = Component.literal("<").append(Component.literal(agent.getAgentName()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("> " + clean));
            announce(level.getServer(), msg);
        });
    }

    public static void announce(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    /** Strip control characters / formatting codes and cap length. */
    public static String sanitize(String text, int maxChars) {
        if (text == null) return "";
        String s = text.replace('\u00a7', ' ').replaceAll("[\\p{Cntrl}&&[^\n]]", "").replace('\n', ' ').trim();
        if (s.length() > maxChars) s = s.substring(0, maxChars - 3) + "...";
        return s;
    }
}
