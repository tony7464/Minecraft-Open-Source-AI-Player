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

    /** Prompt section for AUTONOMOUS mode. */
    public String promptText() {
        if (agent.getMode() != AgentMode.AUTONOMOUS) return "";
        Map<String, Integer> inv = inventorySummary();
        return "- You are in AUTONOMOUS mode: pursue your goal on your own with tools. Use set_goal to pick a goal "
                + "(prefer the suggested one) and complete_goal when done. Owner orders still come first.\n"
                + "[GOAL] " + goal.describe(inv) + "\n[SUGGESTED NEXT GOAL] " + Curriculum.next(inv).describe(inv) + "\n";
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
        agent.getAgentBrain().submitHeartbeat("[heartbeat] You are idle. Goal: " + goal.describe(inv)
                + ". Suggested next goal: " + Curriculum.next(inv).describe(inv) + ". Decide your next action.");
    }
}
