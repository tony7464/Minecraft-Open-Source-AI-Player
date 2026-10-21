package com.joserayos.aiplayers.brain.tools;

import com.joserayos.aiplayers.agent.AgentMode;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import com.joserayos.aiplayers.skill.ItemResolver;
import com.joserayos.aiplayers.skill.gather.BlockMatcher;
import com.joserayos.aiplayers.skill.gather.CollectBlockTask;
import com.joserayos.aiplayers.skill.inventory.ContainerTask;
import com.joserayos.aiplayers.skill.inventory.GiveItemTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** M4 tools: mining/gathering and inventory management. */
final class GatherTools {
    private GatherTools() {}

    static void register() {
        ToolRegistry.register(new Tool("collect_block",
                "Find, walk to and mine blocks until you have `count` of what they drop. Use 'log' for any tree log, "
                        + "'stone' for cobblestone. Needs the right tool for stone and ores. If none are nearby it walks to a remembered spot or explores to find more.",
                Schema.object().str("block", "Block id, e.g. oak_log, log, stone, coal_ore, iron_ore", true)
                        .integer("count", "How many drops to collect", 1, 64, true)
                        .integer("radius", "Search radius (default 32). If nothing is found you automatically explore farther.", 8, 256, false).build(),
                Tool.Group.GATHER,
                (agent, args, ctx) -> {
                    if (!AiPlayersConfig.get(AiPlayersConfig.ALLOW_BREAK_BLOCKS)) return ToolResult.error("breaking blocks is disabled on this server");
                    BlockMatcher m = BlockMatcher.parse(args.str("block"))
                            .orElseThrow(() -> new ToolArgs.ToolArgException("unknown block '" + args.str("block") + "'"));
                    if (agent.getMode() == AgentMode.FOLLOW) agent.setMode(AgentMode.STAY);
                    return ToolRegistry.queue(agent, new CollectBlockTask(m, args.integer("count", 1, 64), Math.min(args.integer("radius", 8, 256, 32), com.joserayos.aiplayers.config.AiPlayersConfig.get(com.joserayos.aiplayers.config.AiPlayersConfig.MAX_COLLECT_RADIUS))));
                }));

        ToolRegistry.register(new Tool("equip", "Hold an item from your inventory in your main hand.",
                Schema.object().str("item", "Item id, e.g. stone_pickaxe", true).build(), Tool.Group.ITEMS,
                (agent, args, ctx) -> {
                    Item item = ToolRegistry.item(args.str("item"));
                    if (agent.getMainHandItem().is(item)) return ToolResult.done("already holding " + ItemResolver.id(item));
                    var inv = agent.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack s = inv.getItem(i);
                        if (s.is(item)) {
                            inv.setItem(i, agent.getMainHandItem());
                            agent.setItemSlot(EquipmentSlot.MAINHAND, s);
                            return ToolResult.done("now holding " + ItemResolver.id(item));
                        }
                    }
                    return ToolResult.error("you have no " + ItemResolver.id(item));
                }));

        ToolRegistry.register(new Tool("drop_item", "Drop items from your inventory on the ground in front of you.",
                Schema.object().str("item", "Item id", true).integer("count", "How many", 1, 2304, false).build(), Tool.Group.ITEMS,
                (agent, args, ctx) -> {
                    Item item = ToolRegistry.item(args.str("item"));
                    ItemStack removed = agent.removeItems(item, args.integer("count", 1, 2304, 1));
                    if (removed.isEmpty()) return ToolResult.error("you have no " + ItemResolver.id(item));
                    int n = removed.getCount();
                    GiveItemTask.toss(agent, agent, removed);
                    return ToolResult.done("dropped " + n + " " + ItemResolver.id(item));
                }));

        ToolRegistry.register(new Tool("deposit", "Walk to a chest and put items in it (omit item to deposit everything except your held item).",
                Schema.object().integer("x", "Chest X", -30_000_000, 30_000_000, true).integer("y", "Chest Y", -64, 320, true)
                        .integer("z", "Chest Z", -30_000_000, 30_000_000, true).str("item", "Item id (optional)", false)
                        .integer("count", "How many (default all)", 1, 2304, false).build(), Tool.Group.ITEMS,
                (agent, args, ctx) -> {
                    if (!AiPlayersConfig.get(AiPlayersConfig.ALLOW_CONTAINER_ACCESS)) return ToolResult.error("chest access is disabled on this server");
                    BlockPos pos = new BlockPos(args.integer("x", -30_000_000, 30_000_000), args.integer("y", -64, 320), args.integer("z", -30_000_000, 30_000_000));
                    Item item = args.has("item") ? ToolRegistry.item(args.str("item")) : null;
                    return ToolRegistry.queue(agent, new ContainerTask(ContainerTask.Kind.DEPOSIT, pos, item, args.integer("count", 1, 2304, 2304)));
                }));

        ToolRegistry.register(new Tool("withdraw", "Walk to a chest and take items out of it.",
                Schema.object().integer("x", "Chest X", -30_000_000, 30_000_000, true).integer("y", "Chest Y", -64, 320, true)
                        .integer("z", "Chest Z", -30_000_000, 30_000_000, true).str("item", "Item id", true)
                        .integer("count", "How many", 1, 2304, true).build(), Tool.Group.ITEMS,
                (agent, args, ctx) -> {
                    if (!AiPlayersConfig.get(AiPlayersConfig.ALLOW_CONTAINER_ACCESS)) return ToolResult.error("chest access is disabled on this server");
                    BlockPos pos = new BlockPos(args.integer("x", -30_000_000, 30_000_000), args.integer("y", -64, 320), args.integer("z", -30_000_000, 30_000_000));
                    return ToolRegistry.queue(agent, new ContainerTask(ContainerTask.Kind.WITHDRAW, pos, ToolRegistry.item(args.str("item")),
                            args.integer("count", 1, 2304)));
                }));

        ToolRegistry.register(new Tool("inventory_summary", "List what is in your inventory and hands.",
                Schema.object().build(), Tool.Group.INFO,
                (agent, args, ctx) -> ToolResult.info("held: " + com.joserayos.aiplayers.perception.SnapshotBuilder.itemName(agent.getMainHandItem())
                        + "; inventory: " + (agent.inventorySummary().isEmpty() ? "empty" : String.join(", ", agent.inventorySummary())))));
    }
}
