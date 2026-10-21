package com.joserayos.aiplayers.team;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.joserayos.aiplayers.AiPlayersMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Persists the Blackboard's shared locations to {@code <world>/aiplayers/team.json}. */
public final class TeamStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TeamStore() {}

    static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("aiplayers").resolve("team.json");
    }

    public static void load(MinecraftServer server) {
        Blackboard.reset();
        Path f = file(server);
        if (!Files.exists(f)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(f), JsonObject.class);
            JsonObject locs = root.getAsJsonObject("locations");
            if (locs == null) return;
            for (var e : locs.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                var dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(o.get("dimension").getAsString()));
                Blackboard.setLocation(e.getKey(), GlobalPos.of(dim, new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt())));
            }
        } catch (IOException | RuntimeException e) {
            AiPlayersMod.LOGGER.warn("Could not read {}: {}", f, e.toString());
        }
    }

    public static void save(MinecraftServer server) {
        JsonObject root = new JsonObject();
        JsonObject locs = new JsonObject();
        for (var e : Blackboard.locations().entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("dimension", e.getValue().dimension().identifier().toString());
            o.addProperty("x", e.getValue().pos().getX());
            o.addProperty("y", e.getValue().pos().getY());
            o.addProperty("z", e.getValue().pos().getZ());
            locs.add(e.getKey(), o);
        }
        root.add("locations", locs);
        Path f = file(server);
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(root));
        } catch (IOException e) {
            AiPlayersMod.LOGGER.warn("Could not save {}: {}", f, e.toString());
        }
    }
}
