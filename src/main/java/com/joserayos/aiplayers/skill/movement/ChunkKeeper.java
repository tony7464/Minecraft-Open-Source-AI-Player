package com.joserayos.aiplayers.skill.movement;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * Keeps the chunks around a travelling agent loaded and ticking, using a short self-expiring ticket
 * (the same kind a thrown ender pearl uses). Refreshed once a second; when the agent stops travelling
 * the ticket simply times out, so nothing stays loaded forever.
 */
public final class ChunkKeeper {
    public static final int RADIUS = 2;

    private ChunkKeeper() {}

    public static void keep(AgentEntity agent) {
        if (agent.tickCount % 20 != 0 || !(agent.level() instanceof ServerLevel sl)) return;
        if (!AiPlayersConfig.get(AiPlayersConfig.KEEP_CHUNK_LOADED)) return;
        sl.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos.containing(agent.blockPosition()), RADIUS);
    }
}
