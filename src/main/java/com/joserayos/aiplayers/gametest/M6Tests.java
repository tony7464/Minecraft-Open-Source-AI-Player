package com.joserayos.aiplayers.gametest;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.agent.AgentMode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Survival and combat GameTests (M6). */
final class M6Tests {
    private M6Tests() {}

    static void registerAll(AiGameTestsSkills.Registrar r) {
        r.add("guard_kills_zombie", "platform", 1200, M6Tests::guard);
        r.add("reflex_eats_when_hurt", "platform", 200, M6Tests::eats);
        r.add("equip_best_armor", "platform", 200, M6Tests::armor);
        r.add("never_attacks_owner", "platform", 100, M6Tests::noOwnerAttack);
    }

    private static void guard(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 6, 1, 6);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Guardian", 7, 1, 6);
        agent.setMode(AgentMode.STAY);
        agent.addItem(new ItemStack(Items.IRON_SWORD));
        AiGameTestsSkills.say(agent, owner, "!guard {\"radius\":12}");
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(14, 1, 14));
        zombie.setPersistenceRequired();
        zombie.setCanPickUpLoot(false);
        helper.succeedWhen(() -> {
            helper.assertTrue(agent.getMode() == AgentMode.GUARD, "not guarding");
            helper.assertTrue(!zombie.isAlive(), "zombie still alive, health " + zombie.getHealth() + ", agent target " + agent.getTarget());
            helper.assertTrue(agent.getMainHandItem().is(Items.IRON_SWORD), "should fight with the sword");
        });
    }

    private static void eats(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Hungry", 5, 1, 5);
        agent.setMode(AgentMode.STAY);
        agent.addItem(new ItemStack(Items.ROTTEN_FLESH, 4));
        agent.addItem(new ItemStack(Items.BREAD, 3));
        agent.setHealth(6);
        helper.succeedWhen(() -> {
            helper.assertTrue(agent.getHealth() >= 14, "health " + agent.getHealth());
            helper.assertTrue(agent.countItem(Items.BREAD) < 3, "didn't eat bread");
            helper.assertTrue(agent.countItem(Items.ROTTEN_FLESH) == 4, "ate rotten flesh");
        });
    }

    private static void armor(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 1, 1, 1);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Knight", 5, 1, 5);
        agent.setMode(AgentMode.STAY);
        agent.addItem(new ItemStack(Items.LEATHER_CHESTPLATE));
        agent.addItem(new ItemStack(Items.IRON_CHESTPLATE));
        agent.addItem(new ItemStack(Items.IRON_HELMET));
        agent.addItem(new ItemStack(Items.WOODEN_SWORD));
        agent.addItem(new ItemStack(Items.STONE_SWORD));
        AiGameTestsSkills.say(agent, owner, "!equip_best_armor {}");
        helper.succeedWhen(() -> {
            helper.assertTrue(agent.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "chest: " + agent.getItemBySlot(EquipmentSlot.CHEST));
            helper.assertTrue(agent.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "head");
            helper.assertTrue(agent.getMainHandItem().is(Items.STONE_SWORD), "hand: " + agent.getMainHandItem());
            helper.assertTrue(agent.getArmorValue() >= 8, "armor " + agent.getArmorValue());
        });
    }

    private static void noOwnerAttack(GameTestHelper helper) {
        ServerPlayer owner = AiGameTests.owner(helper, 3, 1, 3);
        AgentEntity agent = AiGameTests.agent(helper, owner, "Loyal", 4, 1, 4);
        agent.setMode(AgentMode.STAY);
        agent.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(owner), 2f);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(agent.getTarget() == null, "agent targeted its owner");
            helper.succeed();
        });
    }
}
