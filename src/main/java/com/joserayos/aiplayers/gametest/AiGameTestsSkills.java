package com.joserayos.aiplayers.gametest;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Skill GameTests driven through the full brain loop with the offline mock model:
 * the mock turns "!tool {json}" in a chat message into a tool call.
 */
final class AiGameTestsSkills {
    interface Registrar {
        void add(String name, String structure, int maxTicks, Consumer<GameTestHelper> fn);
    }

    private AiGameTestsSkills() {}

    static void registerAll(Registrar r) {
        r.add("tool_go_to", "platform", 400, AiGameTestsSkills::toolGoTo);
        r.add("tool_give_item", "platform", 400, AiGameTestsSkills::toolGiveItem);
        r.add("tool_pickup_items", "platform", 400, AiGameTestsSkills::toolPickupItems);
        r.add("tool_unknown_is_reported", "platform", 200, AiGameTestsSkills::unknownTool);
        M4Tests.registerAll(r);
        M5Tests.registerAll(r);
        M6Tests.registerAll(r);
        M7Tests.registerAll(r);
        M8Tests.registerAll(r);
        M9Tests.registerAll(r);
        M10Tests.registerAll(r);
        M12Tests.registerAll(r);
        M13Tests.registerAll(r);
    }

    static void say(AgentEntity agent, ServerPlayer from, String text) {
        agent.getAgentBrain().submitFromPlayer("<chat from=\"" + from.getGameProfile().name() + "\">" + text + "</chat>", from.getGameProfile().name());
    }

    private static void toolGoTo(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Walker", 3, 1, 3);
        agent.setMode(AgentMode.STAY);
        BlockPos target = helper.absolutePos(new BlockPos(18, 1, 15));
        say(agent, owner, "!go_to {\"x\":" + target.getX() + ",\"y\":" + target.getY() + ",\"z\":" + target.getZ() + "}");
        helper.succeedWhen(() -> {
            helper.assertTrue(agent.lastResult().startsWith("go_to SUCCESS"), "last result: " + agent.lastResult());
            helper.assertTrue(agent.position().distanceTo(target.getBottomCenter()) < 2.5, "not at target");
        });
    }

    private static void toolGiveItem(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 2, 1, 2);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Giver", 14, 1, 14);
        agent.setMode(AgentMode.STAY);
        agent.addItem(new ItemStack(Items.OAK_LOG, 5));
        say(agent, owner, "!give_item {\"item\":\"oak logs\",\"count\":3}");
        helper.succeedWhen(() -> {
            helper.assertTrue(agent.lastResult().startsWith("give_item SUCCESS"), "last result: " + agent.lastResult());
            helper.assertTrue(agent.countItem(Items.OAK_LOG) == 2, "agent should have 2 logs left, has " + agent.countItem(Items.OAK_LOG));
            helper.assertTrue(agent.distanceTo(owner) < 4, "agent didn't walk to the player");
        });
    }

    private static void toolPickupItems(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 2, 1, 2);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Picker", 4, 1, 4);
        agent.setMode(AgentMode.STAY);
        // Make sure the drop spot is open floor (an item resting on a block is out of the agent's reach).
        helper.setBlock(new BlockPos(7, 1, 6), net.minecraft.world.level.block.Blocks.AIR);
        helper.setBlock(new BlockPos(7, 2, 6), net.minecraft.world.level.block.Blocks.AIR);
        BlockPos drop = helper.absolutePos(new BlockPos(7, 1, 6));
        ItemEntity item = new ItemEntity(helper.getLevel(), drop.getX() + 0.5, drop.getY() + 0.2, drop.getZ() + 0.5,
                new ItemStack(Items.COBBLESTONE, 4), 0, 0, 0); // no random toss: it must land on the open floor
        item.setNoPickUpDelay();
        // Spawn after the batch has finished setting up neighbouring test areas.
        helper.runAfterDelay(5, () -> {
            helper.getLevel().addFreshEntity(item);
        });
        // Let the item land first; a falling item is treated as unreachable.
        helper.runAfterDelay(30, () -> say(agent, owner, "!pickup_items {\"radius\":8}"));
        helper.succeedWhen(() -> helper.assertTrue(agent.countItem(Items.COBBLESTONE) == 4,
                "has " + agent.countItem(Items.COBBLESTONE) + " cobblestone; item " + (item.isRemoved() ? "removed: " + item.getRemovalReason() : "alive at " + item.blockPosition())
                        + "; " + agent.lastResult()));
    }

    private static void unknownTool(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 2, 1, 2);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Confused", 4, 1, 4);
        agent.setMode(AgentMode.STAY);
        say(agent, owner, "!fly_to_moon {}");
        // The mock answers the follow-up turn with "Done: <tool result>", so the error must reach the model.
        helper.succeedWhen(() -> helper.assertTrue(agent.getAgentBrain().lastReply().contains("unknown tool 'fly_to_moon'"),
                "reply: " + agent.getAgentBrain().lastReply()));
    }
}
