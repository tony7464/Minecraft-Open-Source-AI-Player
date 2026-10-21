package com.joserayos.aiplayers.brain.tools;

/** Registers tools added by later milestones. */
final class ExtraTools {
    private ExtraTools() {}

    static void registerAll() {
        GatherTools.register();
        CraftTools.register();
        CombatTools.register();
    }
}
