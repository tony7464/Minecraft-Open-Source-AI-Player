package com.joserayos.aiplayers.perception;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Per-agent cache of "interesting" blocks nearby (logs, ores, stations, containers, water, lava).
 * Refreshed incrementally: a fixed budget of blocks per tick, so it never causes lag.
 */
public final class BlockInterestCache {
    public static final int DEFAULT_RADIUS = 32;
    public static final int VERTICAL = 12;
    public static final int BUDGET_PER_TICK = 2048;

    public record Entry(String type, int count, BlockPos nearest, double nearestDistance, boolean structure) {}

    private BlockPos origin = BlockPos.ZERO;
    private int index;
    private final Map<String, Entry> building = new HashMap<>();
    private volatile List<Entry> latest = List.of();
    private int radius = DEFAULT_RADIUS;
    private int cycles;

    /** Completed full scans so far (lets listeners react once per scan). */
    public int cycles() { return cycles; }

    public BlockPos origin() { return origin; }

    public List<Entry> latest() { return latest; }

    public void tick(ServerLevel level, BlockPos center) {
        if (index == 0) {
            origin = center.immutable();
            building.clear();
            radius = com.joserayos.aiplayers.config.AiPlayersConfig.get(com.joserayos.aiplayers.config.AiPlayersConfig.SCAN_RADIUS);
        }
        int side = radius * 2 + 1;
        int height = VERTICAL * 2 + 1;
        int total = side * side * height;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int end = Math.min(total, index + BUDGET_PER_TICK);
        for (; index < end; index++) {
            int dy = index % height - VERTICAL;
            int rest = index / height;
            int dx = rest % side - radius;
            int dz = rest / side - radius;
            p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
            if (!level.hasChunkAt(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            String type = classify(s);
            if (type == null) continue;
            double d = Math.sqrt(p.distSqr(origin));
            boolean structure = isStructure(s);
            building.merge(type, new Entry(type, 1, p.immutable(), d, structure), (a, b) ->
                    new Entry(type, a.count() + 1, b.nearestDistance() < a.nearestDistance() ? b.nearest() : a.nearest(),
                            Math.min(a.nearestDistance(), b.nearestDistance()), structure));
        }
        if (index >= total) {
            List<Entry> out = new ArrayList<>(building.values());
            out.sort(Comparator.comparingDouble(Entry::nearestDistance));
            latest = List.copyOf(out);
            index = 0;
            cycles++;
        }
    }

    static String classify(BlockState s) {
        if (s.is(BlockTags.LOGS)) return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        if (path.endsWith("_ore")) return path.replace("deepslate_", "");
        if (isStructure(s)) return path;
        if (s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource()) return "water";
        if (s.getFluidState().is(Fluids.LAVA)) return "lava";
        if (s.is(Blocks.STONE)) return "stone";
        if (s.is(BlockTags.CROPS)) return "crops";
        return null;
    }

    static boolean isStructure(BlockState s) {
        return s.is(Blocks.CRAFTING_TABLE) || s.is(Blocks.FURNACE) || s.is(Blocks.CHEST) || s.is(Blocks.BARREL)
                || s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.SMOKER) || s.is(BlockTags.BEDS);
    }
}
