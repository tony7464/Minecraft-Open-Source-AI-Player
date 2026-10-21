package com.joserayos.aiplayers.skill;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Turns model-provided names ("oak logs", "Oak_Log", "minecraft:oak_log") into registry entries. */
public final class ItemResolver {
    private ItemResolver() {}

    public static Optional<Item> item(String name) {
        return resolve(BuiltInRegistries.ITEM, name).map(BuiltInRegistries.ITEM::getValue);
    }

    public static Optional<Block> block(String name) {
        return resolve(BuiltInRegistries.BLOCK, name).map(BuiltInRegistries.BLOCK::getValue);
    }

    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    public static String id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    /** Normalizes "Oak Logs" -> "oak_logs". */
    public static String normalize(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        s = s.replaceAll("[^a-z0-9_:/.]", "");
        return s;
    }

    static <T> Optional<Identifier> resolve(Registry<T> registry, String raw) {
        String s = normalize(raw);
        if (s.isEmpty()) return Optional.empty();
        String[] candidates = {s, singular(s), s.replace("_wood", "_log"), singular(s).replace("_wood", "_log")};
        for (String c : candidates) {
            Identifier id = Identifier.tryParse(c.contains(":") ? c : "minecraft:" + c);
            if (id != null && registry.containsKey(id) && !id.getPath().equals("air")) return Optional.of(id);
        }
        // Fuzzy: shortest registry path that contains the query (e.g. "cobble" -> cobblestone).
        String path = s.contains(":") ? s.substring(s.indexOf(':') + 1) : s;
        String sing = singular(path);
        return registry.keySet().stream()
                .filter(id -> !id.getPath().equals("air"))
                .filter(id -> id.getPath().contains(sing))
                .min(Comparator.comparingInt((Identifier id) -> id.getPath().length())
                        .thenComparing(id -> id.getNamespace().equals("minecraft") ? 0 : 1));
    }

    static String singular(String s) {
        if (s.endsWith("ies")) return s.substring(0, s.length() - 3) + "y";
        if (s.endsWith("ches") || s.endsWith("shes")) return s.substring(0, s.length() - 2);
        if (s.endsWith("s") && !s.endsWith("ss") && s.length() > 3) return s.substring(0, s.length() - 1);
        return s;
    }
}
