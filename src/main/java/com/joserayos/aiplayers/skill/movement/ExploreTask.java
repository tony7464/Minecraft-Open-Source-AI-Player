package com.joserayos.aiplayers.skill.movement;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import com.joserayos.aiplayers.perception.BlockInterestCache;
import com.joserayos.aiplayers.perception.ResourceMap;
import com.joserayos.aiplayers.skill.gather.BlockMatcher;
import com.joserayos.aiplayers.task.AgentTask;
import com.joserayos.aiplayers.task.TaskStatus;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Walk a long way in a direction (or toward unexplored land) using vanilla navigation in short surface
 * waypoints. Keeps its own chunks loaded, swims out of water, and turns around obstacles (cliffs, oceans,
 * walls) when it stops making progress. Optionally stops as soon as it sees a wanted block.
 */
public class ExploreTask implements AgentTask {
    public static final String[] DIRECTIONS = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
    static final int CHECK_TICKS = 100;
    static final int MAX_DETOURS = 10;

    private final @Nullable String direction; // null = auto
    private final int distance;
    private final @Nullable BlockMatcher lookingFor;
    private Vec3 start = Vec3.ZERO;
    private Vec3 dir = new Vec3(1, 0, 0);
    private Vec3 dest = Vec3.ZERO;
    private String heading = "";
    private int repath;
    private int pathFails;
    private int detours;
    private int checkTimer;
    private Vec3 lastCheckPos = Vec3.ZERO;
    private double lastCheckDist;
    private int seenCycle = -1;
    private final Map<String, BlockPos> sightings = new LinkedHashMap<>();
    private @Nullable BlockPos found;
    private String result = "";

    public ExploreTask(@Nullable String direction, int distance, @Nullable BlockMatcher lookingFor) {
        this.direction = direction == null || direction.isBlank() || direction.equalsIgnoreCase("auto") ? null : direction.toLowerCase(Locale.ROOT);
        this.distance = distance;
        this.lookingFor = lookingFor;
    }

