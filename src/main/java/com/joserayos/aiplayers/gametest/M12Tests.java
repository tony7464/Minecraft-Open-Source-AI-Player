package com.joserayos.aiplayers.gametest;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** Performance GameTest (M12): five busy agents must stay cheap per tick. */
final class M12Tests {
    private M12Tests() {}

    static void registerAll(AiGameTestsSkills.Registrar r) {
        r.add("perf_five_agents", "forest", 400, M12Tests::fiveAgents);
    }

    private static void fiveAgents(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity[] agents = new AgentEntity[5];
        for (int i = 0; i < 5; i++) {
            agents[i] = AiGameTests.agent(helper, owner, "Perf" + i, 2 + i * 2, 1, 2);
        }
        agents[0].setMode(AgentMode.FOLLOW);
        agents[1].setMode(AgentMode.GUARD);
        agents[2].setMode(AgentMode.STAY);
        agents[3].setMode(AgentMode.IDLE);
        agents[4].setMode(AgentMode.STAY);
        AiGameTestsSkills.say(agents[2], owner, "!collect_block {\"block\":\"log\",\"count\":2,\"radius\":12}");
        AiGameTestsSkills.say(agents[4], owner, "!go_to {" + M8Tests.at(helper, 14, 1, 14) + "}");
        // Agent tick cost over 200 game ticks (counters are server-wide, so other tests' agents count too).
        long n0 = AgentEntity.TICK_COUNT.sum();
        long ns0 = AgentEntity.TICK_NANOS.sum();
        helper.runAfterDelay(200, () -> {
            long n = AgentEntity.TICK_COUNT.sum() - n0;
            long ns = AgentEntity.TICK_NANOS.sum() - ns0;
            double avgMicros = n == 0 ? 0 : ns / 1000.0 / n;
            helper.assertTrue(n > 0, "no agent ticks measured");
            helper.assertTrue(avgMicros < 1500, String.format("agents too slow: %.0f µs per agent tick", avgMicros));
            com.joserayos.aiplayers.AiPlayersMod.LOGGER.info("perf_five_agents: {} agent ticks, avg {} µs", n, String.format("%.1f", avgMicros));
            helper.succeed();
        });
    }
}
