package com.joserayos.aiplayers.skill.combat;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Combat rules and equipment helpers shared by goals, reflexes and tools. */
public final class Combat {
    private Combat() {}

    /** Hostile mobs the agent may attack on its own (defense / guard). */
    public static boolean isHostile(LivingEntity e) {
        return e instanceof Enemy && e.isAlive();
    }

    /** Whether the agent is allowed to attack this entity at all (explicit orders included). */
    public static boolean mayAttack(AgentEntity agent, LivingEntity e) {
        if (e == agent || !e.isAlive()) return false;
        if (e instanceof AgentEntity) return false;
        if (e instanceof Player p) {
            if (agent.isOwnedBy(p) || p.isCreative() || p.isSpectator()) return false;
            return AiPlayersConfig.get(AiPlayersConfig.ALLOW_ATTACK_PLAYERS);
        }
        if (e instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame()) return false;
        if (!(e instanceof Enemy)) return AiPlayersConfig.get(AiPlayersConfig.ALLOW_ATTACK_PASSIVE_MOBS);
        return true;
    }

    static double attackValue(ItemStack s) {
        return s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
    }

    static double armorValue(ItemStack s, EquipmentSlot slot) {
        var mods = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return mods.compute(Attributes.ARMOR, 0, slot) * 10 + mods.compute(Attributes.ARMOR_TOUGHNESS, 0, slot);
    }

    /** Swap the best weapon from the inventory into the main hand. Returns true if changed. */
    public static boolean equipBestWeapon(AgentEntity agent) {
        var inv = agent.getInventory();
        double best = attackValue(agent.getMainHandItem());
        int bestSlot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            double v = attackValue(inv.getItem(i));
            if (v > best + 0.01) {
                best = v;
                bestSlot = i;
            }
        }
        if (bestSlot < 0) return false;
        ItemStack weapon = inv.getItem(bestSlot);
        inv.setItem(bestSlot, agent.getMainHandItem());
        agent.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        return true;
    }

    /** Wear the best armor piece from the inventory in every armor slot. Returns the pieces equipped. */
    public static java.util.List<String> equipBestArmor(AgentEntity agent) {
        java.util.List<String> changed = new java.util.ArrayList<>();
        var inv = agent.getInventory();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            double best = armorValue(agent.getItemBySlot(slot), slot);
            int bestSlot = -1;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                var eq = s.get(DataComponents.EQUIPPABLE);
                if (eq == null || eq.slot() != slot) continue;
                double v = armorValue(s, slot);
                if (v > best) {
                    best = v;
                    bestSlot = i;
                }
            }
            if (bestSlot >= 0) {
                ItemStack piece = inv.getItem(bestSlot);
                inv.setItem(bestSlot, agent.getItemBySlot(slot));
                agent.setItemSlot(slot, piece);
                agent.setGuaranteedDrop(slot);
                changed.add(com.joserayos.aiplayers.skill.ItemResolver.id(piece.getItem()));
            }
        }
        return changed;
    }
}
