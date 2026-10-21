package com.joserayos.aiplayers.gametest;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;

/** Building GameTests (M8). */
final class M8Tests {
    private M8Tests() {}

    static void registerAll(AiGameTestsSkills.Registrar r) {
        r.add("build_hut", "platform", 2400, M8Tests::hut);
        r.add("build_reports_missing_materials", "platform", 200, M8Tests::missing);
        r.add("build_wall_shape", "platform", 800, M8Tests::wall);
        r.add("build_shelter_around_self", "platform", 800, M8Tests::shelter);
    }

    static String at(GameTestHelper h, int x, int y, int z) {
        BlockPos p = h.absolutePos(new BlockPos(x, y, z));
        return "\"x\":" + p.getX() + ",\"y\":" + p.getY() + ",\"z\":" + p.getZ();
    }

    private static void hut(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Builder", 10, 1, 4);
        agent.setMode(AgentMode.STAY);
        agent.addItem(new ItemStack(Items.COBBLESTONE, 64));
        agent.addItem(new ItemStack(Items.SPRUCE_PLANKS, 32));
        agent.addItem(new ItemStack(Items.SPRUCE_DOOR, 1));
        AiGameTestsSkills.say(agent, owner, "!build_blueprint {\"name\":\"hut\"," + at(helper, 8, 1, 8) + "}");
        helper.succeedWhen(() -> {
            helper.assertTrue(agent.lastResult().contains("SUCCESS"), "result: " + agent.lastResult());
            helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(8, 1, 8));
            helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(12, 3, 12));
            helper.assertBlockPresent(Blocks.SPRUCE_PLANKS, new BlockPos(10, 4, 10));
            helper.assertBlockPresent(Blocks.SPRUCE_DOOR, new BlockPos(10, 1, 8));
            helper.assertBlockPresent(Blocks.SPRUCE_DOOR, new BlockPos(10, 2, 8));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(10, 1, 10));
            helper.assertTrue(helper.getBlockState(new BlockPos(10, 2, 8)).getValue(DoorBlock.HALF).name().equals("UPPER"), "door top");
        });
    }

    private static void missing(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Poor", 4, 1, 4);
        agent.setMode(AgentMode.STAY);
        agent.addItem(new ItemStack(Items.COBBLESTONE, 10));
        AiGameTestsSkills.say(agent, owner, "!build_blueprint {\"name\":\"hut\"," + at(helper, 8, 1, 8) + "}");
        helper.succeedWhen(() -> {
            helper.assertTrue(agent.lastResult().contains("missing materials") && agent.lastResult().contains("36 more cobblestone"),
                    "result: " + agent.lastResult());
            helper.assertBlockNotPresent(Blocks.COBBLESTONE, new BlockPos(8, 1, 8));
            helper.assertTrue(agent.countItem(Items.COBBLESTONE) == 10, "nothing should be used");
        });
    }

    private static void wall(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Mason", 6, 1, 6);
        agent.setMode(AgentMode.STAY);
        agent.addItem(new ItemStack(Items.OAK_PLANKS, 12));
        AiGameTestsSkills.say(agent, owner, "!build_shape {\"shape\":\"wall\",\"material\":\"#minecraft:planks\",\"width\":4,\"height\":3," + at(helper, 4, 1, 10) + "}");
        helper.succeedWhen(() -> {
            for (int x = 4; x < 8; x++) for (int y = 1; y < 4; y++) helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(x, y, 10));
            helper.assertTrue(agent.countItem(Items.OAK_PLANKS) == 0, "planks left: " + agent.countItem(Items.OAK_PLANKS));
        });
    }

    private static void shelter(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Camper", 12, 1, 12);
        agent.setMode(AgentMode.STAY);
        agent.addItem(new ItemStack(Items.DIRT, 30));
        AiGameTestsSkills.say(agent, owner, "!build_blueprint {\"name\":\"shelter\"}");
        helper.succeedWhen(() -> {
            helper.assertTrue(agent.lastResult().contains("SUCCESS"), "result: " + agent.lastResult());
            BlockPos p = agent.blockPosition();
            var level = helper.getLevel();
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                helper.assertTrue(level.getBlockState(p.relative(d)).is(Blocks.DIRT), "open side " + d);
                helper.assertTrue(level.getBlockState(p.relative(d).above()).is(Blocks.DIRT), "open upper side " + d);
            }
            helper.assertTrue(level.getBlockState(p.above(2)).is(Blocks.DIRT), "no roof");
        });
    }
}
