package com.joserayos.aiplayers.gametest;

import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;

/** Skill GameTests (added as skills are built). */
final class AiGameTestsSkills {
    interface Registrar {
        void add(String name, String structure, int maxTicks, Consumer<GameTestHelper> fn);
    }

    private AiGameTestsSkills() {}

    static void registerAll(Registrar r) {}
}
