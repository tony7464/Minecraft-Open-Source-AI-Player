package com.joserayos.aiplayers.skill.build;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import com.joserayos.aiplayers.skill.ItemResolver;
import com.joserayos.aiplayers.skill.gather.Mining;
import com.joserayos.aiplayers.skill.movement.Navigation;
import com.joserayos.aiplayers.task.AgentTask;
import com.joserayos.aiplayers.task.TaskStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Builds a blueprint at an origin: checks materials first, then clears and places bottom-up. */
public class BuildTask implements AgentTask {
    /** Blocks accepted by the "any_building" material, in order of preference. */
    static final List<Item> BUILDING_BLOCKS = List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.STONE, Items.DIRT,
            Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS,
            Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.SANDSTONE, Items.STONE_BRICKS, Items.NETHERRACK, Items.ANDESITE,
            Items.DIORITE, Items.GRANITE, Items.TUFF, Items.DEEPSLATE);

    record Op(BlockPos pos, @Nullable Item item, BlockState state) {
        boolean clear() { return item == null; }
    }

    private final Blueprint blueprint;
    private final BlockPos origin;
    private final Deque<Op> ops = new ArrayDeque<>();
    private int total;
    private int placed;
    private int cooldown;
    private int repath;
    private int deferrals;
    private String result = "";

    public BuildTask(Blueprint blueprint, BlockPos origin) {
        this.blueprint = blueprint;
        this.origin = origin.immutable();
    }

    @Override public String id() { return "build"; }
    @Override public String describe() { return "build " + blueprint.name() + " at (" + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + ")"; }
    private @Nullable AgentEntity lastAgent;
    private @Nullable Vec3 standGoal;

    @Override public String progress() {
        Op op = ops.peekFirst();
        return placed + "/" + total + " blocks" + (op != null && lastAgent != null
                ? ", next " + op.pos().toShortString() + " from " + lastAgent.blockPosition().toShortString() : "");
    }
    @Override public String resultMessage() { return result; }
    @Override public int timeoutTicks() { return 20 * 60 + total * 20; }
    @Override public @Nullable Vec3 movementGoal() {
        Op op = ops.peekFirst();
        if (op == null || lastAgent != null && inReach(lastAgent, op.pos())) return null;
        return standGoal != null ? standGoal : Vec3.atCenterOf(op.pos());
    }

    @Override
    public void start(AgentEntity agent) {
        ServerLevel level = (ServerLevel) agent.level();
        Map<String, Item> resolved = new LinkedHashMap<>();
        Map<Item, Integer> needed = new LinkedHashMap<>();
        List<Op> list = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Blueprint.Cell c : blueprint.cells()) {
            BlockPos pos = origin.offset(c.x(), c.y(), c.z());
            if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) {
                problems.add("part of it is outside the loaded world");
                break;
            }
            BlockState current = level.getBlockState(pos);
            if (c.spec().equals("air")) {
                if (!current.isAir() && !current.canBeReplaced()) list.add(new Op(pos, null, Blocks.air()));
                continue;
            }
            Item item = resolved.computeIfAbsent(c.spec(), s -> resolve(agent, s, blueprint.cells().stream().filter(x -> x.spec().equals(s)).count()));
            if (item == null || !(item instanceof BlockItem bi)) {
                problems.add("unknown material '" + c.spec() + "'");
                break;
            }
            Block block = bi.getBlock();
            if (current.is(block)) continue; // already there
            BlockState state = block.defaultBlockState();
            if (block instanceof DoorBlock) state = state.setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            list.add(new Op(pos, item, state));
            needed.merge(item, 1, Integer::sum);
        }
        if (problems.isEmpty()) {
            for (var e : needed.entrySet()) {
                int missing = e.getValue() - agent.countItem(e.getKey());
                if (missing > 0) problems.add(missing + " more " + ItemResolver.id(e.getKey()));
            }
            if (!problems.isEmpty()) problems.set(0, "missing materials: " + problems.getFirst());
        }
        if (list.stream().anyMatch(Op::clear) && !AiPlayersConfig.get(AiPlayersConfig.ALLOW_BREAK_BLOCKS)) problems.add("the site needs clearing but block breaking is disabled");
        if (!AiPlayersConfig.get(AiPlayersConfig.ALLOW_PLACE_BLOCKS)) problems.add("placing blocks is disabled on this server");
        if (!problems.isEmpty()) {
            result = "can't build " + blueprint.name() + ": " + String.join(", ", problems);
            return;
        }
        // Clear first (top-down), then place bottom-up so every block has support.
        list.sort(Comparator.comparing((Op o) -> !o.clear())
                .thenComparingInt(o -> o.clear() ? -o.pos().getY() : o.pos().getY())
                .thenComparingInt(o -> o.pos().getZ()).thenComparingInt(o -> o.pos().getX()));
        ops.addAll(list);
        total = list.size();
        if (blueprint.name().equals("shelter")) {
            // Stand exactly in the middle so the walls can go up around us.
            agent.getNavigation().stop();
            agent.teleportTo(origin.getX() + 1.5, origin.getY(), origin.getZ() + 1.5);
        }
    }

    /** A free spot on the build's ground level near the op, where the agent can stand and reach it. */
    private @Nullable BlockPos standSpot(ServerLevel level, AgentEntity agent, BlockPos target) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        java.util.Set<BlockPos> pending = new java.util.HashSet<>();
        for (Op o : ops) if (!o.clear()) pending.add(o.pos());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = origin.getY() - 1; y <= origin.getY() + 1; y++) {
                    BlockPos p = new BlockPos(target.getX() + dx, y, target.getZ() + dz);
                    if (pending.contains(p) || pending.contains(p.above())) continue;
                    if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) continue;
                    if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) continue;
                    if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) continue;
                    Vec3 eye = Vec3.atBottomCenterOf(p).add(0, agent.getEyeHeight(), 0);
                    if (eye.distanceTo(Vec3.atCenterOf(target)) > REACH - 0.3) continue;
                    double d = p.distSqr(agent.blockPosition());
                    if (d < bestD) {
                        bestD = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    /** Material spec -> concrete item, choosing what the agent has most of for tags and any_building. */
    static @Nullable Item resolve(AgentEntity agent, String spec, long countNeeded) {
        if (spec.equals("any_building")) {
            return BUILDING_BLOCKS.stream().max(Comparator.comparingInt(agent::countItem)).orElse(Items.COBBLESTONE);
        }
        if (spec.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(spec.substring(1)));
            Item best = null;
            int bestCount = -1;
            for (var h : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                int n = agent.countItem(h.value());
                if (n > bestCount) {
                    bestCount = n;
                    best = h.value();
                }
            }
            return best;
        }
        return ItemResolver.item(spec).orElse(null);
    }

    static final double REACH = 4.5;

    static boolean inReach(@Nullable AgentEntity agent, BlockPos pos) {
        return agent != null && agent.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= REACH;
    }

    @Override
    public TaskStatus tick(AgentEntity agent) {
        if (!result.isEmpty() && ops.isEmpty() && placed == 0) return TaskStatus.FAILED;
        lastAgent = agent;
        if (cooldown-- > 0) return TaskStatus.RUNNING;
        Op op = ops.peekFirst();
        if (op == null) {
            result = "built " + blueprint.name() + " (" + placed + " blocks changed)";
            return TaskStatus.SUCCESS;
        }
        ServerLevel level = (ServerLevel) agent.level();
        // Too far, or standing on top of the build (agents can hop onto fresh walls): walk to a ground spot.
        boolean onTop = agent.getY() > origin.getY() + 0.9 && agent.onGround();
        if (!inReach(agent, op.pos()) || onTop) {
            if (--repath <= 0) {
                repath = 20;
                BlockPos spot = standSpot(level, agent, op.pos());
                standGoal = spot == null ? Vec3.atCenterOf(op.pos()) : Vec3.atBottomCenterOf(spot);
                if (spot != null) agent.getNavigation().moveTo(standGoal.x, standGoal.y, standGoal.z, 1.0);
                else Navigation.moveToward(agent, standGoal, 1.0);
            }
            if (!onTop || !inReach(agent, op.pos())) return TaskStatus.RUNNING;
        }
        agent.getLookControl().setLookAt(Vec3.atCenterOf(op.pos()));
        if (op.clear()) {
            BlockState s = level.getBlockState(op.pos());
            if (!s.isAir() && !Mining.isSafeToBreak(level, op.pos())) {
                result = "unsafe block in the way at " + op.pos().toShortString();
                return TaskStatus.FAILED;
            }
            level.destroyBlock(op.pos(), true, agent);
        } else {
            boolean occupied = !level.getEntities(null, new AABB(op.pos())).stream().filter(e -> e.blocksBuilding).toList().isEmpty()
                    || op.state().getBlock() instanceof DoorBlock && !level.getEntities(null, new AABB(op.pos().above())).stream().filter(e -> e.blocksBuilding).toList().isEmpty();
            if (occupied) {
                // Someone (maybe us) is standing there: try it later, step aside if it's us.
                if (++deferrals > total + 20) {
                    result = "something keeps standing where I need to build (" + placed + "/" + total + " done)";
                    return TaskStatus.FAILED;
                }
                ops.addLast(ops.removeFirst());
                if (agent.getBoundingBox().intersects(new AABB(op.pos()))) {
                    Vec3 away = agent.position().add(agent.position().subtract(Vec3.atCenterOf(origin)).normalize().scale(2));
                    agent.getNavigation().moveTo(away.x, away.y, away.z, 1.0);
                }
                cooldown = 5;
                return TaskStatus.RUNNING;
            }
            if (agent.countItem(op.item()) < 1) {
                result = "ran out of " + ItemResolver.id(op.item()) + " (" + placed + "/" + total + " done)";
                return TaskStatus.FAILED;
            }
            if (!level.getBlockState(op.pos()).canBeReplaced()) level.destroyBlock(op.pos(), true, agent);
            level.setBlock(op.pos(), op.state(), Block.UPDATE_ALL);
            if (op.state().getBlock() instanceof DoorBlock) {
                level.setBlock(op.pos().above(), op.state().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            }
            agent.removeItems(op.item(), 1);
            var sound = op.state().getSoundType();
            level.playSound(null, op.pos(), sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
            level.gameEvent(agent, GameEvent.BLOCK_PLACE, op.pos());
        }
        agent.swing(InteractionHand.MAIN_HAND);
        ops.removeFirst();
        placed++;
        cooldown = 2;
        return TaskStatus.RUNNING;
    }

    /** Tiny indirection so the air state reads clearly above. */
    static final class Blocks {
        static BlockState air() { return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(); }
    }
}
