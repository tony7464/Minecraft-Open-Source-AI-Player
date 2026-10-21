package com.joserayos.aiplayers.gametest;

import com.joserayos.aiplayers.agent.AgentEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Exploration GameTests (M13): explore tool, auto-explore in collect_block, remembered resource map. */
final class M13Tests {
    private M13Tests() {}

    static void registerAll(AiGameTestsSkills.Registrar r) {
        r.add("explore_walks_in_a_direction", "platform", 600, M13Tests::exploreEast);
        r.add("collect_block_searches_farther", "platform", 1200, M13Tests::collectFarther);
        r.add("resource_map_remembers_scan", "platform", 400, M13Tests::remembers);
    }

    private static void exploreEast(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Scout", 2, 1, 2);
        // Head across the platform (test structures may be rotated, so compute the compass direction).
        BlockPos far = helper.absolutePos(new BlockPos(20, 1, 20));
        double[] start = new double[2];
        helper.runAfterDelay(2, () -> {
            start[0] = agent.getX();
            start[1] = agent.getZ();
            String dir = com.joserayos.aiplayers.perception.SnapshotBuilder.compass(far.getX() + 0.5 - agent.getX(), far.getZ() + 0.5 - agent.getZ()).toLowerCase();
            AiGameTestsSkills.say(agent, owner, "!explore {\"direction\":\"" + dir + "\",\"distance\":16}");
        });
        helper.runAfterDelay(3, () -> helper.succeedWhen(() -> {
            double moved = Math.hypot(agent.getX() - start[0], agent.getZ() - start[1]);
            helper.assertTrue(moved >= 12, "moved only " + Math.round(moved) + " blocks; mode " + agent.getMode().id() + "; " + agent.lastResult());
            helper.assertTrue(agent.lastResult().contains("explored"), "no explore result yet: " + agent.lastResult());
        }));
    }

    /** No log within the search radius: collect_block must look farther (explore/remembered spot) instead of failing. */
    private static void collectFarther(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        helper.setBlock(new BlockPos(21, 1, 21), Blocks.OAK_LOG);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Seeker", 3, 1, 3);
        AiGameTestsSkills.say(agent, owner, "!collect_block {\"block\":\"log\",\"count\":1,\"radius\":8}");
        helper.succeedWhen(() -> helper.assertTrue(agent.countItem(Items.OAK_LOG) >= 1,
                "no log yet; " + agent.taskSummary() + " | " + agent.lastResult()));
    }

    private static void remembers(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        helper.setBlock(new BlockPos(12, 1, 12), Blocks.CRAFTING_TABLE);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Mapper", 3, 1, 3);
        helper.succeedWhen(() -> {
            var spot = agent.getResourceMap().nearest(agent.dimensionId(), agent.getX(), agent.getY(), agent.getZ(), t -> t.equals("crafting_table"));
            helper.assertTrue(spot.isPresent(), "crafting table not remembered");
            helper.assertTrue(spot.get().x() == helper.absolutePos(new BlockPos(12, 1, 12)).getX(), "wrong spot " + spot.get());
            helper.assertTrue(agent.getResourceMap().visitedCount() >= 1, "no visited area");
        });
    }
}
