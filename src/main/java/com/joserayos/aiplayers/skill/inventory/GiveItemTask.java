package com.joserayos.aiplayers.skill.inventory;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.skill.ItemResolver;
import com.joserayos.aiplayers.skill.movement.GoToEntityTask;
import com.joserayos.aiplayers.task.TaskStatus;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Walk to a player and toss them items from the agent's inventory. */
public class GiveItemTask extends GoToEntityTask {
    private final Item item;
    private final int count;
    private String result = "";

    public GiveItemTask(UUID playerId, String playerName, Item item, int count) {
        super(playerId, playerName, 3.0);
        this.item = item;
        this.count = count;
    }

    @Override public String id() { return "give_item"; }

    @Override public String describe() { return "give_item " + count + " " + ItemResolver.id(item); }

    @Override
    public TaskStatus tick(AgentEntity agent) {
        TaskStatus walk = super.tick(agent);
        if (walk != TaskStatus.SUCCESS) {
            if (walk == TaskStatus.FAILED) result = super.resultMessage();
            return walk;
        }
        Entity target = target(agent);
        if (target == null) {
            result = "player is gone";
            return TaskStatus.FAILED;
        }
        int have = agent.countItem(item);
        if (have <= 0) {
            result = "I don't have any " + ItemResolver.id(item);
            return TaskStatus.FAILED;
        }
        int give = Math.min(have, count);
        ItemStack removed = agent.removeItems(item, give);
        toss(agent, target, removed);
        result = "gave " + removed.getCount() + " " + ItemResolver.id(item) + (removed.getCount() < count ? " (that's all I had)" : "");
        return TaskStatus.SUCCESS;
    }

    /** Throw a stack toward a target, like a player dropping items. */
    public static void toss(AgentEntity agent, Entity target, ItemStack stack) {
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(Math.min(stack.getMaxStackSize(), stack.getCount()));
            ItemEntity drop = new ItemEntity(agent.level(), agent.getX(), agent.getEyeY() - 0.3, agent.getZ(), part);
            Vec3 dir = target.position().subtract(agent.position()).normalize().scale(0.3);
            drop.setDeltaMovement(dir.x, 0.2, dir.z);
            drop.setPickUpDelay(10);
            agent.level().addFreshEntity(drop);
        }
        agent.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    @Override public String resultMessage() { return result; }
}
