package com.joserayos.aiplayers.skill.gather;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.task.AgentTask;
import com.joserayos.aiplayers.task.TaskStatus;
import com.joserayos.aiplayers.team.Blackboard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Find, walk to and mine blocks until {@code count} drops were collected.
 * Scans incrementally (a budget of blocks per tick), claims targets on the Blackboard,
 * picks the best tool, and breaks blocks over time like a player would.
 */
public class CollectBlockTask implements AgentTask {
    public static final int SCAN_BUDGET_PER_TICK = 6000;
    private static final int CLAIM_TICKS = 20 * 30;

    private enum Phase { SCAN, MOVE, BREAK, TRAVEL }
    public static final int EXPLORE_STEP = 160;

    private final BlockMatcher matcher;
    private final int count;
    private final int radius;
    private final int vertical;
    private Phase phase = Phase.SCAN;
    private int collected;
    private final Set<BlockPos> blacklist = new HashSet<>();
    private final List<BlockPos> candidates = new ArrayList<>();
    private int scanIndex;
    private BlockPos scanOrigin = BlockPos.ZERO;
    private boolean scannedOnce;
    private @Nullable BlockPos target;
    private int repath;
    private int pathFails;
    private float progress;
    private int breakTicks;
    private String result = "";
    private com.joserayos.aiplayers.task.@Nullable AgentTask travel;
    private int trips;
    private int extraTicks;
    private final java.util.List<String> travelLog = new java.util.ArrayList<>();

    public CollectBlockTask(BlockMatcher matcher, int count, int radius) {
        this.matcher = matcher;
        this.count = count;
        this.radius = radius;
        this.vertical = Math.min(radius, 16);
    }

    @Override public String id() { return "collect_block"; }

    @Override public String describe() { return "collect_block " + matcher.label() + " x" + count; }

    @Override
    public String progress() {
        return collected + "/" + count + " collected" + (travel != null ? ", searching farther: " + travel.describe() + " " + travel.progress() : "");
    }

    @Override public String resultMessage() { return result; }

    @Override public int timeoutTicks() { return Math.max(3 * 60 * 20, count * 20 * 20) + extraTicks; }

    @Override public @Nullable Vec3 movementGoal() { return phase == Phase.MOVE && target != null ? Vec3.atCenterOf(target) : null; }

    @Override
    public void start(AgentEntity agent) {
        beginScan(agent);
    }

    @Override
    public void stop(AgentEntity agent, String reason) {
        agent.getNavigation().stop();
        if (travel != null) travel.stop(agent, reason);
        ServerLevel level = (ServerLevel) agent.level();
        if (target != null) {
            level.destroyBlockProgress(agent.getId(), target, -1);
            Blackboard.release(level.dimension(), target, agent.getUUID());
        }
    }

    private void beginScan(AgentEntity agent) {
        phase = Phase.SCAN;
        scanIndex = 0;
        scanOrigin = agent.blockPosition();
        candidates.clear();
    }

    @Override
    public TaskStatus tick(AgentEntity agent) {
        ServerLevel level = (ServerLevel) agent.level();
        if (collected >= count) {
            result = "collected " + collected + " (" + matcher.label() + ")";
            return TaskStatus.SUCCESS;
        }
        return switch (phase) {
            case SCAN -> scan(agent, level);
            case MOVE -> move(agent, level);
            case BREAK -> breakBlock(agent, level);
            case TRAVEL -> travel(agent);
        };
    }

