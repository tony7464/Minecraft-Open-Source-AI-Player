package com.joserayos.aiplayers.brain.tools;

import com.joserayos.aiplayers.agent.AgentChat;
import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import com.joserayos.aiplayers.llm.ToolSpec;
import com.joserayos.aiplayers.perception.SnapshotBuilder;
import com.joserayos.aiplayers.skill.ItemResolver;
import com.joserayos.aiplayers.skill.gather.PickupItemsTask;
import com.joserayos.aiplayers.skill.inventory.GiveItemTask;
import com.joserayos.aiplayers.skill.movement.GoToEntityTask;
import com.joserayos.aiplayers.skill.movement.GoToTask;
import com.joserayos.aiplayers.task.AgentTask;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/** All tools the model can call. Skills from later milestones register here too. */
public final class ToolRegistry {
    private static final Map<String, Tool> TOOLS = new LinkedHashMap<>();

    private ToolRegistry() {}

    public static void register(Tool tool) {
        TOOLS.put(tool.name(), tool);
    }

    public static Optional<Tool> get(String name) {
        if (name == null) return Optional.empty();
        String n = name.strip().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (n.startsWith("functions.")) n = n.substring("functions.".length());
        return Optional.ofNullable(TOOLS.get(n));
    }

    public static List<Tool> all() {
        bootstrap();
        return List.copyOf(TOOLS.values());
    }

    /** Tools offered in a given mode. */
    public static List<ToolSpec> specsFor(AgentMode mode) {
        bootstrap();
        Set<Tool.Group> groups = mode == AgentMode.AUTONOMOUS ? EnumSet.allOf(Tool.Group.class) : EnumSet.complementOf(EnumSet.of(Tool.Group.PLAN));
        List<ToolSpec> out = new ArrayList<>();
        for (Tool t : TOOLS.values()) if (groups.contains(t.group())) out.add(t.spec());
        return out;
    }

    /** Validate and run one tool call. Never throws. */
    public static ToolResult execute(AgentEntity agent, String name, ToolArgs args, ToolContext ctx) {
        bootstrap();
        Optional<Tool> tool = get(name);
        if (tool.isEmpty()) {
            return ToolResult.error("unknown tool '" + name + "'. Available: " + String.join(", ", TOOLS.keySet()));
        }
        try {
            return tool.get().handler().run(agent, args, ctx);
        } catch (ToolArgs.ToolArgException e) {
            return ToolResult.error(e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.error("tool failed: " + e);
        }
    }

    // ---- helpers for handlers ----

    /** Resolve "me", "owner", or a player name. */
    public static ServerPlayer player(AgentEntity agent, String name, ToolContext ctx) {
        ServerLevel level = (ServerLevel) agent.level();
        String n = name.strip();
        if (n.equalsIgnoreCase("me") || n.equalsIgnoreCase("owner") || n.isEmpty()) {
            ServerPlayer owner = agent.getOwnerPlayer();
            if (owner != null && (n.equalsIgnoreCase("owner") || owner.getGameProfile().name().equalsIgnoreCase(ctx.speakerName()))) return owner;
            ServerPlayer speaker = level.getServer().getPlayerList().getPlayerByName(ctx.speakerName());
            if (speaker != null && !n.equalsIgnoreCase("owner")) return speaker;
            if (owner != null) return owner;
            throw new ToolArgs.ToolArgException("your owner is offline");
        }
        ServerPlayer p = level.getServer().getPlayerList().getPlayerByName(n);
        if (p == null) throw new ToolArgs.ToolArgException("no online player named '" + n + "'");
        return p;
    }

    public static Item item(String name) {
        return ItemResolver.item(name).orElseThrow(() -> new ToolArgs.ToolArgException("unknown item '" + name + "'"));
    }

    public static ToolResult queue(AgentEntity agent, AgentTask task) {
        if (!agent.getExecutor().enqueue(task)) return ToolResult.error("task queue is full; call stop first");
        return ToolResult.task("started " + task.describe() + "; you will get a task_result when it finishes");
    }

    private static boolean bootstrapped;

    private static synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        registerCoreTools();
        ExtraTools.registerAll();
    }

