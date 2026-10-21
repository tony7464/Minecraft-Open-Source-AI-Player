package com.joserayos.aiplayers.brain.tools;

import com.joserayos.aiplayers.agent.AgentChat;
import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentManager;
import com.joserayos.aiplayers.skill.movement.GoToTask;
import com.joserayos.aiplayers.team.AgentMemory;
import com.joserayos.aiplayers.team.Blackboard;
import com.joserayos.aiplayers.team.TeamStore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;

/** M7 tools: memory, shared locations, agent-to-agent messages. */
final class MemoryTools {
    private MemoryTools() {}

    static void register() {
        ToolRegistry.register(new Tool("remember", "Save a short note to long-term memory (survives restarts). Same key overwrites.",
                Schema.object().str("key", "Short key, e.g. base_location", true).str("text", "The fact to remember", true).build(),
                Tool.Group.MEMORY,
                (agent, args, ctx) -> {
                    String k = AgentMemory.of(agent.level().getServer(), agent.getUUID()).remember(args.str("key"), args.str("text"));
                    return ToolResult.done("remembered '" + k + "'");
                }));

        ToolRegistry.register(new Tool("recall", "Search long-term memory for notes matching words.",
                Schema.object().str("query", "Words to look for", true).build(), Tool.Group.MEMORY,
                (agent, args, ctx) -> {
                    var found = AgentMemory.of(agent.level().getServer(), agent.getUUID()).recall(args.str("query"));
                    return ToolResult.info(found.isEmpty() ? "no matching memories" : String.join("; ", found));
                }));

        ToolRegistry.register(new Tool("forget", "Delete a note from long-term memory.",
                Schema.object().str("key", "Key to delete", true).build(), Tool.Group.MEMORY,
                (agent, args, ctx) -> AgentMemory.of(agent.level().getServer(), agent.getUUID()).forget(args.str("key"))
                        ? ToolResult.done("forgot '" + args.str("key") + "'") : ToolResult.error("no memory named '" + args.str("key") + "'")));

        ToolRegistry.register(new Tool("set_location", "Save a named location shared with all agents (default: where you stand), e.g. base.",
                Schema.object().str("name", "Location name", true)
                        .integer("x", "X (optional)", -30_000_000, 30_000_000, false).integer("y", "Y (optional)", -64, 320, false)
                        .integer("z", "Z (optional)", -30_000_000, 30_000_000, false).build(), Tool.Group.MEMORY,
                (agent, args, ctx) -> {
                    BlockPos pos = args.has("x") && args.has("y") && args.has("z")
                            ? new BlockPos(args.integer("x", -30_000_000, 30_000_000), args.integer("y", -64, 320), args.integer("z", -30_000_000, 30_000_000))
                            : agent.blockPosition();
                    String name = args.str("name").strip();
                    if (name.isEmpty() || name.length() > 32) return ToolResult.error("name must be 1-32 characters");
                    Blackboard.setLocation(name, GlobalPos.of(agent.level().dimension(), pos));
                    TeamStore.save(agent.level().getServer());
                    return ToolResult.done("saved '" + name + "' at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
                }));

        ToolRegistry.register(new Tool("go_to_location", "Walk to a saved shared location.",
                Schema.object().str("name", "Location name", true).build(), Tool.Group.MOVE,
                (agent, args, ctx) -> {
                    GlobalPos p = Blackboard.getLocation(args.str("name").strip());
                    if (p == null) return ToolResult.error("no saved location '" + args.str("name") + "'");
                    if (p.dimension() != agent.level().dimension()) return ToolResult.error("that location is in another dimension");
                    if (agent.getMode().follows()) agent.setMode(com.joserayos.aiplayers.agent.AgentMode.STAY);
                    return ToolRegistry.queue(agent, new GoToTask(p.pos(), 2.0));
                }));

        ToolRegistry.register(new Tool("message_agent",
                "Send a short message to another AI agent (e.g. to split work). Replies chains are limited; don't chat for fun.",
                Schema.object().str("agent", "Agent name", true).str("message", "What to say", true).build(), Tool.Group.TALK,
                (agent, args, ctx) -> {
                    var server = agent.level().getServer();
                    AgentEntity to = AgentManager.byName(server, args.str("agent")).orElse(null);
                    if (to == null) return ToolResult.error("no agent named '" + args.str("agent") + "'");
                    if (to == agent) return ToolResult.error("that's you");
                    int hop = agent.nextOutgoingHop();
                    if (hop > AgentEntity.MAX_HOPS) return ToolResult.error("conversation limit reached; stop messaging and act, or ask your owner");
                    if (!agent.tryRecordAgentMessage()) return ToolResult.error("too many agent messages this minute; wait");
                    String text = AgentChat.sanitize(args.str("message"), 400);
                    AgentChat.announce(server, Component.literal("[" + agent.getAgentName() + " -> " + to.getAgentName() + "] " + text)
                            .withStyle(ChatFormatting.DARK_AQUA));
                    to.receiveAgentMessage(agent, text, hop);
                    return ToolResult.done("sent to " + to.getAgentName());
                }));
    }
}