    private TaskStatus scan(AgentEntity agent, ServerLevel level) {
        int side = radius * 2 + 1;
        int height = vertical * 2 + 1;
        int total = side * side * height;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int end = Math.min(total, scanIndex + SCAN_BUDGET_PER_TICK);
        for (; scanIndex < end; scanIndex++) {
            int i = scanIndex;
            int dy = i % height - vertical;
            int rest = i / height;
            int dx = rest % side - radius;
            int dz = rest / side - radius;
            p.set(scanOrigin.getX() + dx, scanOrigin.getY() + dy, scanOrigin.getZ() + dz);
            if (!level.hasChunkAt(p)) continue;
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && matcher.test().test(s) && candidates.size() < 1024) candidates.add(p.immutable());
        }
        if (scanIndex < total) return TaskStatus.RUNNING;
        scannedOnce = true;
        return pickNext(agent, level);
    }

    private TaskStatus pickNext(AgentEntity agent, ServerLevel level) {
        long now = level.getGameTime();
        BlockPos feet = agent.blockPosition();
        candidates.removeIf(c -> blacklist.contains(c) || !matcher.test().test(level.getBlockState(c)));
        candidates.sort(Comparator.comparingDouble(c -> c.distSqr(feet)));
        for (BlockPos c : candidates) {
            if (c.equals(feet.below())) continue;                       // never dig straight down
            if (c.getY() - feet.getY() > 3 && !Mining.inReach(agent, c)) continue; // too high to reach from the ground
            if (level.getBlockEntity(c) != null) continue;             // never break chests, furnaces...
            if (!isExposed(level, c)) continue;
            if (!Mining.isSafeToBreak(level, c)) continue;
            if (Blackboard.isClaimedByOther(level.dimension(), c, agent.getUUID(), now)) continue;
            BlockState state = level.getBlockState(c);
            if (!Mining.hasToolFor(agent, state)) {
                result = "need a " + toolName(state) + " to mine " + matcher.label() + " (collected " + collected + "/" + count + ")";
                return TaskStatus.FAILED;
            }
            Blackboard.claim(level.dimension(), c, agent.getUUID(), now, CLAIM_TICKS);
            target = c;
            phase = Phase.MOVE;
            repath = 0;
            pathFails = 0;
            return TaskStatus.RUNNING;
        }
        if (!scannedOnce) return TaskStatus.RUNNING;
        if (startTravel(agent)) return TaskStatus.RUNNING;
        result = (collected > 0 ? "only found enough for " + collected + "/" + count + "; " : "")
                + "no reachable " + matcher.label() + " within " + radius + " blocks"
                + (travelLog.isEmpty() ? "" : " (searched farther: " + String.join("; ", travelLog) + ")");
        return collected > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILED;
    }

    private TaskStatus move(AgentEntity agent, ServerLevel level) {
        if (target == null || !matcher.test().test(level.getBlockState(target))) {
            return nextTarget(agent, level);
        }
        if (Mining.inReach(agent, target) && agent.getNavigation().isDone() || Mining.inReach(agent, target) && agent.getEyePosition().distanceTo(Vec3.atCenterOf(target)) < 3.0) {
            agent.getNavigation().stop();
            phase = Phase.BREAK;
            progress = 0;
            breakTicks = 0;
            Mining.equipBestTool(agent, level.getBlockState(target));
            return TaskStatus.RUNNING;
        }
        if (--repath <= 0) {
            repath = 20;
            boolean ok = agent.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1, 1.0);
            if (!ok && ++pathFails >= 3) {
                blacklist.add(target);
                Blackboard.release(level.dimension(), target, agent.getUUID());
                return nextTarget(agent, level);
            }
        }
        if (agent.getNavigation().isDone() && !Mining.inReach(agent, target) && repath < 18) {
            if (++pathFails >= 3) {
                blacklist.add(target);
                return nextTarget(agent, level);
            }
            repath = 0;
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus nextTarget(AgentEntity agent, ServerLevel level) {
        if (target != null) Blackboard.release(level.dimension(), target, agent.getUUID());
        target = null;
        if (candidates.isEmpty()) {
            beginScan(agent);
            scannedOnce = false;
            return TaskStatus.RUNNING;
        }
        phase = Phase.SCAN;
        return pickNext(agent, level);
    }

    private TaskStatus breakBlock(AgentEntity agent, ServerLevel level) {
        if (target == null) return nextTarget(agent, level);
        BlockState state = level.getBlockState(target);
        if (!matcher.test().test(state)) {
            level.destroyBlockProgress(agent.getId(), target, -1);
            return nextTarget(agent, level);
        }
        if (!Mining.inReach(agent, target)) {
            phase = Phase.MOVE;
            return TaskStatus.RUNNING;
        }
        agent.getLookControl().setLookAt(Vec3.atCenterOf(target));
        ItemStack tool = agent.getMainHandItem();
        float perTick = Mining.progressPerTick(state, level, target, tool);
        if (perTick <= 0) {
            blacklist.add(target);
            return nextTarget(agent, level);
        }
        progress += perTick;
        if (breakTicks++ % 5 == 0) agent.swing(InteractionHand.MAIN_HAND);
        level.destroyBlockProgress(agent.getId(), target, Math.min(9, (int) (progress * 10)));
        if (progress >= 1.0f) {
            level.destroyBlockProgress(agent.getId(), target, -1);
            List<ItemStack> drops = Mining.harvest(agent, level, target);
            for (ItemStack d : drops) collected += d.getCount();
            Blackboard.release(level.dimension(), target, agent.getUUID());
            candidates.remove(target);
            target = null;
            if (collected >= count) {
                result = "collected " + collected + " (" + matcher.label() + ")";
                return TaskStatus.SUCCESS;
            }
            if (agent.isInventoryFull()) {
                result = "inventory full after collecting " + collected + "/" + count;
                return TaskStatus.SUCCESS;
            }
            phase = Phase.SCAN;
            return pickNext(agent, level);
        }
        return TaskStatus.RUNNING;
    }

    /** Nothing usable nearby: walk to a remembered spot, or explore toward new land, then scan again. */
    private boolean startTravel(AgentEntity agent) {
        if (!com.joserayos.aiplayers.config.AiPlayersConfig.get(com.joserayos.aiplayers.config.AiPlayersConfig.AUTO_EXPLORE)) return false;
        if (trips >= com.joserayos.aiplayers.config.AiPlayersConfig.get(com.joserayos.aiplayers.config.AiPlayersConfig.AUTO_EXPLORE_TRIPS)) return false;
        if (agent.isInventoryFull()) return false;
        trips++;
        var spot = KnownSpots.nearestElsewhere(agent, matcher, Math.max(16, radius / 2.0));
        if (spot.isPresent() && spot.get().dist(agent.getX(), agent.getY(), agent.getZ()) > Math.max(16, radius / 2.0)) {
            var s = spot.get();
            travel = new com.joserayos.aiplayers.skill.movement.GoToTask(new BlockPos(s.x(), s.y(), s.z()), 3.0);
        } else {
            travel = new com.joserayos.aiplayers.skill.movement.ExploreTask("auto", EXPLORE_STEP, matcher);
        }
        extraTicks += travel.timeoutTicks() + 600;
        travel.start(agent);
        if (travel instanceof com.joserayos.aiplayers.skill.movement.GoToTask) extraTicks += travel.timeoutTicks();
        phase = Phase.TRAVEL;
        agent.addEvent("collect_block: no " + matcher.label() + " nearby, " + travel.describe());
        return true;
    }

    private TaskStatus travel(AgentEntity agent) {
        if (travel == null) { beginScan(agent); scannedOnce = false; return TaskStatus.RUNNING; }
        TaskStatus st = travel.tick(agent);
        if (st == TaskStatus.RUNNING) return TaskStatus.RUNNING;
        travel.stop(agent, st.name().toLowerCase(java.util.Locale.ROOT));
        String msg = travel.resultMessage();
        if (travel instanceof com.joserayos.aiplayers.skill.movement.ExploreTask ex && ex.found() != null
                && Math.sqrt(ex.found().distToCenterSqr(agent.position())) > Math.max(4, radius - 4)) {
            // Spotted one from a distance: walk over before scanning again.
            travelLog.add(msg.indexOf("; saw:") > 0 ? msg.substring(0, msg.indexOf("; saw:")) : msg);
            travel = new com.joserayos.aiplayers.skill.movement.GoToTask(ex.found(), 3.0);
            travel.start(agent);
            extraTicks += travel.timeoutTicks();
            return TaskStatus.RUNNING;
        }
        int cut = msg.indexOf("; saw:");
        travelLog.add(cut > 0 ? msg.substring(0, cut) : msg);
        travel = null;
        blacklist.clear();
        beginScan(agent);
        scannedOnce = false;
        return TaskStatus.RUNNING;
    }

    static boolean isExposed(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (n.isAir() || !n.isSolidRender()) return true;
        }
        return false;
    }

    static String toolName(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return "pickaxe (better tier may be needed)";
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return "axe";
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return "shovel";
        return "better tool";
    }
}
