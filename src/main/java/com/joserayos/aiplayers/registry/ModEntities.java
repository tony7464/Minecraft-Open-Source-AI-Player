package com.joserayos.aiplayers.registry;

import com.joserayos.aiplayers.AiPlayersMod;
import com.joserayos.aiplayers.agent.AgentEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(AiPlayersMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<AgentEntity>> AGENT = ENTITIES.registerEntityType(
            "agent", AgentEntity::new, MobCategory.MISC,
            b -> b.sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10));

    private ModEntities() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(ModEntities::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(AGENT.get(), AgentEntity.createAttributes().build());
    }
}
