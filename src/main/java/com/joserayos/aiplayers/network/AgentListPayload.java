package com.joserayos.aiplayers.network;

import com.joserayos.aiplayers.AiPlayersMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: the agents the player can see in the Agent Manager, plus model profile names. */
public record AgentListPayload(List<Entry> agents, List<String> profiles, boolean open) implements CustomPacketPayload {
    public static final int MAX_AGENTS = 64;

    public record Entry(String name, String owner, String profile, String mode, String task, float health, float maxHealth,
                        String goal, boolean canControl) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(name, 64);
            buf.writeUtf(owner, 64);
            buf.writeUtf(profile, 64);
            buf.writeUtf(mode, 32);
            buf.writeUtf(task, 256);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
            buf.writeUtf(goal, 256);
            buf.writeBoolean(canControl);
        }

        static Entry read(FriendlyByteBuf buf) {
            return new Entry(buf.readUtf(64), buf.readUtf(64), buf.readUtf(64), buf.readUtf(32), buf.readUtf(256),
                    buf.readFloat(), buf.readFloat(), buf.readUtf(256), buf.readBoolean());
        }
    }

    public static final Type<AgentListPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AiPlayersMod.MODID, "agent_list"));

    public static final StreamCodec<FriendlyByteBuf, AgentListPayload> CODEC = CustomPacketPayload.codec(AgentListPayload::write, AgentListPayload::read);

    void write(FriendlyByteBuf buf) {
        buf.writeVarInt(Math.min(agents.size(), MAX_AGENTS));
        for (int i = 0; i < Math.min(agents.size(), MAX_AGENTS); i++) agents.get(i).write(buf);
        buf.writeVarInt(Math.min(profiles.size(), 64));
        for (int i = 0; i < Math.min(profiles.size(), 64); i++) buf.writeUtf(profiles.get(i), 64);
        buf.writeBoolean(open);
    }

    static AgentListPayload read(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_AGENTS);
        List<Entry> agents = new ArrayList<>(n);
        for (int i = 0; i < n; i++) agents.add(Entry.read(buf));
        int p = Math.min(buf.readVarInt(), 64);
        List<String> profiles = new ArrayList<>(p);
        for (int i = 0; i < p; i++) profiles.add(buf.readUtf(64));
        return new AgentListPayload(agents, profiles, buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
