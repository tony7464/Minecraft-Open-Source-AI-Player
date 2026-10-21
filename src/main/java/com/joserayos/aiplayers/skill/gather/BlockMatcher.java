package com.joserayos.aiplayers.skill.gather;

import com.joserayos.aiplayers.skill.ItemResolver;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Turns a model-provided block name into a predicate, with a few friendly aliases. */
public record BlockMatcher(String label, Predicate<BlockState> test) {
    private static final Set<String> LOG_WORDS = Set.of("log", "logs", "wood", "tree", "trees", "any_log");

    public static Optional<BlockMatcher> parse(String raw) {
        String name = ItemResolver.normalize(raw).replace("minecraft:", "");
        if (LOG_WORDS.contains(name)) return Optional.of(new BlockMatcher("any log", s -> s.is(BlockTags.LOGS)));
        if (name.equals("stone") || name.equals("cobblestone") || name.equals("cobble")) {
            return Optional.of(new BlockMatcher("stone/cobblestone", s -> s.is(Blocks.STONE) || s.is(Blocks.COBBLESTONE)));
        }
        if (name.equals("dirt")) return Optional.of(new BlockMatcher("dirt", s -> s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK)));
        Optional<Block> block = ItemResolver.block(name);
        if (block.isEmpty()) {
            // "coal" -> coal_ore, "iron" -> iron_ore, "raw_iron" -> iron_ore
            block = ItemResolver.block(name.replace("raw_", "") + "_ore");
        }
        if (block.isEmpty()) return Optional.empty();
        Block b = block.get();
        String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (path.endsWith("_ore")) {
            String base = path.replace("deepslate_", "");
            return Optional.of(new BlockMatcher(base, s -> {
                String p = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
                return p.equals(base) || p.equals("deepslate_" + base);
            }));
        }
        return Optional.of(new BlockMatcher(path.toLowerCase(Locale.ROOT), s -> s.is(b)));
    }
}
