package com.joserayos.aiplayers.brain.tools;

import com.joserayos.aiplayers.agent.autonomy.Goal;

/** M9 tools (AUTONOMOUS mode only): goals with code verification. */
final class PlanTools {
    private PlanTools() {}

    static void register() {
        ToolRegistry.register(new Tool("set_goal",
                "Set your current goal. Give `item` and `count` when success can be checked by inventory "
                        + "(e.g. item stone_pickaxe count 1; '#food', '#logs', '#planks', '@shelter' also work).",
                Schema.object().str("goal", "What you're trying to do", true).str("item", "Item id to verify (optional)", false)
                        .integer("count", "How many (optional)", 1, 2304, false).build(), Tool.Group.PLAN,
                (agent, args, ctx) -> {
                    String item = args.str("item", "").strip();
                    if (!item.isEmpty() && !item.startsWith("#") && !item.startsWith("@")) item = com.joserayos.aiplayers.skill.ItemResolver.id(ToolRegistry.item(item));
                    Goal g = new Goal(args.str("goal").strip(), item, item.isEmpty() ? 0 : args.integer("count", 1, 2304, 1));
                    agent.getAutonomy().setGoal(g);
                    return ToolResult.done("goal set: " + g.describe(agent.getAutonomy().inventorySummary()));
                }));

        ToolRegistry.register(new Tool("complete_goal", "Mark your goal as done. Verifiable goals are checked against your inventory.",
                Schema.object().build(), Tool.Group.PLAN,
                (agent, args, ctx) -> {
                    Goal g = agent.getAutonomy().goal();
                    if (g.isNone()) return ToolResult.error("you have no goal");
                    var inv = agent.getAutonomy().inventorySummary();
                    if (g.verifiable() && !g.satisfied(inv)) {
                        return ToolResult.error("not done yet: " + g.have(inv) + "/" + g.count() + " " + g.item());
                    }
                    agent.getAutonomy().setGoal(Goal.NONE);
                    agent.addEvent("goal done: " + g.text());
                    return ToolResult.done("goal complete: " + g.text() + ". Suggested next: " + agent.getAutonomy().suggestion().text());
                }));
    }
}
