package com.joserayos.aiplayers.gametest;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import com.joserayos.aiplayers.team.AgentMemory;
import com.joserayos.aiplayers.team.Blackboard;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Multi-agent and memory GameTests (M7). */
final class M7Tests {
    private M7Tests() {}

    static void registerAll(AiGameTestsSkills.Registrar r) {
        r.add("remember_and_recall", "platform", 200, M7Tests::remember);
        r.add("message_agent_delivers", "platform", 300, M7Tests::message);
        r.add("two_agents_split_logs", "forest", 1600, M7Tests::splitLogs);
        r.add("shared_location", "platform", 400, M7Tests::sharedLocation);
    }

    private static void remember(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Memo", 5, 1, 5);
        agent.setMode(AgentMode.STAY);
        AiGameTestsSkills.say(agent, owner, "!remember {\"key\":\"favorite food\",\"text\":\"owner likes baked potatoes\"}");
        helper.succeedWhen(() -> {
            var mem = AgentMemory.of(helper.getLevel().getServer(), agent.getUUID());
            helper.assertTrue("owner likes baked potatoes".equals(mem.notes().get("favorite_food")), "notes: " + mem.notes());
            helper.assertTrue(mem.recall("potatoes").size() == 1, "recall failed");
            helper.assertTrue(com.joserayos.aiplayers.team.TeamPrompt.extra(agent).contains("baked potatoes"), "memory not in prompt");
        });
    }

    private static void message(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity ava = AiGameTests.agent(helper, owner, "Ava" + helper.getTick() % 1000, 4, 1, 4);
        AgentEntity bo = AiGameTests.agent(helper, owner, "Bo" + helper.getTick() % 1000, 8, 1, 8);
        ava.setMode(AgentMode.STAY);
        bo.setMode(AgentMode.STAY);
        AiGameTestsSkills.say(ava, owner, "!message_agent {\"agent\":\"" + bo.getAgentName() + "\",\"message\":\"you take the logs\"}");
        helper.succeedWhen(() -> {
            boolean got = bo.getAgentBrain().history().stream().anyMatch(m -> m.content().contains("you take the logs")
                    && m.content().contains("agent_message from=\"" + ava.getAgentName() + "\""));
            helper.assertTrue(got, "Bo never got the message");
        });
    }

    /** Two agents gather from the same 3 trees; claims must stop them from mining the same block. */
    private static void splitLogs(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity a = AiGameTests.agent(helper, owner, "LogA", 7, 1, 6);
        AgentEntity b = AiGameTests.agent(helper, owner, "LogB", 8, 1, 9);
        a.setMode(AgentMode.STAY);
        b.setMode(AgentMode.STAY);
        AiGameTestsSkills.say(a, owner, "!collect_block {\"block\":\"log\",\"count\":4,\"radius\":12}");
        AiGameTestsSkills.say(b, owner, "!collect_block {\"block\":\"log\",\"count\":4,\"radius\":12}");
        helper.succeedWhen(() -> {
            helper.assertTrue(a.countItem(Items.OAK_LOG) >= 4 && b.countItem(Items.OAK_LOG) >= 4,
                    "A " + a.countItem(Items.OAK_LOG) + " (" + a.lastResult() + "), B " + b.countItem(Items.OAK_LOG) + " (" + b.lastResult() + ")");
            helper.assertTrue(a.countItem(Items.OAK_LOG) + b.countItem(Items.OAK_LOG) <= 9, "more logs than exist");
        });
    }

    private static void sharedLocation(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity a = AiGameTests.agent(helper, owner, "Scout", 18, 1, 18);
        AgentEntity b = AiGameTests.agent(helper, owner, "Runner", 3, 1, 3);
        a.setMode(AgentMode.STAY);
        b.setMode(AgentMode.STAY);
        String name = "camp" + helper.absolutePos(net.minecraft.core.BlockPos.ZERO).getX();
        AiGameTestsSkills.say(a, owner, "!set_location {\"name\":\"" + name + "\"}");
        helper.runAfterDelay(20, () -> AiGameTestsSkills.say(b, owner, "!go_to_location {\"name\":\"" + name + "\"}"));
        helper.succeedWhen(() -> {
            helper.assertTrue(Blackboard.getLocation(name) != null, "location not saved");
            helper.assertTrue(b.distanceTo(a) < 3.5, "runner didn't reach the shared location: " + b.lastResult());
        });
    }
}
