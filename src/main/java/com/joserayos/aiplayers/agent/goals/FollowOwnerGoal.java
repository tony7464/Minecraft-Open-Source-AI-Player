package com.joserayos.aiplayers.agent.goals;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.Nullable;

/**
 * Follows the owner in FOLLOW mode: starts moving when farther than {@code startDistance},
 * stops within {@code stopDistance}, and teleports nearby when farther than the teleport distance.
 */
public class FollowOwnerGoal extends Goal {
    private final AgentEntity agent;
    private final double speed;
    private final float startDistance;
    private final float stopDistance;
    private @Nullable ServerPlayer owner;
    private int recalcCooldown;
    private float oldWaterCost;

    public FollowOwnerGoal(AgentEntity agent, double speed, float startDistance, float stopDistance) {
        this.agent = agent;
        this.speed = speed;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!agent.getMode().follows() || agent.hasActiveTask()) return false;
        ServerPlayer o = agent.getFollowTargetPlayer();
        if (o == null || o.isSpectator() || o.level() != agent.level()) return false;
        if (agent.distanceToSqr(o) < startDistance * startDistance) return false;
        owner = o;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (owner == null || !agent.getMode().follows() || agent.hasActiveTask()) return false;
        if (owner.isRemoved() || owner.level() != agent.level()) return false;
        return agent.distanceToSqr(owner) > stopDistance * stopDistance;
    }

    @Override
    public void start() {
        recalcCooldown = 0;
        oldWaterCost = agent.getPathfindingMalus(PathType.WATER);
        agent.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public void stop() {
        owner = null;
        agent.getNavigation().stop();
        agent.setPathfindingMalus(PathType.WATER, oldWaterCost);
    }

    @Override
    public void tick() {
        if (owner == null) return;
        agent.getLookControl().setLookAt(owner, 10.0F, agent.getMaxHeadXRot());
        if (--recalcCooldown <= 0) {
            recalcCooldown = adjustedTickDelay(10);
            if (agent.shouldTeleportTo(owner)) {
                agent.teleportNear(owner.blockPosition());
            } else {
                agent.getNavigation().moveTo(owner, speed);
            }
        }
    }
}