    private static void registerCoreTools() {
        register(new Tool("say", "Say something in chat. Use only when you need to talk in addition to your normal reply.",
                Schema.object().str("text", "What to say (one or two short sentences)", true).build(), Tool.Group.TALK,
                (agent, args, ctx) -> {
                    AgentChat.say(agent, args.str("text"));
                    return ToolResult.done("said it");
                }));

        register(new Tool("follow_player", "Follow a player around (default: the player talking to you). Stops other tasks.",
                Schema.object().str("player", "Player name, or 'me'", false).build(), Tool.Group.MOVE,
                (agent, args, ctx) -> {
                    ServerPlayer p = player(agent, args.str("player", "me"), ctx);
                    agent.getExecutor().clear("follow");
                    agent.setFollowTarget(p.getUUID());
                    agent.setMode(AgentMode.FOLLOW);
                    return ToolResult.done("now following " + p.getGameProfile().name());
                }));

        register(new Tool("go_to", "Walk to exact block coordinates. Only use coordinates you saw in the snapshot or were told.",
                Schema.object().integer("x", "X", -30_000_000, 30_000_000, true).integer("y", "Y", -64, 320, true)
                        .integer("z", "Z", -30_000_000, 30_000_000, true).build(), Tool.Group.MOVE,
                (agent, args, ctx) -> {
                    BlockPos target = new BlockPos(args.integer("x", -30_000_000, 30_000_000), args.integer("y", -64, 320),
                            args.integer("z", -30_000_000, 30_000_000));
                    double dist = Math.sqrt(target.distToCenterSqr(agent.position()));
                    int max = com.joserayos.aiplayers.config.AiPlayersConfig.get(com.joserayos.aiplayers.config.AiPlayersConfig.MAX_GO_TO_DISTANCE);
                    if (max > 0 && dist > max) return ToolResult.error("target is " + Math.round(dist) + " blocks away; max is " + max + " (server setting maxGoToDistance)");
                    if (agent.getMode().follows()) agent.setMode(AgentMode.STAY);
                    return queue(agent, new GoToTask(target, 1.8));
                }));

        register(new Tool("explore",
                "Travel far to discover new land, resources and places. Walks in a compass direction (or 'auto' = toward land you "
                        + "haven't visited) for `distance` blocks, going around water, cliffs and walls, and reports what it saw. "
                        + "Give `looking_for` to stop as soon as that block is seen (e.g. iron_ore, log, sand).",
                Schema.object().str("direction", "north, northeast, east, southeast, south, southwest, west, northwest, or auto (default)", false)
                        .integer("distance", "Blocks to travel (default 128)", 16, 100_000, false)
                        .str("looking_for", "Optional block to search for; stops when seen", false).build(), Tool.Group.MOVE,
                (agent, args, ctx) -> {
                    String dir = args.str("direction", "auto").strip().toLowerCase(Locale.ROOT);
                    if (!dir.equals("auto") && com.joserayos.aiplayers.skill.movement.ExploreTask.vector(dir) == null) {
                        return ToolResult.error("unknown direction '" + dir + "'; use north, east, south, west, northeast... or auto");
                    }
                    int max = com.joserayos.aiplayers.config.AiPlayersConfig.get(com.joserayos.aiplayers.config.AiPlayersConfig.MAX_EXPLORE_DISTANCE);
                    int distance = Math.min(args.integer("distance", 16, 100_000, 128), max);
                    String want = args.str("looking_for", "").strip();
                    com.joserayos.aiplayers.skill.gather.BlockMatcher m = null;
                    if (!want.isEmpty()) {
                        m = com.joserayos.aiplayers.skill.gather.BlockMatcher.parse(want).orElse(null);
                        if (m == null) return ToolResult.error("unknown block '" + want + "'");
                    }
                    if (agent.getMode().follows()) agent.setMode(AgentMode.STAY);
                    return queue(agent, new com.joserayos.aiplayers.skill.movement.ExploreTask(dir, distance, m));
                }));

        register(new Tool("go_to_known",
                "Walk back to the nearest place where you remember seeing a block (from your map of explored areas), e.g. iron_ore, log, crafting_table, water.",
                Schema.object().str("block", "Block name", true).build(), Tool.Group.MOVE,
                (agent, args, ctx) -> {
                    String want = args.str("block").strip();
                    var m = com.joserayos.aiplayers.skill.gather.BlockMatcher.parse(want)
                            .orElseThrow(() -> new ToolArgs.ToolArgException("unknown block '" + want + "'"));
                    var spot = com.joserayos.aiplayers.skill.gather.KnownSpots.nearest(agent, m);
                    if (spot.isEmpty()) return ToolResult.error("you don't remember seeing any " + m.label() + "; use explore with looking_for");
                    var s = spot.get();
                    if (agent.getMode().follows()) agent.setMode(AgentMode.STAY);
                    return queue(agent, new GoToTask(new BlockPos(s.x(), s.y(), s.z()), 3.0));
                }));

        register(new Tool("go_to_player", "Walk to a player and stop next to them.",
                Schema.object().str("player", "Player name, or 'me'", false).build(), Tool.Group.MOVE,
                (agent, args, ctx) -> {
                    ServerPlayer p = player(agent, args.str("player", "me"), ctx);
                    if (agent.getMode().follows()) agent.setMode(AgentMode.STAY);
                    return queue(agent, new GoToEntityTask(p.getUUID(), p.getGameProfile().name(), 3.0));
                }));

        register(new Tool("stay", "Stop moving and wait here until told otherwise.",
                Schema.object().build(), Tool.Group.MOVE,
                (agent, args, ctx) -> {
                    agent.getExecutor().clear("stay");
                    agent.getNavigation().stop();
                    // Autonomous agents keep their mode; only the owner (/ai mode) takes them out of it.
                    if (agent.getMode() != AgentMode.AUTONOMOUS) agent.setMode(AgentMode.STAY);
                    return ToolResult.done("staying at " + agent.getBlockX() + ", " + agent.getBlockY() + ", " + agent.getBlockZ());
                }));

        register(new Tool("stop", "Cancel the current task and any queued tasks.",
                Schema.object().build(), Tool.Group.MOVE,
                (agent, args, ctx) -> {
                    agent.getExecutor().clear("stop tool");
                    agent.getNavigation().stop();
                    return ToolResult.done("stopped all tasks");
                }));

        register(new Tool("give_item", "Walk to a player and give them items from your inventory.",
                Schema.object().str("player", "Player name, or 'me'", false).str("item", "Item id, e.g. oak_log", true)
                        .integer("count", "How many", 1, 2304, false).build(), Tool.Group.ITEMS,
                (agent, args, ctx) -> {
                    ServerPlayer p = player(agent, args.str("player", "me"), ctx);
                    Item item = item(args.str("item"));
                    int count = args.integer("count", 1, 2304, 1);
                    if (agent.countItem(item) <= 0) {
                        return ToolResult.error("you have no " + ItemResolver.id(item) + ". Inventory: " + String.join(", ", agent.inventorySummary()));
                    }
                    return queue(agent, new GiveItemTask(p.getUUID(), p.getGameProfile().name(), item, count));
                }));

        register(new Tool("pickup_items", "Pick up dropped items lying on the ground nearby.",
                Schema.object().integer("radius", "Search radius in blocks (default 8)", 1, 16, false).build(), Tool.Group.GATHER,
                (agent, args, ctx) -> queue(agent, new PickupItemsTask(args.integer("radius", 1, 16, 8)))));

        register(new Tool("get_status", "Get your current status, inventory and surroundings.",
                Schema.object().build(), Tool.Group.INFO,
                (agent, args, ctx) -> ToolResult.info(SnapshotBuilder.capture(agent).toPromptText())));
    }
}
