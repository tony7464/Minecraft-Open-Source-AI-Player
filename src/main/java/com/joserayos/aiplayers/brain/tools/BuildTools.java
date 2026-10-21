package com.joserayos.aiplayers.brain.tools;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import com.joserayos.aiplayers.skill.ItemResolver;
import com.joserayos.aiplayers.skill.build.Blueprint;
import com.joserayos.aiplayers.skill.build.Blueprints;
import com.joserayos.aiplayers.skill.build.BuildTask;
import com.joserayos.aiplayers.skill.build.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** M8 tools: placing blocks, shapes and blueprints. */
final class BuildTools {
    private BuildTools() {}

    static BlockPos pos(ToolArgs args, BlockPos def) {
        if (args.has("x") && args.has("y") && args.has("z")) {
            return new BlockPos(args.integer("x", -30_000_000, 30_000_000), args.integer("y", -64, 320), args.integer("z", -30_000_000, 30_000_000));
        }
        return def;
    }

    /** Default origin: a few blocks in front of the agent, centered on its line of sight. */
    static BlockPos inFront(AgentEntity agent, int width, int length) {
        Direction d = agent.getDirection();
        BlockPos front = agent.blockPosition().relative(d, 2);
        return switch (d) {
            case NORTH -> front.offset(-width / 2, 0, -(length - 1));
            case SOUTH -> front.offset(-width / 2, 0, 0);
            case WEST -> front.offset(-(width - 1), 0, -length / 2);
            default -> front.offset(0, 0, -length / 2);
        };
    }

    static Schema xyz(Schema b) {
        return b.integer("x", "Origin X (min corner; optional)", -30_000_000, 30_000_000, false).integer("y", "Origin Y", -64, 320, false)
                .integer("z", "Origin Z", -30_000_000, 30_000_000, false);
    }

    static ToolResult start(AgentEntity agent, Blueprint bp, BlockPos origin) {
        if (agent.getMode().follows()) agent.setMode(AgentMode.STAY);
        return ToolRegistry.queue(agent, new BuildTask(bp, origin));
    }

    static void register() {
        ToolRegistry.register(new Tool("place_block", "Place one block from your inventory at a position (must be within reach after walking there).",
                xyz(Schema.object().str("item", "Block item id", true)).build(), Tool.Group.BUILD,
                (agent, args, ctx) -> {
                    var item = ToolRegistry.item(args.str("item"));
                    BlockPos p = pos(args, Placement.spotNear(agent).orElseThrow(() -> new ToolArgs.ToolArgException("no free spot next to me")));
                    return start(agent, new Blueprint("block", ItemResolver.id(item), java.util.List.of(new Blueprint.Cell(0, 0, 0, ItemResolver.id(item)))), p);
                }));

        ToolRegistry.register(new Tool("build_shape",
                "Build a simple shape: wall (width x height), floor (width x length), pillar (height), or box (hollow room with roof and doorway). "
                        + "Checks materials first and tells you what's missing.",
                xyz(Schema.object().str("shape", "wall, floor, pillar or box", true).str("material", "Block id, a tag like #minecraft:planks, or any_building", true)
                        .integer("width", "Width (x)", 1, 16, false).integer("height", "Height (y)", 1, 12, false).integer("length", "Length (z)", 1, 16, false)).build(),
                Tool.Group.BUILD,
                (agent, args, ctx) -> {
                    int w = args.integer("width", 1, 16, 5), h = args.integer("height", 1, 12, 3), l = args.integer("length", 1, 16, 5);
                    Blueprint bp;
                    try {
                        bp = Blueprint.shape(args.str("shape").strip().toLowerCase(java.util.Locale.ROOT), args.str("material"), w, h, l);
                    } catch (IllegalArgumentException e) {
                        return ToolResult.error(e.getMessage());
                    }
                    return start(agent, bp, pos(args, inFront(agent, w, l)));
                }));

        ToolRegistry.register(new Tool("build_blueprint",
                "Build a named blueprint (see list_blueprints), e.g. hut or shelter. Without coordinates it builds in front of you; "
                        + "'shelter' is built around you.",
                xyz(Schema.object().str("name", "Blueprint name", true)).build(), Tool.Group.BUILD,
                (agent, args, ctx) -> {
                    Blueprint bp = Blueprints.get(args.str("name")).orElseThrow(() ->
                            new ToolArgs.ToolArgException("no blueprint '" + args.str("name") + "'; known: " + String.join(", ", Blueprints.all().keySet())));
                    int w = bp.cells().stream().mapToInt(Blueprint.Cell::x).max().orElse(0) + 1;
                    int l = bp.cells().stream().mapToInt(Blueprint.Cell::z).max().orElse(0) + 1;
                    BlockPos def = bp.name().equals("shelter") ? agent.blockPosition().offset(-1, 0, -1) : inFront(agent, w, l);
                    return start(agent, bp, pos(args, def));
                }));

        ToolRegistry.register(new Tool("list_blueprints", "List the blueprints you can build.", Schema.object().build(), Tool.Group.INFO,
                (agent, args, ctx) -> {
                    var all = Blueprints.all();
                    StringBuilder sb = new StringBuilder();
                    for (var b : all.values()) sb.append(b.name()).append(": ").append(b.description()).append("; ");
                    return ToolResult.info(all.isEmpty() ? "no blueprints" : sb.toString());
                }));
    }
}
