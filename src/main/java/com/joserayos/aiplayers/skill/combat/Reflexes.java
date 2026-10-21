package com.joserayos.aiplayers.skill.combat;

import com.joserayos.aiplayers.agent.AgentEntity;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Fast, LLM-free survival behaviour run from the agent's tick. */
public final class Reflexes {
    public static final float EAT_BELOW = 14f;
    public static final float FLEE_BELOW = 6f;

    private final AgentEntity agent;
    private int eatCooldown;
    private int regenTimer;
    private int fleeTicks;
    private int lastHurtTick = -1000;

    public Reflexes(AgentEntity agent) {
        this.agent = agent;
    }

    public boolean isFleeing() { return fleeTicks > 0; }

    public void onHurt() { lastHurtTick = agent.tickCount; }

    public void tick(ServerLevel level) {
        if (eatCooldown > 0) eatCooldown--;
        // Escape lava / fire first.
        if (agent.isInLava() || agent.isOnFire() && agent.level().getBlockState(agent.blockPosition()).is(net.minecraft.tags.BlockTags.FIRE)) {
            BlockPos safe = findSafe(level);
            if (safe != null) {
                agent.getNavigation().moveTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, 1.6);
                agent.getJumpControl().jump();
            }
        }
        if (agent.tickCount % 5 != 0) return;
        float hp = agent.getHealth();
        if (hp < EAT_BELOW && eatCooldown == 0) eatBest();
        if (fleeTicks > 0) fleeTicks -= 5;
        if (hp < FLEE_BELOW && fleeTicks <= 0) {
            Mob threat = nearestThreat(10);
            if (threat != null) {
                Vec3 away = DefaultRandomPos.getPosAway(agent, 16, 7, threat.position());
                if (away != null) {
                    agent.setTarget(null);
                    agent.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
                    fleeTicks = 60;
                    agent.addEvent("fled from " + threat.getType().getDescription().getString() + " at low health");
                }
            }
        }
        // Slow natural regeneration when not hurt recently (stands in for a hunger bar).
        if (hp < agent.getMaxHealth() && agent.tickCount - lastHurtTick > 100 && ++regenTimer >= 16) {
            regenTimer = 0;
            agent.heal(1f);
        }
    }

    /** Eats the most nourishing food. Returns a description or null if there is none. */
    public @Nullable String eatBest() {
        var inv = agent.getInventory();
        int bestSlot = -1;
        int bestNutrition = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            FoodProperties food = s.get(DataComponents.FOOD);
            if (food == null || isRisky(s.getItem())) continue;
            if (food.nutrition() > bestNutrition) {
                bestNutrition = food.nutrition();
                bestSlot = i;
            }
        }
        if (bestSlot < 0) return null;
        ItemStack s = inv.getItem(bestSlot);
        FoodProperties food = s.get(DataComponents.FOOD);
        String name = com.joserayos.aiplayers.skill.ItemResolver.id(s.getItem());
        s.shrink(1);
        agent.heal(food.nutrition() + food.saturation() / 2f);
        agent.playSound(SoundEvents.GENERIC_EAT.value(), 0.6f, 1.0f);
        eatCooldown = 32;
        return name;
    }

    static boolean isRisky(Item item) {
        String id = com.joserayos.aiplayers.skill.ItemResolver.id(item);
        return id.equals("rotten_flesh") || id.equals("spider_eye") || id.equals("poisonous_potato") || id.equals("pufferfish")
                || id.equals("chorus_fruit") || id.equals("suspicious_stew");
    }

    private @Nullable Mob nearestThreat(double radius) {
        return agent.level().getEntitiesOfClass(Mob.class, agent.getBoundingBox().inflate(radius), Combat::isHostile)
                .stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(agent))).orElse(null);
    }

    private @Nullable BlockPos findSafe(ServerLevel level) {
        BlockPos base = agent.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-6, -2, -6), base.offset(6, 3, 6))) {
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) continue;
            var below = level.getBlockState(p.below());
            if (!below.isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP) || !below.getFluidState().isEmpty()) continue;
            if (!level.getFluidState(p).isEmpty()) continue;
            double d = p.distSqr(base);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }
}
