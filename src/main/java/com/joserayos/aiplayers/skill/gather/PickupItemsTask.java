package com.joserayos.aiplayers.skill.gather;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.task.AgentTask;
import com.joserayos.aiplayers.task.TaskStatus;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Walk over and pick up dropped items within a radius. */
public class PickupItemsTask implements AgentTask {
    private final int radius;
    private @Nullable ItemEntity target;
    private final java.util.Set<java.util.UUID> unreachable = new java.util.HashSet<>();
    private double bestDistance = Double.MAX_VALUE;
    private int noProgressTicks;
    private int picked;
    private int repath;
    private int idleTicks;
    private String result = "";

    public PickupItemsTask(int radius) {
        this.radius = radius;
    }

    @Override public String id() { return "pickup_items"; }

    @Override public String describe() { return "pickup_items radius " + radius; }

    @Override
    public void start(AgentEntity agent) {
        agent.setPickupAllowed(true);
    }

    @Override
    public void stop(AgentEntity agent, String reason) {
        agent.setPickupAllowed(false);
        agent.getNavigation().stop();
    }

    @Override
    public TaskStatus tick(AgentEntity agent) {
        picked += agent.drainPickedUpCount();
        if (agent.isInventoryFull()) {
            result = "inventory full after picking up " + picked + " item(s)";
            return TaskStatus.SUCCESS;
        }
        if (target == null || !target.isAlive()) {
            target = nearest(agent);
            repath = 0;
            bestDistance = Double.MAX_VALUE;
            noProgressTicks = 0;
            if (target == null) {
                if (++idleTicks > 20) {
                    result = picked > 0 ? "picked up " + picked + " item(s); none left nearby" : "no items to pick up within " + radius + " blocks";
                    return TaskStatus.SUCCESS;
                }
                return TaskStatus.RUNNING;
            }
        }
        idleTicks = 0;
        double d = agent.distanceTo(target);
        if (d < bestDistance - 0.5) {
            bestDistance = d;
            noProgressTicks = 0;
        } else if (++noProgressTicks > 60) {
            // Can't reach this one (behind a wall, across a gap...): skip it and try the next.
            unreachable.add(target.getUUID());
            target = null;
            return TaskStatus.RUNNING;
        }
        if (--repath <= 0) {
            repath = 10;
            agent.getNavigation().moveTo(target, 1.0);
        }
        return TaskStatus.RUNNING;
    }

    private @Nullable ItemEntity nearest(AgentEntity agent) {
        List<ItemEntity> items = agent.level().getEntitiesOfClass(ItemEntity.class, agent.getBoundingBox().inflate(radius),
                e -> e.isAlive() && !e.getItem().isEmpty() && !unreachable.contains(e.getUUID()) && agent.canHold(e.getItem()));
        return items.stream().min(Comparator.comparingDouble(agent::distanceToSqr)).orElse(null);
    }

    @Override public String progress() { return picked + " picked up"; }

    @Override public String resultMessage() { return result; }

    @Override public @Nullable Vec3 movementGoal() { return target == null ? null : target.position(); }

    @Override public int timeoutTicks() { return 90 * 20; }
}
