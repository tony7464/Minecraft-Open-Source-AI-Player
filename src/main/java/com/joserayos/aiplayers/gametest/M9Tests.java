package com.joserayos.aiplayers.gametest;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import com.joserayos.aiplayers.agent.autonomy.Goal;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Autonomous-mode GameTests (M9). */
final class M9Tests {
    private M9Tests() {}

    static void registerAll(AiGameTestsSkills.Registrar r) {
        r.add("autonomous_heartbeat", "platform", 800, M9Tests::heartbeat);
        r.add("goal_verified_by_inventory", "platform", 400, M9Tests::verified);
        r.add("autonomous_recovers_from_no_tool_replies", "forest", 3000, M9Tests::noToolFallback);
    }

    private static void heartbeat(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Solo", 5, 1, 5);
        agent.setMode(AgentMode.AUTONOMOUS);
        helper.succeedWhen(() -> helper.assertTrue(agent.getAgentBrain().history().stream()
                        .anyMatch(m -> m.content().contains("[heartbeat]") && m.content().contains("collect 4 logs")),
                "no heartbeat with the first curriculum goal yet"));
    }

    private static void verified(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Checker", 5, 1, 5);
        agent.setMode(AgentMode.AUTONOMOUS);
        AiGameTestsSkills.say(agent, owner, "!set_goal {\"goal\":\"get two logs\",\"item\":\"oak_log\",\"count\":2}");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(agent.getAutonomy().goal().item().equals("oak_log"), "goal not set: " + agent.getAutonomy().goal());
            agent.addItem(new ItemStack(Items.OAK_LOG, 2));
        });
        helper.runAfterDelay(41, () -> helper.succeedWhen(() -> {
            helper.assertTrue(agent.getAutonomy().goal() == Goal.NONE, "goal still open");
            helper.assertTrue(agent.recentEvents().stream().anyMatch(e -> e.contains("goal done (verified)")), "no event");
        }));
    }

    /**
     * Regression for "chops a tree, then stands still forever": the mock model only ever answers with plain text
     * (no tool call). The agent must keep getting heartbeats, get nudged, then run the curriculum goal itself.
     */
    private static void noToolFallback(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Idler", 7, 1, 7);
        agent.setMode(AgentMode.AUTONOMOUS);
        helper.succeedWhen(() -> {
            helper.assertTrue(agent.getAgentBrain().history().stream().anyMatch(m -> m.content().contains("[nudge]")), "never nudged; "
                    + agent.getAgentBrain().lastOutcome() + " | " + agent.getAutonomy().idleBlocker());
            helper.assertTrue(agent.countItem(Items.OAK_LOG) >= 4, "fallback didn't collect logs: " + agent.countItem(Items.OAK_LOG)
                    + "; " + agent.getAgentBrain().lastOutcome() + " | " + agent.getAutonomy().idleBlocker() + " | " + agent.lastResult());
            helper.assertTrue(!agent.isThinking() && !agent.getAgentBrain().isBusy(), "still busy");
        });
    }
}
