package com.joserayos.aiplayers.skill.build;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

/** Helpers for placing blocks and finding placed blocks. */
public final class Placement {
    private Placement() {}

    /** Can a block go at pos (replaceable, sturdy floor, nobody standing in it)? */
    public static boolean canPlaceAt(ServerLevel level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        if (!here.canBeReplaced()) return false;
        if (!here.getFluidState().isEmpty()) return false;
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;
        return level.getEntities(null, new AABB(pos)).stream().noneMatch(e -> e.blocksBuilding);
    }

    /** Places `item` (a block item) from the agent's inventory at `pos`. Returns an error or null. */
    public static String place(AgentEntity agent, BlockPos pos, Item item) {
        if (!AiPlayersConfig.get(AiPlayersConfig.ALLOW_PLACE_BLOCKS)) return "placing blocks is disabled on this server";
        if (!(item instanceof BlockItem bi)) return "that item is not a block";
        ServerLevel level = (ServerLevel) agent.level();
        if (!canPlaceAt(level, pos)) return "can't place there";
        if (agent.countItem(item) < 1) return "I have no " + item;
        Block block = bi.getBlock();
        BlockState state = block.defaultBlockState();
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) return "placement failed";
        agent.removeItems(item, 1);
        agent.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        var sound = state.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
        level.gameEvent(agent, GameEvent.BLOCK_PLACE, pos);
        return null;
    }

    /** Finds a free spot next to the agent (not where it stands). */
    public static Optional<BlockPos> spotNear(AgentEntity agent) {
        ServerLevel level = (ServerLevel) agent.level();
        BlockPos base = agent.blockPosition();
        for (int r = 1; r <= 3; r++) {
            for (int dy = 0; dy >= -1; dy--) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos p = base.offset(dx, dy, dz);
                        if (canPlaceAt(level, p) && !agent.getBoundingBox().intersects(new AABB(p))) return Optional.of(p);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Nearest block matching `test` within `radius` (cube scan; keep radius small). */
    public static Optional<BlockPos> findNearest(AgentEntity agent, int radius, Predicate<BlockState> test) {
        ServerLevel level = (ServerLevel) agent.level();
        BlockPos center = agent.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius / 2, -radius), center.offset(radius, radius / 2, radius))) {
            if (!level.hasChunkAt(p)) continue;
            if (test.test(level.getBlockState(p))) {
                double d = p.distSqr(center);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
