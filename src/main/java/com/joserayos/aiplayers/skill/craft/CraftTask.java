package com.joserayos.aiplayers.skill.craft;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.skill.ItemResolver;
import com.joserayos.aiplayers.skill.build.Placement;
import com.joserayos.aiplayers.skill.movement.Navigation;
import com.joserayos.aiplayers.task.AgentTask;
import com.joserayos.aiplayers.task.TaskStatus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Crafts an item, crafting intermediate items and placing a crafting table when needed. */
public class CraftTask implements AgentTask {
    public static final int TABLE_SEARCH_RADIUS = 12;

    private final Item target;
    private final int count;
    private List<Crafter.Step> steps = List.of();
    private int index;
    private @Nullable BlockPos table;
    private int cooldown;
    private int repath;
    private String result = "";
    private final List<String> done = new ArrayList<>();

    public CraftTask(Item target, int count) {
        this.target = target;
        this.count = count;
    }

    @Override public String id() { return "craft_item"; }
    @Override public String describe() { return "craft_item " + count + " " + ItemResolver.id(target); }
    @Override public String resultMessage() { return result; }
    @Override public String progress() { return index + "/" + steps.size() + " steps"; }
    @Override public @Nullable Vec3 movementGoal() { return table == null ? null : Vec3.atCenterOf(table); }

    @Override
    public void start(AgentEntity agent) {
        ServerLevel level = (ServerLevel) agent.level();
        table = Placement.findNearest(agent, TABLE_SEARCH_RADIUS, s -> s.is(Blocks.CRAFTING_TABLE)).orElse(null);
        Crafter crafter = new Crafter(level);
        boolean owned = agent.countItem(Items.CRAFTING_TABLE) > 0;
        Crafter.Plan plan = crafter.plan(Crafter.snapshot(agent), target, count, table != null || owned);
        if (!plan.ok()) {
            result = "can't craft " + count + " " + ItemResolver.id(target) + ": " + plan.missing();
            return;
        }
        steps = plan.steps();
    }

    @Override
    public TaskStatus tick(AgentEntity agent) {
        if (!result.isEmpty() && steps.isEmpty()) return TaskStatus.FAILED;
        if (cooldown-- > 0) return TaskStatus.RUNNING;
        if (index >= steps.size()) {
            result = "crafted " + count + " " + ItemResolver.id(target) + (done.isEmpty() ? "" : " (" + String.join(", ", done) + ")");
            return TaskStatus.SUCCESS;
        }
        Crafter.Step step = steps.get(index);
        if (step.recipe().needsTable()) {
            if (table == null || !agent.level().getBlockState(table).is(Blocks.CRAFTING_TABLE)) {
                table = Placement.findNearest(agent, TABLE_SEARCH_RADIUS, s -> s.is(Blocks.CRAFTING_TABLE)).orElse(null);
            }
            if (table == null) {
                BlockPos spot = Placement.spotNear(agent).orElse(null);
                if (spot == null) {
                    result = "no room to place a crafting table";
                    return TaskStatus.FAILED;
                }
                String err = Placement.place(agent, spot, Items.CRAFTING_TABLE);
                if (err != null) {
                    result = "couldn't place a crafting table: " + err;
                    return TaskStatus.FAILED;
                }
                table = spot;
                done.add("placed a crafting table");
            }
            if (agent.getEyePosition().distanceTo(Vec3.atCenterOf(table)) > 4.0) {
                if (--repath <= 0) {
                    repath = 20;
                    Navigation.moveToward(agent, Vec3.atCenterOf(table), 1.0);
                }
                return TaskStatus.RUNNING;
            }
            agent.getNavigation().stop();
            agent.getLookControl().setLookAt(Vec3.atCenterOf(table));
        }
        String err = Crafter.execute(agent, step);
        if (err != null) {
            result = "crafting failed at '" + step.describe() + "': " + err;
            return TaskStatus.FAILED;
        }
        agent.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (step.recipe().output() != target) done.add(step.describe());
        index++;
        cooldown = 5;
        return TaskStatus.RUNNING;
    }
}
