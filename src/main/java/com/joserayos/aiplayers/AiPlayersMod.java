package com.joserayos.aiplayers;

import com.joserayos.aiplayers.command.AiCommand;
import com.joserayos.aiplayers.command.ChatRouter;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import com.joserayos.aiplayers.gametest.AiGameTests;
import com.joserayos.aiplayers.registry.ModEntities;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.slf4j.Logger;

/** Entry point for the AI Players mod. */
@Mod(AiPlayersMod.MODID)
public final class AiPlayersMod {
    public static final String MODID = "aiplayers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AiPlayersMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, AiPlayersConfig.SPEC);
        ModEntities.register(modEventBus);
        AiGameTests.register(modEventBus);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> AiCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ServerChatEvent.class, ChatRouter::onServerChat);
        LOGGER.info("AI Players loaded");
    }
}
