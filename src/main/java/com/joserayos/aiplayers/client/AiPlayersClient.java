package com.joserayos.aiplayers.client;

import com.joserayos.aiplayers.AiPlayersMod;
import com.joserayos.aiplayers.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point: renderers and the config screen. */
@Mod(value = AiPlayersMod.MODID, dist = Dist.CLIENT)
public final class AiPlayersClient {
    public AiPlayersClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(AiPlayersClient::registerRenderers);
        modBus.addListener(ClientNetwork::onRegister);
        com.joserayos.aiplayers.client.voice.PushToTalk.register(modBus);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.AGENT.get(), AgentRenderer::new);
    }
}
