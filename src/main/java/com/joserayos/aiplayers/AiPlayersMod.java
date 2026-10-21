package com.joserayos.aiplayers;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Entry point for the AI Players mod. */
@Mod(AiPlayersMod.MODID)
public final class AiPlayersMod {
    public static final String MODID = "aiplayers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AiPlayersMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("AI Players loaded");
    }
}
