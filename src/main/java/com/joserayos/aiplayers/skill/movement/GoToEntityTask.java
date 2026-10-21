package com.joserayos.aiplayers.skill.movement;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.task.AgentTask;
import com.joserayos.aiplayers.task.TaskStatus;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Walk to a (moving) entity such as a player. */
public class GoToEntityTask implements AgentTask {
    private final UUID targetId;
    private final String targetName;
    private final double range;
    private int repathCooldown;
    private int failedPaths;
    private @Nullable Vec3 lastGoal;
    private String result = "";

    public GoToEntityTask(UUID targetId, String targetName, double range) {
        this.targetId = targetId;
        this.targetName = targetName;
        this.range = range;
    }

    @Override public String id() { return "go_to_player"; }

    @Override public String describe() { return "go_to_player " + targetName; }

    @Override public void start(AgentEntity agent) { repathCooldown = 0; }

    protected @Nullable Entity target(AgentEntity agent) {
        Entity e = ((ServerLevel) agent.level()).getEntity(targetId);
        return e != null && e.isAlive() && e.level() == agent.level() ? e : null;
    }

    @Override
    public TaskStatus tick(AgentEntity agent) {
        Entity target = target(agent);
        if (target == null) {
            result = targetName + " is not in this dimension or is gone";
            return TaskStatus.FAILED;
        }
        lastGoal = target.position();
        if (agent.distanceTo(target) <= range) {
            result = "reached " + targetName;
            agent.getLookControl().setLookAt(target);
            return TaskStatus.SUCCESS;
        }
        if (--repathCooldown <= 0) {
            repathCooldown = 10;
            if (!Navigation.moveToward(agent, target.position(), 1.0)) {
                if (++failedPaths >= 6) {
                    result = "no walkable path to " + targetName;
                    return TaskStatus.FAILED;
                }
            } else {
                failedPaths = 0;
            }
        }
        return TaskStatus.RUNNING;
    }

    @Override public String resultMessage() { return result; }

    @Override public @Nullable Vec3 movementGoal() { return lastGoal; }
}
