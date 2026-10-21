package com.joserayos.aiplayers.gametest;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import com.joserayos.aiplayers.skill.build.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Crafting and smelting GameTests (M5). */
final class M5Tests {
    private M5Tests() {}

    static void registerAll(AiGameTestsSkills.Registrar r) {
        r.add("craft_stone_pickaxe_from_logs", "platform", 600, M5Tests::stonePickaxe);
        r.add("craft_reports_missing", "platform", 200, M5Tests::missing);
        r.add("smelt_raw_iron", "platform", 1600, M5Tests::smelt);
    }

    private static void stonePickaxe(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Smith", 10, 1, 10);
        agent.setMode(AgentMode.STAY);
        agent.addItem(new ItemStack(Items.OAK_LOG, 2));
        agent.addItem(new ItemStack(Items.COBBLESTONE, 3));
        AiGameTestsSkills.say(agent, owner, "!craft_item {\"item\":\"stone_pickaxe\"}");
        helper.succeedWhen(() -> {
            helper.assertTrue(agent.countItem(Items.STONE_PICKAXE) == 1, "no pickaxe; " + agent.lastResult());
            helper.assertTrue(Placement.findNearest(agent, 4, s -> s.is(Blocks.CRAFTING_TABLE)).isPresent(), "no crafting table placed");
            helper.assertTrue(agent.countItem(Items.COBBLESTONE) == 0, "cobblestone not used");
        });
    }

    private static void missing(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Broke", 5, 1, 5);
        agent.setMode(AgentMode.STAY);
        agent.addItem(new ItemStack(Items.OAK_LOG, 1));
        agent.addItem(new ItemStack(Items.CRAFTING_TABLE, 1));
        AiGameTestsSkills.say(agent, owner, "!craft_item {\"item\":\"stone_pickaxe\"}");
        helper.succeedWhen(() -> {
            helper.assertTrue(agent.lastResult().contains("FAILED") && agent.lastResult().contains("cobblestone"),
                    "should say cobblestone is missing: " + agent.lastResult());
            helper.assertTrue(agent.countItem(Items.OAK_LOG) == 1, "nothing should be consumed");
        });
    }

    private static void smelt(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Smelter", 5, 1, 5);
        agent.setMode(AgentMode.STAY);
        helper.setBlock(new BlockPos(12, 1, 12), Blocks.FURNACE);
        agent.addItem(new ItemStack(Items.RAW_IRON, 3));
        agent.addItem(new ItemStack(Items.COAL, 1));
        AiGameTestsSkills.say(agent, owner, "!smelt {\"item\":\"iron_ingot\",\"count\":3}");
        helper.succeedWhen(() -> helper.assertTrue(agent.countItem(Items.IRON_INGOT) == 3,
                "has " + agent.countItem(Items.IRON_INGOT) + " ingots; " + agent.lastResult()));
    }
}
