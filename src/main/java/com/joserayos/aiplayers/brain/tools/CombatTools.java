package com.joserayos.aiplayers.brain.tools;

import com.joserayos.aiplayers.agent.AgentMode;
import com.joserayos.aiplayers.skill.ItemResolver;
import com.joserayos.aiplayers.skill.combat.Combat;
import java.util.Comparator;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** M6 tools: combat and survival. */
final class CombatTools {
    private CombatTools() {}

    static void register() {
        ToolRegistry.register(new Tool("attack",
                "Attack the nearest mob of a type (e.g. zombie, skeleton, cow) or a named entity within 24 blocks. "
                        + "You fight on your own until it dies. Never attacks your owner.",
                Schema.object().str("target", "Mob type or name", true).build(), Tool.Group.FIGHT,
                (agent, args, ctx) -> {
                    String want = ItemResolver.normalize(args.str("target"));
                    LivingEntity target = agent.level().getEntitiesOfClass(LivingEntity.class, agent.getBoundingBox().inflate(24), e -> {
                                String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
                                String name = e.getName().getString().toLowerCase(Locale.ROOT);
                                return (type.equals(want) || name.equals(want.replace('_', ' ')) || name.equals(want))
                                        && Combat.mayAttack(agent, e);
                            }).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(agent))).orElse(null);
                    if (target == null) return ToolResult.error("no attackable '" + args.str("target") + "' within 24 blocks (or not allowed)");
                    Combat.equipBestWeapon(agent);
                    agent.setTarget(target);
                    return ToolResult.done("attacking " + target.getName().getString() + " " + Math.round(target.distanceTo(agent)) + "m away");
                }));

        ToolRegistry.register(new Tool("guard",
                "Switch to GUARD mode: stay near a player (default: whoever is talking) or the current spot, and fight hostile mobs within `radius`.",
                Schema.object().str("player", "Player to guard, 'me', or 'here' for this spot", false)
                        .integer("radius", "Guard radius (default 10)", 4, 24, false).build(), Tool.Group.FIGHT,
                (agent, args, ctx) -> {
                    int radius = args.integer("radius", 4, 24, 10);
                    String who = args.str("player", "me");
                    if (who.equalsIgnoreCase("here")) {
                        agent.startGuard(null, agent.position(), radius);
                        return ToolResult.done("guarding this spot (radius " + radius + ")");
                    }
                    ServerPlayer p = ToolRegistry.player(agent, who, ctx);
                    agent.startGuard(p, null, radius);
                    return ToolResult.done("guarding " + p.getGameProfile().name() + " (radius " + radius + ")");
                }));

        ToolRegistry.register(new Tool("equip_best_armor", "Put on the best armor from your inventory and hold your best weapon.",
                Schema.object().build(), Tool.Group.ITEMS,
                (agent, args, ctx) -> {
                    var worn = Combat.equipBestArmor(agent);
                    boolean weapon = Combat.equipBestWeapon(agent);
                    if (worn.isEmpty() && !weapon) return ToolResult.done("already wearing the best gear I have (armor " + agent.getArmorValue() + ")");
                    return ToolResult.done("equipped " + String.join(", ", worn) + (weapon ? (worn.isEmpty() ? "" : ", ") + "weapon " + ItemResolver.id(agent.getMainHandItem().getItem()) : "")
                            + " (armor " + agent.getArmorValue() + ")");
                }));

        ToolRegistry.register(new Tool("eat", "Eat the best food in your inventory to heal.",
                Schema.object().build(), Tool.Group.ITEMS,
                (agent, args, ctx) -> {
                    if (agent.getHealth() >= agent.getMaxHealth()) return ToolResult.done("not hungry, health is full");
                    String food = agent.getReflexes().eatBest();
                    return food == null ? ToolResult.error("I have no safe food") : ToolResult.done("ate " + food + ", health " + Math.round(agent.getHealth()));
                }));

        ToolRegistry.register(new Tool("stop_attacking", "Stop fighting and leave GUARD mode (back to following).",
                Schema.object().build(), Tool.Group.FIGHT,
                (agent, args, ctx) -> {
                    agent.setTarget(null);
                    if (agent.getMode() == AgentMode.GUARD) agent.setMode(AgentMode.FOLLOW);
                    return ToolResult.done("stopped fighting");
                }));
    }

}
