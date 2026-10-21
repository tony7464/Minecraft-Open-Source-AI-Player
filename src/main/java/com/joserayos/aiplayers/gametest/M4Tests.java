package com.joserayos.aiplayers.gametest;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Gathering GameTests (M4). */
final class M4Tests {
    private M4Tests() {}

    static void registerAll(AiGameTestsSkills.Registrar r) {
        r.add("collect_logs", "forest", 1200, M4Tests::collectLogs);
        r.add("collect_cobblestone_with_pickaxe", "forest", 1200, M4Tests::collectCobble);
        r.add("collect_stone_needs_pickaxe", "forest", 600, M4Tests::needsPickaxe);
        r.add("deposit_into_chest", "platform", 600, M4Tests::deposit);
    }

    private static void collectLogs(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Lumberjack", 7, 1, 7);
        agent.setMode(AgentMode.STAY);
        AiGameTestsSkills.say(agent, owner, "!collect_block {\"block\":\"log\",\"count\":4,\"radius\":12}");
        helper.succeedWhen(() -> helper.assertTrue(agent.countItem(Items.OAK_LOG) >= 4,
                "has " + agent.countItem(Items.OAK_LOG) + " logs; " + agent.lastResult()));
    }

    private static void collectCobble(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Miner", 6, 1, 8);
        agent.setMode(AgentMode.STAY);
        agent.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        AiGameTestsSkills.say(agent, owner, "!collect_block {\"block\":\"cobblestone\",\"count\":3,\"radius\":10}");
        helper.succeedWhen(() -> helper.assertTrue(agent.countItem(Items.COBBLESTONE) >= 3,
                "has " + agent.countItem(Items.COBBLESTONE) + " cobblestone; " + agent.lastResult()));
    }

    private static void needsPickaxe(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Barehands", 6, 1, 8);
        agent.setMode(AgentMode.STAY);
        AiGameTestsSkills.say(agent, owner, "!collect_block {\"block\":\"cobblestone\",\"count\":2,\"radius\":10}");
        helper.succeedWhen(() -> {
            helper.assertTrue(agent.lastResult().contains("FAILED"), "last result: " + agent.lastResult());
            helper.assertTrue(agent.lastResult().contains("pickaxe"), "should mention needing a pickaxe: " + agent.lastResult());
            helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(3, 1, 10));
        });
    }

    private static void deposit(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Storer", 4, 1, 4);
        agent.setMode(AgentMode.STAY);
        BlockPos rel = new BlockPos(14, 1, 12);
        helper.setBlock(rel, Blocks.CHEST);
        BlockPos chest = helper.absolutePos(rel);
        agent.addItem(new ItemStack(Items.COBBLESTONE, 20));
        agent.addItem(new ItemStack(Items.OAK_LOG, 3));
        AiGameTestsSkills.say(agent, owner, "!deposit {\"x\":" + chest.getX() + ",\"y\":" + chest.getY() + ",\"z\":" + chest.getZ()
                + ",\"item\":\"cobblestone\"}");
        helper.succeedWhen(() -> {
            Container c = (Container) helper.getBlockEntity(rel, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            int n = 0;
            for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(Items.COBBLESTONE)) n += c.getItem(i).getCount();
            helper.assertTrue(n == 20, "chest has " + n + " cobblestone; " + agent.lastResult());
            helper.assertTrue(agent.countItem(Items.OAK_LOG) == 3, "logs should stay with the agent");
        });
    }
}
