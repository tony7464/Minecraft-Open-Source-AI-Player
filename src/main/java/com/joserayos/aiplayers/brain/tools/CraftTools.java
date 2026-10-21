package com.joserayos.aiplayers.brain.tools;

import com.joserayos.aiplayers.skill.build.Placement;
import com.joserayos.aiplayers.skill.craft.CraftTask;
import com.joserayos.aiplayers.skill.craft.Crafter;
import com.joserayos.aiplayers.skill.craft.SmeltTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** M5 tools: crafting and smelting. */
final class CraftTools {
    private CraftTools() {}

    static void register() {
        ToolRegistry.register(new Tool("craft_item",
                "Craft an item from your inventory. Automatically crafts intermediate items (planks, sticks) and places "
                        + "a crafting table if the recipe needs one. Fails with what's missing.",
                Schema.object().str("item", "Item id, e.g. stone_pickaxe", true).integer("count", "How many", 1, 64, false).build(),
                Tool.Group.ITEMS,
                (agent, args, ctx) -> ToolRegistry.queue(agent, new CraftTask(ToolRegistry.item(args.str("item")), args.integer("count", 1, 64, 1)))));

        ToolRegistry.register(new Tool("list_craftable", "List items you can craft right now from your inventory.",
                Schema.object().build(), Tool.Group.INFO,
                (agent, args, ctx) -> {
                    boolean table = agent.countItem(Items.CRAFTING_TABLE) > 0
                            || Placement.findNearest(agent, CraftTask.TABLE_SEARCH_RADIUS, s -> s.is(Blocks.CRAFTING_TABLE)).isPresent();
                    var list = new Crafter((ServerLevel) agent.level()).craftableNow(agent, table);
                    if (list.isEmpty()) return ToolResult.info("nothing craftable with the current inventory");
                    return ToolResult.info("craftable: " + String.join(", ", list.subList(0, Math.min(40, list.size())))
                            + (list.size() > 40 ? " (+" + (list.size() - 40) + " more)" : ""));
                }));

        ToolRegistry.register(new Tool("smelt",
                "Smelt items in a nearby furnace (or place one from your inventory). `item` may be the input (raw_iron) "
                        + "or the wanted output (iron_ingot). Uses fuel from your inventory.",
                Schema.object().str("item", "Item to smelt or to produce", true).integer("count", "How many", 1, 64, false).build(),
                Tool.Group.ITEMS,
                (agent, args, ctx) -> ToolRegistry.queue(agent, new SmeltTask(args.str("item"), args.integer("count", 1, 64, 1)))));
    }
}
