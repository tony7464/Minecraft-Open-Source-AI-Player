package com.joserayos.aiplayers.network;

import com.joserayos.aiplayers.AiPlayersMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: an Agent Manager button press. Validated on the server like the matching command. */
public record AgentActionPayload(String agent, String action, String arg) implements CustomPacketPayload {
    public static final Type<AgentActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AiPlayersMod.MODID, "agent_action"));

    public static final StreamCodec<FriendlyByteBuf, AgentActionPayload> CODEC = CustomPacketPayload.codec(
            (p, buf) -> {
                buf.writeUtf(p.agent, 64);
                buf.writeUtf(p.action, 32);
                buf.writeUtf(p.arg, 512);
            },
            buf -> new AgentActionPayload(buf.readUtf(64), buf.readUtf(32), buf.readUtf(512)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
