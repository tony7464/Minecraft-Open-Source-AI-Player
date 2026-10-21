package com.joserayos.aiplayers.command;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentManager;
import com.joserayos.aiplayers.brain.PromptBuilder;
import com.joserayos.aiplayers.safety.Permissions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.ServerChatEvent;

/** Routes "@Ava ..." / "@all ..." chat to agents. The original message still shows for everyone. */
public final class ChatRouter {
    private ChatRouter() {}

    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        MentionParser.Parsed parsed = MentionParser.parse(event.getRawText());
        if (parsed.isEmpty() || parsed.message().isEmpty()) return;
        MinecraftServer server = player.level().getServer();
        // Run after the chat message itself is broadcast.
        server.execute(() -> route(server, player, parsed));
    }

    static void route(MinecraftServer server, ServerPlayer player, MentionParser.Parsed parsed) {
        List<AgentEntity> targets = new ArrayList<>();
        if (parsed.mentionsAll()) {
            targets.addAll(AgentManager.ownedBy(server, player));
        }
        for (String name : parsed.names()) {
            if (name.equalsIgnoreCase("all")) continue;
            AgentManager.byName(server, name).ifPresent(a -> { if (!targets.contains(a)) targets.add(a); });
        }
        for (AgentEntity agent : targets) {
            deliver(player, agent, parsed.message());
        }
    }

    public static void deliver(ServerPlayer player, AgentEntity agent, String message) {
        if (!Permissions.canCommand(player, agent)) {
            player.sendSystemMessage(Component.literal(agent.getAgentName() + " only takes orders from its owner.").withStyle(ChatFormatting.GRAY));
            return;
        }
        agent.getAgentBrain().submit(PromptBuilder.chatInput(player.getGameProfile().name(), message));
    }
}