    /** Unit vector for a compass direction (north = -Z), or null if unknown. */
    public static @Nullable Vec3 vector(String name) {
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (DIRECTIONS[i].equals(name) || abbreviation(DIRECTIONS[i]).equals(name)) return angle(i * 45);
        }
        return null;
    }

    static String abbreviation(String d) {
        return d.replace("north", "n").replace("south", "s").replace("east", "e").replace("west", "w");
    }

    /** Compass angle in degrees, 0 = north, clockwise. */
    static Vec3 angle(double deg) {
        double r = Math.toRadians(deg);
        return new Vec3(Math.sin(r), 0, -Math.cos(r));
    }

    static String nameOf(Vec3 v) {
        double deg = (Math.toDegrees(Math.atan2(v.x, -v.z)) + 360) % 360;
        return DIRECTIONS[(int) Math.round(deg / 45) % 8];
    }

    public @Nullable BlockPos found() { return found; }

    @Override public String id() { return "explore"; }

    @Override
    public String describe() {
        return "explore " + (direction == null ? "auto" + (heading.isEmpty() ? "" : " (" + heading + ")") : direction) + " " + distance + " blocks"
                + (lookingFor != null ? " looking for " + lookingFor.label() : "");
    }

    @Override
    public String progress() {
        return Math.round(horizontal(start, here())) + "/" + distance + " blocks";
    }

    private Vec3 here;
    private Vec3 here() { return here == null ? start : here; }

    @Override public String resultMessage() { return result; }

    @Override public int timeoutTicks() { return 1200 + distance * 15; }

    // Own progress/detour handling: the generic stuck detector would fire whenever we turn.
    @Override public @Nullable Vec3 movementGoal() { return null; }

    @Override
    public void start(AgentEntity agent) {
        start = agent.position();
        here = start;
        Vec3 v = direction == null ? null : vector(direction);
        if (v == null) v = autoDirection(agent);
        setHeading(agent, v, distance);
        lastCheckPos = start;
    }

    private void setHeading(AgentEntity agent, Vec3 v, double length) {
        dir = v.normalize();
        heading = nameOf(dir);
        Vec3 from = agent.position();
        dest = from.add(dir.scale(length));
        int limit = AiPlayersConfig.get(AiPlayersConfig.EXPLORATION_RADIUS);
        if (limit > 0) {
            BlockPos home = agent.getHome();
            Vec3 h = Vec3.atBottomCenterOf(home);
            if (horizontal(dest, h) > limit) {
                // Clamp: walk only as far as the edge of the allowed area (or back toward home if already outside).
                double lo = 0, hi = length;
                for (int i = 0; i < 20; i++) {
                    double mid = (lo + hi) / 2;
                    if (horizontal(from.add(dir.scale(mid)), h) <= limit) lo = mid; else hi = mid;
                }
                dest = lo < 8 ? h : from.add(dir.scale(lo));
                if (lo < 8) { dir = h.subtract(from).normalize(); heading = nameOf(dir); }
            }
        }
        lastCheckDist = horizontal(from, dest);
        repath = 0;
        pathFails = 0;
    }

    /** Pick the compass direction that leads to the least-visited land (random tie-break). */
    Vec3 autoDirection(AgentEntity agent) {
        ResourceMap map = agent.getResourceMap();
        String dim = agent.dimensionId();
        int limit = AiPlayersConfig.get(AiPlayersConfig.EXPLORATION_RADIUS);
        Vec3 home = Vec3.atBottomCenterOf(agent.getHome());
        double best = Double.MAX_VALUE;
        Vec3 bestV = angle(agent.getRandom().nextInt(8) * 45);
        for (int i = 0; i < 8; i++) {
            Vec3 v = angle(i * 45);
            double score = agent.getRandom().nextDouble() * 0.5;
            for (int d = 48; d <= Math.max(96, distance); d += 48) {
                Vec3 p = agent.position().add(v.scale(d));
                if (map.visited(dim, (int) p.x, (int) p.z)) score += 1;
                if (limit > 0 && horizontal(p, home) > limit) score += 3;
            }
            if (score < best) { best = score; bestV = v; }
        }
        return bestV;
    }

    static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public TaskStatus tick(AgentEntity agent) {
        here = agent.position();
        ChunkKeeper.keep(agent);
        noteSightings(agent);
        if (found != null) {
            result = "found " + lookingFor.label() + " at (" + found.getX() + ", " + found.getY() + ", " + found.getZ() + ") after "
                    + Math.round(horizontal(start, here)) + " blocks" + sightingText();
            return TaskStatus.SUCCESS;
        }
        double toDest = horizontal(here, dest);
        if (toDest <= 4) return finish(agent);
        if (agent.isInWater()) agent.getJumpControl().jump(); // swim at the surface
        if (--repath <= 0 || agent.getNavigation().isDone()) {
            repath = 20;
            BlockPos d = BlockPos.containing(dest.x, agent.getY(), dest.z);
            double gy = agent.level().hasChunkAt(d)
                    ? agent.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, d.getX(), d.getZ()) : agent.getY();
            if (gy <= agent.level().getMinY()) gy = agent.getY(); // void column: keep our height
            Vec3 goal = new Vec3(dest.x, gy, dest.z);
            if (!Navigation.moveToward(agent, goal, 1.0)) {
                if (++pathFails >= 3) detour(agent);
            } else {
                pathFails = 0;
            }
        }
        if (++checkTimer >= CHECK_TICKS) {
            checkTimer = 0;
            double now = horizontal(here, dest);
            if (lastCheckDist - now < 4) detour(agent); // blocked by a cliff, wall, ocean...
            else lastCheckDist = now;
            lastCheckPos = here;
        }
        if (detours > MAX_DETOURS) return finish(agent);
        return TaskStatus.RUNNING;
    }

    private void detour(AgentEntity agent) {
        detours++;
        double remaining = Math.max(24, distance - horizontal(start, agent.position()));
        // Alternate left/right and widen: +45, -45, +90, -90, +135...
        int step = (detours + 1) / 2;
        double sign = detours % 2 == 1 ? 1 : -1;
        double deg = (Math.toDegrees(Math.atan2(dir.x, -dir.z)) + sign * 45 * Math.min(step, 3) + 360) % 360;
        setHeading(agent, angle(deg), Math.min(remaining, 96));
        agent.getNavigation().stop();
    }

    private TaskStatus finish(AgentEntity agent) {
        double travelled = horizontal(start, here);
        BlockPos p = agent.blockPosition();
        String where = " now at (" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
        if (lookingFor != null) {
            result = "explored " + Math.round(travelled) + " blocks (" + heading + "), no " + lookingFor.label() + " seen;" + where + sightingText();
            return travelled >= minTrip() ? TaskStatus.SUCCESS : TaskStatus.FAILED;
        }
        if (travelled < minTrip()) {
            result = "could not get anywhere (blocked in every direction tried);" + where + sightingText();
            return TaskStatus.FAILED;
        }
        result = "explored " + Math.round(travelled) + " blocks (" + heading + ")" + (detours > 0 ? " with " + detours + " detours" : "") + ";" + where + sightingText();
        return TaskStatus.SUCCESS;
    }

    private double minTrip() { return Math.min(16, distance * 0.6); }

    private void noteSightings(AgentEntity agent) {
        if (lookingFor != null && found == null && agent.tickCount % 40 == 0) probe(agent);
        BlockInterestCache cache = agent.getBlockCache();
        if (cache.cycles() == seenCycle) return;
        seenCycle = cache.cycles();
        for (BlockInterestCache.Entry e : cache.latest()) {
            sightings.putIfAbsent(e.type(), e.nearest());
            if (lookingFor != null && found == null && agent.level().hasChunkAt(e.nearest())
                    && lookingFor.test().test(agent.level().getBlockState(e.nearest()))) {
                found = e.nearest();
            }
        }
    }

    /** Small local scan for blocks the background scan doesn't track (sand, clay, flowers...). */
    private void probe(AgentEntity agent) {
        BlockPos c = agent.blockPosition();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) for (int dy = -4; dy <= 4; dy++) {
            p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
            if (!agent.level().hasChunkAt(p)) continue;
            var s = agent.level().getBlockState(p);
            if (!s.isAir() && lookingFor.test().test(s)) { found = p.immutable(); return; }
        }
    }

    private String sightingText() {
        if (sightings.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("; saw: ");
        int n = 0;
        for (var e : sightings.entrySet()) {
            if (n++ >= 12) { sb.append(", ..."); break; }
            if (n > 1) sb.append(", ");
            sb.append(e.getKey()).append(" (").append(e.getValue().getX()).append(' ').append(e.getValue().getY()).append(' ').append(e.getValue().getZ()).append(')');
        }
        return sb.toString();
    }
}
