package com.joserayos.aiplayers.perception;

import com.joserayos.aiplayers.agent.AgentEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Builds an {@link AgentSnapshot} on the server thread. Keep this cheap: it runs on every think. */
public final class SnapshotBuilder {
    public static final double RADIUS = 32.0;
    private static final int MAX_PER_CATEGORY = 6;

    private SnapshotBuilder() {}

    public static AgentSnapshot capture(AgentEntity agent) {
        ServerLevel level = (ServerLevel) agent.level();
        ServerPlayer owner = agent.getOwnerPlayer();
        String ownerName = owner != null ? owner.getGameProfile().name() : "(offline)";
        String ownerInfo;
        if (owner == null) ownerInfo = "offline";
        else if (owner.level() != level) ownerInfo = "in another dimension";
        else ownerInfo = describeRelative(agent, owner) + " at (" + owner.getBlockX() + ", " + owner.getBlockY() + ", " + owner.getBlockZ() + ")"
                + ", health " + Math.round(owner.getHealth()) + "/" + Math.round(owner.getMaxHealth());

        AABB box = agent.getBoundingBox().inflate(RADIUS);
        List<String> players = new ArrayList<>();
        List<String> agents = new ArrayList<>();
        List<String> threats = new ArrayList<>();
        List<Entity> nearby = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box, e -> e != agent && e.isAlive()));
        nearby.sort(Comparator.comparingDouble(agent::distanceToSqr));
        for (Entity e : nearby) {
            if (e instanceof Player p && players.size() < MAX_PER_CATEGORY && !p.isSpectator()) {
                players.add(p.getGameProfile().name() + " " + describeRelative(agent, p) + (agent.isOwnedBy(p) ? " (owner)" : ""));
            } else if (e instanceof AgentEntity other && agents.size() < MAX_PER_CATEGORY) {
                agents.add(other.getAgentName() + " " + describeRelative(agent, other) + " (mode " + other.getMode().id() + ")");
            } else if (e instanceof Enemy && threats.size() < MAX_PER_CATEGORY) {
                threats.add(EntityType.getKey(e.getType()).getPath() + " " + describeRelative(agent, e));
            }
        }

        String biome = level.getBiome(agent.blockPosition()).unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
        return new AgentSnapshot(
                agent.getAgentName(), ownerName, agent.getMode().id(),
                agent.getHealth(), agent.getMaxHealth(),
                agent.getBlockX(), agent.getBlockY(), agent.getBlockZ(),
                level.dimension().identifier().getPath(), biome,
                describeTime(level), level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear",
                ownerInfo, players, agents, threats,
                List.of(), itemName(agent.getMainHandItem()),
                agent.hasActiveTask() ? "busy" : "none", "none", List.of());
    }

    public static String itemName(ItemStack stack) {
        if (stack.isEmpty()) return "nothing";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + (stack.getCount() > 1 ? " x" + stack.getCount() : "");
    }

    /** e.g. "day 3, evening (about 11:00 to sunset)". */
    public static String describeTime(ServerLevel level) {
        long t = level.getOverworldClockTime();
        long day = t / 24000L + 1;
        long tod = Math.floorMod(t, 24000L);
        String part;
        if (tod < 1000) part = "early morning";
        else if (tod < 6000) part = "morning";
        else if (tod < 11000) part = "afternoon";
        else if (tod < 13000) part = "evening (sunset soon)";
        else if (tod < 23000) part = "night (monsters spawn)";
        else part = "dawn";
        int hours = (int) ((tod / 1000 + 6) % 24);
        int minutes = (int) ((tod % 1000) * 60 / 1000);
        return "day " + day + ", " + part + String.format(" (%02d:%02d)", hours, minutes);
    }

    /** e.g. "9m NE". */
    public static String describeRelative(Entity from, Entity to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        int dist = (int) Math.round(from.distanceTo(to));
        String vertical = dy > 3 ? " above" : dy < -3 ? " below" : "";
        return dist + "m " + compass(dx, dz) + vertical;
    }

    /** Compass direction where -Z is north and +X is east. */
    public static String compass(double dx, double dz) {
        if (Math.abs(dx) < 0.5 && Math.abs(dz) < 0.5) return "here";
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) angle += 360;
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[(int) Math.round(angle / 45.0) % 8];
    }
}
