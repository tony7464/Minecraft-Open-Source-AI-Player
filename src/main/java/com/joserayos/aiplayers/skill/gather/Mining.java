package com.joserayos.aiplayers.skill.gather;

import com.joserayos.aiplayers.agent.AgentEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Block-breaking helpers mirroring vanilla's destroy-progress math. Server thread only. */
public final class Mining {
    public static final double REACH = 4.5;

    private Mining() {}

    /** Fraction of the block broken per tick with {@code tool} (vanilla formula, without enchantments/effects). */
    public static float progressPerTick(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return 0;
        if (hardness == 0) return 1;
        float speed = tool.getDestroySpeed(state);
        boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        return speed / hardness / (correct ? 30f : 100f);
    }

    public static boolean canHarvest(BlockState state, ItemStack tool) {
        return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    }

    /** Move the best tool for {@code state} from the inventory into the main hand. Returns the held stack. */
    public static ItemStack equipBestTool(AgentEntity agent, BlockState state) {
        SimpleContainer inv = agent.getInventory();
        ItemStack held = agent.getMainHandItem();
        int bestSlot = -1;
        float bestScore = score(held, state);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            float sc = score(inv.getItem(i), state);
            if (sc > bestScore) {
                bestScore = sc;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0) {
            ItemStack best = inv.getItem(bestSlot);
            inv.setItem(bestSlot, held);
            agent.setItemSlot(EquipmentSlot.MAINHAND, best);
        }
        return agent.getMainHandItem();
    }

    private static float score(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) return 1f;
        float s = stack.getDestroySpeed(state);
        if (canHarvest(state, stack)) s += 1000f;
        return s;
    }

    /** True if any inventory/held item can harvest the block. */
    public static boolean hasToolFor(AgentEntity agent, BlockState state) {
        if (!state.requiresCorrectToolForDrops()) return true;
        if (agent.getMainHandItem().isCorrectToolForDrops(state)) return true;
        for (ItemStack s : agent.getInventory().getItems()) if (s.isCorrectToolForDrops(state)) return true;
        return false;
    }

    /** Never open a hole into lava or water next to the block. */
    public static boolean isSafeToBreak(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (!level.getFluidState(pos.relative(d)).isEmpty()) return false;
        }
        return true;
    }

    public static boolean inReach(AgentEntity agent, BlockPos pos) {
        return agent.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= REACH;
    }

    /** Break the block: drops go straight into the agent's inventory (overflow is dropped on the ground). */
    public static List<ItemStack> harvest(AgentEntity agent, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ItemStack tool = agent.getMainHandItem();
        BlockEntity be = level.getBlockEntity(pos);
        List<ItemStack> drops = canHarvest(state, tool) ? Block.getDrops(state, level, pos, be, agent, tool) : List.of();
        level.destroyBlock(pos, false, agent);
        for (ItemStack d : drops) {
            ItemStack rest = agent.addItem(d.copy());
            if (!rest.isEmpty()) Block.popResource(level, pos, rest);
        }
        if (!tool.isEmpty() && tool.isDamageableItem()) tool.hurtAndBreak(1, agent, EquipmentSlot.MAINHAND);
        return drops;
    }
}
