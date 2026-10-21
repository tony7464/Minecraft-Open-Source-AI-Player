package com.joserayos.aiplayers.network;

import com.joserayos.aiplayers.agent.AgentChat;
import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentManager;
import com.joserayos.aiplayers.agent.AgentMode;
import com.joserayos.aiplayers.brain.AiServices;
import com.joserayos.aiplayers.brain.PromptBuilder;
import com.joserayos.aiplayers.safety.Permissions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Payload registration and the server side of the Agent Manager. */
public final class AiNetwork {
    public static final String VERSION = "1";

    private AiNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(AiNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(VERSION).optional();
        registrar.playToClient(AgentListPayload.TYPE, AgentListPayload.CODEC);
        registrar.playToServer(AgentActionPayload.TYPE, AgentActionPayload.CODEC, AiNetwork::handleAction);
    }

    private static void handleAction(AgentActionPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        String result = apply(player, payload);
        if (!result.isEmpty()) {
            player.sendSystemMessage(Component.literal(result).withStyle(result.startsWith("Can't") || result.startsWith("Unknown") || result.startsWith("No ")
                    ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
        sendList(player, false);
    }

    /** Applies an action with the same permission rules as the commands. Returns a message for the player. */
    public static String apply(ServerPlayer player, AgentActionPayload p) {
        if (p.action().equals("refresh")) return "";
        AgentEntity agent = AgentManager.byName(player.level().getServer(), p.agent()).orElse(null);
        if (agent == null) return "No agent named " + p.agent() + ".";
        if (!Permissions.canCommand(player, agent)) return "Can't control " + agent.getAgentName() + ": it isn't yours.";
        switch (p.action()) {
            case "mode" -> {
                AgentMode mode = AgentMode.parse(p.arg()).orElse(null);
                if (mode == null) return "Unknown mode " + p.arg() + ".";
                if (mode == AgentMode.GUARD) agent.startGuard(player, null, agent.getGuardRadius());
                else agent.setMode(mode);
                return agent.getAgentName() + " is now in " + mode.id() + " mode.";
            }
            case "stop" -> {
                agent.stopEverything("stopped by " + player.getGameProfile().name() + " (GUI)");
                return "Stopped " + agent.getAgentName() + ".";
            }
            case "despawn" -> {
                agent.getAgentBrain().cancel("despawned");
                agent.discard();
                return "Despawned " + agent.getAgentName() + ".";
            }
            case "model" -> {
                var profile = AiServices.router().profiles().get(p.arg());
                if (profile.isEmpty()) return "Unknown profile " + p.arg() + ".";
                agent.setProfileId(profile.get().id());
                return agent.getAgentName() + " now uses " + profile.get().id() + ".";
            }
            case "tell" -> {
                String text = AgentChat.sanitize(p.arg(), 400);
                if (text.isBlank()) return "";
                String name = player.getGameProfile().name();
                agent.getAgentBrain().submitFromPlayer(PromptBuilder.chatInput(name, text), name);
                return "";
            }
            default -> {
                return "Unknown action " + p.action() + ".";
            }
        }
    }

    public static AgentListPayload buildList(ServerPlayer player, boolean open) {
        List<AgentListPayload.Entry> entries = new ArrayList<>();
        for (AgentEntity a : AgentManager.all(player.level().getServer())) {
            ServerPlayer owner = a.getOwnerPlayer();
            entries.add(new AgentListPayload.Entry(a.getAgentName(), owner != null ? owner.getGameProfile().name() : "offline",
                    a.getProfileId(), a.getMode().id(), trim(a.taskSummary(), 250), a.getHealth(), a.getMaxHealth(),
                    trim(a.getAutonomy().goal().text(), 250), Permissions.canCommand(player, a)));
        }
        List<String> profiles = new ArrayList<>(AiServices.router().profiles().all().keySet());
        return new AgentListPayload(entries, profiles, open);
    }

    static String trim(String s, int n) { return s.length() <= n ? s : s.substring(0, n); }

    public static void sendList(ServerPlayer player, boolean open) {
        PacketDistributor.sendToPlayer(player, buildList(player, open));
    }
}
