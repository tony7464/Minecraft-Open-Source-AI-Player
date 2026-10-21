package com.joserayos.aiplayers.gametest;

import com.joserayos.aiplayers.AiPlayersMod;
import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import com.joserayos.aiplayers.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-world GameTests. Run all of them with {@code ./gradlew runGameTestServer}
 * or in-game with {@code /test runall}.
 */
public final class AiGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, AiPlayersMod.MODID);

    private record TestDef(String name, String structure, int maxTicks) {}

    private static final List<TestDef> TESTS = new ArrayList<>();

    private AiGameTests() {}

    private static void test(String name, String structure, int maxTicks, Consumer<GameTestHelper> fn) {
        FUNCTIONS.register(name, () -> fn);
        TESTS.add(new TestDef(name, structure, maxTicks));
    }

    static {
        test("agent_follows_owner", "platform", 400, AiGameTests::agentFollowsOwner);
        test("agent_replies_with_mock_model", "platform", 200, AiGameTests::agentRepliesWithMockModel);
        AiGameTestsSkills.registerAll(AiGameTests::test);
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(AiGameTests::onRegisterTests);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf());
        for (TestDef def : TESTS) {
            ResourceKey<Consumer<GameTestHelper>> fn = ResourceKey.create(Registries.TEST_FUNCTION, id(def.name()));
            event.registerTest(id(def.name()), new FunctionGameTestInstance(fn,
                    new TestData<>(env, id(def.structure()), def.maxTicks(), 0, true, Rotation.NONE)));
        }
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AiPlayersMod.MODID, path);
    }

    // ---- shared helpers ----

    /** Creates a mock owner player standing at a relative position. */
    @SuppressWarnings("removal")
    static ServerPlayer owner(GameTestHelper helper, int x, int y, int z) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(x, y, z));
        player.snapTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
        return player;
    }

    static AgentEntity agent(GameTestHelper helper, ServerPlayer owner, String name, int x, int y, int z) {
        AgentEntity agent = helper.spawn(ModEntities.AGENT.get(), x, y, z, EntitySpawnReason.COMMAND);
        agent.setOwnerId(owner.getUUID());
        agent.setAgentName(name);
        agent.setProfileId("mock");
        return agent;
    }

    // ---- tests ----

    private static void agentRepliesWithMockModel(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, 3, 1, 3);
        AgentEntity agent = agent(helper, owner, "Chatty", 5, 1, 5);
        agent.setMode(AgentMode.STAY);
        agent.getAgentBrain().submit("<chat from=\"tester\">hello there</chat>");
        helper.succeedWhen(() -> helper.assertTrue(agent.getAgentBrain().lastReply().startsWith("Mock reply to: hello there"),
                "no reply yet: '" + agent.getAgentBrain().lastReply() + "'"));
    }

    private static void agentFollowsOwner(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, 3, 1, 3);
        AgentEntity agent = agent(helper, owner, "Follower", 19, 1, 19);
        agent.setMode(AgentMode.FOLLOW);
        AiPlayersMod.LOGGER.info("[gametest] follow start distance {}", agent.distanceTo(owner));
        helper.succeedWhen(() -> {
            double d = agent.distanceTo(owner);
            if (d < 6.5) AiPlayersMod.LOGGER.info("[gametest] follow reached distance {} at tick {}", d, helper.getTick());
            helper.assertTrue(d < 6.5, "agent is still " + Math.round(d) + " blocks from its owner");
        });
    }
}
