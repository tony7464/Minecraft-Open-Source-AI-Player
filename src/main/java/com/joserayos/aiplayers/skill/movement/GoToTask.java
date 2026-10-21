package com.joserayos.aiplayers.skill.movement;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.task.AgentTask;
import com.joserayos.aiplayers.task.TaskStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Walk to a block position (with waypoints for long trips). */
public class GoToTask implements AgentTask {
    private final BlockPos target;
    private final double range;
    private int repathCooldown;
    private int failedPaths;
    private String result = "";

    public GoToTask(BlockPos target, double range) {
        this.target = target;
        this.range = range;
    }

    @Override public String id() { return "go_to"; }

    @Override public String describe() { return "go_to (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")"; }

    private int timeout = 5 * 60 * 20;

    @Override
    public void start(AgentEntity agent) {
        repathCooldown = 0;
        // Long trips: about 4 blocks/s walking, with generous slack for detours.
        timeout = Math.max(5 * 60 * 20, (int) Math.min(Integer.MAX_VALUE / 2, agent.position().distanceTo(Vec3.atBottomCenterOf(target)) * 12));
    }

    @Override
    public TaskStatus tick(AgentEntity agent) {
        Vec3 goal = Vec3.atBottomCenterOf(target);
        double dist = agent.position().distanceTo(goal);
        if (dist > 24) ChunkKeeper.keep(agent);
        if (dist <= range) {
            result = "arrived near (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")";
            return TaskStatus.SUCCESS;
        }
        if (--repathCooldown <= 0 || agent.getNavigation().isDone()) {
            repathCooldown = 20;
            if (!Navigation.moveToward(agent, goal, 1.0)) {
                if (++failedPaths >= 5) {
                    result = "no walkable path found toward (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + "), "
                            + Math.round(dist) + " blocks away";
                    return TaskStatus.FAILED;
                }
            } else {
                failedPaths = 0;
            }
        }
        return TaskStatus.RUNNING;
    }

    @Override public String progress() { return ""; }

    @Override public String resultMessage() { return result; }

    @Override public Vec3 movementGoal() { return Vec3.atBottomCenterOf(target); }

    @Override public int timeoutTicks() { return timeout; }
}
