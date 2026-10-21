package com.joserayos.aiplayers.skill.build;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.joserayos.aiplayers.AiPlayersMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.neoforged.fml.loading.FMLPaths;

/** Built-in blueprints (in the mod jar) plus user blueprints from {@code config/aiplayers/blueprints/*.json}. */
public final class Blueprints {
    public static final List<String> BUILT_IN = List.of("hut", "shelter");

    private Blueprints() {}

    public static Map<String, Blueprint> all() {
        Map<String, Blueprint> out = new TreeMap<>();
        for (String n : BUILT_IN) {
            try (InputStream in = Blueprints.class.getResourceAsStream("/data/aiplayers/blueprints/" + n + ".json")) {
                if (in == null) continue;
                out.put(n, Blueprint.fromJson(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject()));
            } catch (IOException | RuntimeException e) {
                AiPlayersMod.LOGGER.warn("Bad built-in blueprint {}: {}", n, e.toString());
            }
        }
        Path dir = userDir();
        if (dir != null && Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    try {
                        JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                        Blueprint b = Blueprint.fromJson(o);
                        out.put(b.name().toLowerCase(Locale.ROOT), b);
                    } catch (IOException | RuntimeException e) {
                        AiPlayersMod.LOGGER.warn("Bad blueprint {}: {}", f, e.toString());
                    }
                }
            } catch (IOException e) {
                AiPlayersMod.LOGGER.warn("Can't list {}: {}", dir, e.toString());
            }
        }
        return out;
    }

    public static Optional<Blueprint> get(String name) {
        return Optional.ofNullable(all().get(name.strip().toLowerCase(Locale.ROOT)));
    }

    static Path userDir() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve("aiplayers").resolve("blueprints");
        } catch (RuntimeException e) {
            return null;
        }
    }
}
