package com.joserayos.aiplayers.agent.autonomy;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import com.joserayos.aiplayers.skill.ItemResolver;
import com.joserayos.aiplayers.team.AgentMemory;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** AUTONOMOUS mode: goal state, code-verified completion, and an idle heartbeat that wakes the brain. */
public final class Autonomy {
    private final AgentEntity agent;
    private Goal goal = Goal.NONE;
    private int idleTicks;
    private final ArrayDeque<Long> heartbeats = new ArrayDeque<>();

    public Autonomy(AgentEntity agent) {
        this.agent = agent;
    }

    public Goal goal() { return goal; }

    public void setGoal(Goal g) { this.goal = g == null ? Goal.NONE : g; }

    /** Inventory summary used by goals and the curriculum. */
    public Map<String, Integer> inventorySummary() {
        Map<String, Integer> inv = new HashMap<>();
        var c = agent.getInventory();
        for (int i = 0; i < c.getContainerSize(); i++) add(inv, c.getItem(i));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HAND || slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) add(inv, agent.getItemBySlot(slot));
        }
        var server = agent.level().getServer();
        if (server != null) {
            var notes = AgentMemory.of(server, agent.getUUID()).notes();
            if (notes.containsKey("shelter") || notes.containsKey("base") || notes.containsKey("hut")) inv.put("@shelter", 1);
        }
        if (agent.getBlockCache().latest().stream().anyMatch(e -> e.type().equals("crafting_table") && e.nearestDistance() < 16)) inv.put("@table_nearby", 1);
        return inv;
    }

    static void add(Map<String, Integer> inv, ItemStack s) {
        if (s.isEmpty()) return;
        int n = s.getCount();
        inv.merge(ItemResolver.id(s.getItem()), n, Integer::sum);
        if (s.has(DataComponents.FOOD) && !ItemResolver.id(s.getItem()).matches("rotten_flesh|spider_eye|poisonous_potato|pufferfish")) inv.merge("#food", n, Integer::sum);
        if (s.is(ItemTags.LOGS)) inv.merge("#logs", n, Integer::sum);
        if (s.is(ItemTags.PLANKS)) inv.merge("#planks", n, Integer::sum);
        if (s.is(ItemTags.WOODEN_DOORS)) inv.merge("#doors", n, Integer::sum);
    }

    public Goal suggestion() { return Curriculum.next(inventorySummary()); }

    /** Runs the suggested goal's tool directly (no model). Returns a description for logs/status. */
    public String runSuggestedFallback() {
        Goal g = suggestion();
        String[] call = Curriculum.fallbackCall(g);
        // Goals that need judgment (food, free choice): go look around somewhere new instead of standing still.
        if (call == null) call = new String[]{"explore", "{\"direction\":\"auto\",\"distance\":128}"};
        if (goal.isNone()) goal = g;
        var result = com.joserayos.aiplayers.brain.tools.ToolRegistry.execute(agent, call[0],
                new com.joserayos.aiplayers.brain.tools.ToolArgs(com.google.gson.JsonParser.parseString(call[1]).getAsJsonObject()),
                new com.joserayos.aiplayers.brain.tools.ToolContext(""));
        String msg = "fallback " + call[0] + " " + call[1] + " -> " + result.message();
        agent.addEvent(msg);
        com.joserayos.aiplayers.AiPlayersMod.LOGGER.info("{} (autonomous) {}", agent.getAgentName(), msg);
        return msg;
    }

    /** Why the heartbeat isn't firing right now ("" = idle, a heartbeat is due). */
    public String idleBlocker() {
        if (agent.getMode() != AgentMode.AUTONOMOUS) return "not in autonomous mode";
        if (agent.hasActiveTask()) return "running a task";
        if (agent.getAgentBrain().isBusy()) return "waiting for the model (" + agent.getAgentBrain().thinkingSeconds() + "s)";
        if (agent.getAgentBrain().hasPending()) return "model input queued (cooldown between thinks)";
        if (agent.getTarget() != null) return "fighting " + agent.getTarget().getName().getString();
        if (agent.isFleeing()) return "fleeing";
        long now = agent.level().getGameTime();
        while (!heartbeats.isEmpty() && now - heartbeats.peekFirst() > 72_000) heartbeats.removeFirst();
        if (heartbeats.size() >= AiPlayersConfig.get(AiPlayersConfig.AUTONOMOUS_MAX_THINKS_PER_HOUR)) return "hourly heartbeat cap reached";
        int every = AiPlayersConfig.get(AiPlayersConfig.AUTONOMOUS_HEARTBEAT_SECONDS) * 20;
        return "idle; next heartbeat in " + Math.max(0, (every - idleTicks) / 20) + "s";
    }

    /** Hint when the current/suggested goal needs a block the agent has never seen. */
    public String explorationHint(Goal g) {
        String block = Curriculum.neededBlock(g);
        if (block == null) return "";
        var m = com.joserayos.aiplayers.skill.gather.BlockMatcher.parse(block).orElse(null);
        if (m == null) return "";
        var filter = com.joserayos.aiplayers.skill.gather.KnownSpots.typeFilter(m);
        boolean seen = agent.getBlockCache().latest().stream().anyMatch(e -> filter.test(e.type()))
                || com.joserayos.aiplayers.skill.gather.KnownSpots.nearest(agent, m).isPresent();
        return seen ? "" : " You haven't seen any " + m.label() + " yet: explore (looking_for " + block + ") to find some.";
    }

    /** Prompt section for AUTONOMOUS mode. */
    public String promptText() {
        if (agent.getMode() != AgentMode.AUTONOMOUS) return "";
        Map<String, Integer> inv = inventorySummary();
        return "- You are in AUTONOMOUS mode: pursue your goal on your own with tools. Use set_goal to pick a goal "
                + "(prefer the suggested one) and complete_goal when done. Owner orders still come first.\n"
                + "- Roam freely: explore new land when you need something you haven't seen, or just to discover things.\n"
                + "[GOAL] " + goal.describe(inv) + "\n[SUGGESTED NEXT GOAL] " + Curriculum.next(inv).describe(inv)
                + explorationHint(goal.isNone() ? Curriculum.next(inv) : goal) + "\n";
    }

    public void tick() {
        if (agent.getMode() != AgentMode.AUTONOMOUS) {
            idleTicks = 0;
            return;
        }
        if (agent.tickCount % 20 == 0 && goal.verifiable() && goal.satisfied(inventorySummary())) {
            agent.addEvent("goal done (verified): " + goal.text());
            goal = Goal.NONE;
        }
        boolean idle = !agent.hasActiveTask() && !agent.getAgentBrain().isBusy() && !agent.getAgentBrain().hasPending()
                && agent.getTarget() == null && !agent.isFleeing();
        if (!idle) {
            idleTicks = 0;
            return;
        }
        int every = AiPlayersConfig.get(AiPlayersConfig.AUTONOMOUS_HEARTBEAT_SECONDS) * 20;
        if (++idleTicks < every) return;
        idleTicks = 0;
        long now = agent.level().getGameTime();
        while (!heartbeats.isEmpty() && now - heartbeats.peekFirst() > 72_000) heartbeats.removeFirst();
        if (heartbeats.size() >= AiPlayersConfig.get(AiPlayersConfig.AUTONOMOUS_MAX_THINKS_PER_HOUR)) return;
        heartbeats.addLast(now);
        Map<String, Integer> inv = inventorySummary();
        Goal next = Curriculum.next(inv);
        String extra = explorationHint(goal.isNone() ? next : goal);
        if (extra.isEmpty() && agent.getRandom().nextInt(100) < AiPlayersConfig.get(AiPlayersConfig.CURIOSITY_PERCENT)) {
            extra = " Feeling curious: you could explore (direction auto) to discover new land, then come back to your goal.";
        }
        agent.getAgentBrain().submitHeartbeat("[heartbeat] You are idle. Goal: " + goal.describe(inv)
                + ". Suggested next goal: " + next.describe(inv) + "." + extra + " Decide your next action.");
    }
}
