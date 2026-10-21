package com.joserayos.aiplayers.gametest;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import com.joserayos.aiplayers.network.AgentActionPayload;
import com.joserayos.aiplayers.network.AgentListPayload;
import com.joserayos.aiplayers.network.AiNetwork;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Agent Manager (M10) server-side GameTests: permissions and payload round trips. */
final class M10Tests {
    private M10Tests() {}

    static void registerAll(AiGameTestsSkills.Registrar r) {
        r.add("gui_owner_can_change_mode", "platform", 100, M10Tests::ownerCan);
        r.add("gui_stranger_is_refused", "platform", 100, M10Tests::strangerRefused);
        r.add("gui_payload_round_trip", "platform", 100, M10Tests::roundTrip);
    }

    private static void ownerCan(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Gui" + helper.getTick() % 1000, 5, 1, 5);
        agent.setMode(AgentMode.STAY);
        String msg = AiNetwork.apply(owner, new AgentActionPayload(agent.getAgentName(), "mode", "guard"));
        helper.assertTrue(agent.getMode() == AgentMode.GUARD, "mode is " + agent.getMode() + ": " + msg);
        AiNetwork.apply(owner, new AgentActionPayload(agent.getAgentName(), "tell", "hello from the gui"));
        helper.succeedWhen(() -> helper.assertTrue(agent.getAgentBrain().lastReply().contains("hello from the gui"), "no reply"));
    }

    private static void strangerRefused(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Mine" + helper.getTick() % 1000, 5, 1, 5);
        agent.setMode(AgentMode.STAY);
        // Pretend it belongs to someone else: a random owner id.
        agent.setOwnerId(UUID.randomUUID());
        String msg = AiNetwork.apply(owner, new AgentActionPayload(agent.getAgentName(), "despawn", ""));
        helper.assertTrue(msg.startsWith("Can't control"), "message: " + msg);
        helper.assertTrue(agent.isAlive(), "stranger despawned the agent");
        var list = AiNetwork.buildList(owner, false);
        helper.assertTrue(list.agents().stream().filter(e -> e.name().equals(agent.getAgentName())).noneMatch(AgentListPayload.Entry::canControl),
                "list says the stranger can control it");
        helper.succeed();
    }

    private static void roundTrip(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AiGameTests.agent(helper, owner, "Wire" + helper.getTick() % 1000, 5, 1, 5);
        AgentListPayload list = AiNetwork.buildList(owner, true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        AgentListPayload.CODEC.encode(buf, list);
        AgentListPayload back = AgentListPayload.CODEC.decode(buf);
        helper.assertTrue(back.equals(list), "list changed on the wire");
        AgentActionPayload action = new AgentActionPayload("Ava", "tell", "hi there");
        AgentActionPayload.CODEC.encode(buf, action);
        helper.assertTrue(AgentActionPayload.CODEC.decode(buf).equals(action), "action changed on the wire");
        helper.succeed();
    }
}
