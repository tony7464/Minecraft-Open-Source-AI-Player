package com.joserayos.aiplayers.client;

import com.joserayos.aiplayers.network.AgentListPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client-side payload handlers. */
final class ClientNetwork {
    private ClientNetwork() {}

    static void onRegister(RegisterClientPayloadHandlersEvent event) {
        event.register(AgentListPayload.TYPE, (payload, ctx) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof AgentManagerScreen screen) screen.update(payload);
            else if (payload.open()) mc.setScreen(new AgentManagerScreen(payload));
        });
    }
}
