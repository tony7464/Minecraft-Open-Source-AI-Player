package com.joserayos.aiplayers.skill.movement;

import com.joserayos.aiplayers.agent.AgentEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Pathfinding level 1 + 2: vanilla navigation for short trips, and intermediate waypoints
 * (on the surface, about {@link #WAYPOINT_STEP} blocks apart) for long ones.
 */
public final class Navigation {
    public static final double WAYPOINT_STEP = 24.0;

    private Navigation() {}

    /**
     * Start or refresh movement toward {@code goal}. Returns false if no path could be found to the
     * goal or to a waypoint toward it.
     */
    public static boolean moveToward(AgentEntity agent, Vec3 goal, double speed) {
        Vec3 here = agent.position();
        double dist = here.distanceTo(goal);
        if (dist <= WAYPOINT_STEP + 8) {
            if (agent.getNavigation().moveTo(goal.x, goal.y, goal.z, speed)) return true;
            // Try a nearby reachable block (the exact target may be inside a wall or tree).
            return agent.getNavigation().moveTo(goal.x, goal.y, goal.z, 2, speed);
        }
        Vec3 dir = goal.subtract(here).normalize();
        for (double step = WAYPOINT_STEP; step >= 8; step -= 8) {
            int wx = (int) Math.floor(here.x + dir.x * step);
            int wz = (int) Math.floor(here.z + dir.z * step);
            if (!agent.level().hasChunkAt(new BlockPos(wx, agent.getBlockY(), wz))) continue;
            int wy = agent.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
            // Underground targets: keep the current height instead of climbing to the surface.
            if (goal.y < wy - 6 && Math.abs(agent.getY() - goal.y) < 6) wy = agent.getBlockY();
            if (agent.getNavigation().moveTo(wx + 0.5, wy, wz + 0.5, 2, speed)) return true;
        }
        return false;
    }

    public static boolean isLoaded(AgentEntity agent, BlockPos pos) {
        return agent.level().hasChunkAt(pos);
    }
}
