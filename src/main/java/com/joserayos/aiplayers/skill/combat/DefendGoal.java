package com.joserayos.aiplayers.skill.combat;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Picks hostile targets without the LLM: mobs attacking the agent or its owner, and in GUARD mode
 * any hostile that comes within the guard radius of the guarded player/position.
 */
public class DefendGoal extends TargetGoal {
    private final AgentEntity agent;
    private @Nullable LivingEntity candidate;
    private int cooldown;

    public DefendGoal(AgentEntity agent) {
        super(agent, false, false);
        this.agent = agent;
        setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) return false;
        cooldown = 10;
        if (agent.isFleeing()) return false;
        candidate = find();
        return candidate != null;
    }

    private @Nullable LivingEntity find() {
        LivingEntity owner = agent.getOwnerPlayer();
        boolean guard = agent.getMode() == AgentMode.GUARD;
        Vec3 center = guard ? agent.guardCenter() : agent.position();
        double radius = guard ? agent.getGuardRadius() : 12;
        AABB box = new AABB(center, center).inflate(radius + 4, 6, radius + 4);
        return agent.level().getEntitiesOfClass(Mob.class, box, m -> {
                    if (!Combat.isHostile(m) || !Combat.mayAttack(agent, m)) return false;
                    if (!agent.getSensing().hasLineOfSight(m) && m.distanceTo(agent) > 4) return false;
                    LivingEntity t = m.getTarget();
                    boolean threat = t == agent || owner != null && t == owner;
                    boolean inZone = guard && m.position().distanceTo(center) <= radius;
                    return threat && m.distanceTo(agent) < 16 || inZone;
                }).stream()
                .min(Comparator.comparingDouble(m -> m.distanceToSqr(agent)))
                .orElse(null);
    }

    @Override
    public void start() {
        agent.setTarget(candidate);
        Combat.equipBestWeapon(agent);
        super.start();
    }
}
