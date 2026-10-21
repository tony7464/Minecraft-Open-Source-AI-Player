package com.joserayos.aiplayers.skill.inventory;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.skill.ItemResolver;
import com.joserayos.aiplayers.skill.movement.Navigation;
import com.joserayos.aiplayers.task.AgentTask;
import com.joserayos.aiplayers.task.TaskStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Walk to a container (chest, barrel...) and deposit or withdraw items. */
public class ContainerTask implements AgentTask {
    public enum Kind { DEPOSIT, WITHDRAW }

    private final Kind kind;
    private final BlockPos pos;
    private final @Nullable Item item; // null = everything (deposit only)
    private final int count;
    private int repath;
    private String result = "";

    public ContainerTask(Kind kind, BlockPos pos, @Nullable Item item, int count) {
        this.kind = kind;
        this.pos = pos;
        this.item = item;
        this.count = count;
    }

    @Override public String id() { return kind == Kind.DEPOSIT ? "deposit" : "withdraw"; }

    @Override
    public String describe() {
        return id() + " " + (item == null ? "all" : count + " " + ItemResolver.id(item)) + " at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    @Override public void start(AgentEntity agent) {}

    @Override public String resultMessage() { return result; }

    @Override public @Nullable Vec3 movementGoal() { return Vec3.atCenterOf(pos); }

    public static @Nullable Container containerAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        return level.getBlockEntity(pos) instanceof Container c ? c : null;
    }

    @Override
    public TaskStatus tick(AgentEntity agent) {
        ServerLevel level = (ServerLevel) agent.level();
        Container container = containerAt(level, pos);
        if (container == null) {
            result = "there is no chest or container at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
            return TaskStatus.FAILED;
        }
        if (agent.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > 3.5) {
            if (--repath <= 0) {
                repath = 20;
                Navigation.moveToward(agent, Vec3.atCenterOf(pos), 1.0);
            }
            return TaskStatus.RUNNING;
        }
        agent.getNavigation().stop();
        agent.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        int moved = kind == Kind.DEPOSIT ? deposit(agent, container) : withdraw(agent, container);
        container.setChanged();
        if (moved == 0) {
            result = kind == Kind.DEPOSIT ? "nothing deposited (container full or I had none)" : "nothing withdrawn (none in the container, or my inventory is full)";
            return TaskStatus.FAILED;
        }
        result = (kind == Kind.DEPOSIT ? "deposited " : "withdrew ") + moved + " item(s)";
        return TaskStatus.SUCCESS;
    }

    private int deposit(AgentEntity agent, Container container) {
        int moved = 0;
        int remaining = item == null ? Integer.MAX_VALUE : count;
        var inv = agent.getInventory();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || item != null && !s.is(item)) continue;
            int take = Math.min(remaining, s.getCount());
            ItemStack part = s.copyWithCount(take);
            ItemStack rest = insert(container, part);
            int done = take - rest.getCount();
            s.shrink(done);
            moved += done;
            remaining -= done;
            if (!rest.isEmpty()) break;
        }
        return moved;
    }

    private int withdraw(AgentEntity agent, Container container) {
        int moved = 0;
        int remaining = count;
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty() || !s.is(item)) continue;
            int take = Math.min(remaining, s.getCount());
            ItemStack rest = agent.addItem(s.copyWithCount(take));
            int done = take - rest.getCount();
            s.shrink(done);
            moved += done;
            remaining -= done;
            if (!rest.isEmpty()) break;
        }
        return moved;
    }

    /** Insert into any container, merging with existing stacks first. Returns what didn't fit. */
    static ItemStack insert(Container c, ItemStack stack) {
        for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slot = c.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack) && c.canPlaceItem(i, stack)) {
                int space = Math.min(c.getMaxStackSize(slot), slot.getMaxStackSize()) - slot.getCount();
                int add = Math.min(space, stack.getCount());
                if (add > 0) {
                    slot.grow(add);
                    stack.shrink(add);
                }
            }
        }
        for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
            if (c.getItem(i).isEmpty() && c.canPlaceItem(i, stack)) {
                c.setItem(i, stack.split(Math.min(stack.getCount(), c.getMaxStackSize(stack))));
            }
        }
        return stack;
    }
}
