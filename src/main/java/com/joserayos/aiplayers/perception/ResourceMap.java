package com.joserayos.aiplayers.perception;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.joserayos.aiplayers.AiPlayersMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Per-agent remembered map: where it has seen resources and landmarks (fed by the background block scan),
 * plus which 64x64 areas it has already visited (so "explore auto" heads somewhere new).
 * Stored as JSON in {@code <world>/aiplayers/maps/<agent uuid>.json}. Bounded in size; pure Java so it can be unit tested.
 */
public final class ResourceMap {
    public static final int SPOTS_PER_TYPE = 8;
    public static final int MAX_TYPES = 96;
    public static final int MIN_SPOT_SEPARATION = 24;
    public static final int MAX_VISITED_CELLS = 8192;
    public static final int CELL = 64;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public record Spot(String type, String dim, int x, int y, int z, int count, long seen) {
        public double dist(double px, double py, double pz) {
            double dx = x - px, dy = y - py, dz = z - pz;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    static final class Data {
        Map<String, List<Spot>> spots = new LinkedHashMap<>();
        List<String> visited = new ArrayList<>();
    }

    private final @Nullable Path file;
    private final Data data;
    private final Set<String> visited = new HashSet<>();
    private boolean dirty;

    public ResourceMap(@Nullable Path file) {
        this.file = file;
        Data d = null;
        if (file != null && Files.exists(file)) {
            try {
                d = GSON.fromJson(Files.readString(file), Data.class);
            } catch (IOException | RuntimeException e) {
                AiPlayersMod.LOGGER.warn("Could not read agent map {}: {}", file, e.toString());
            }
        }
        if (d == null) d = new Data();
        if (d.spots == null) d.spots = new LinkedHashMap<>();
        if (d.visited == null) d.visited = new ArrayList<>();
        this.data = d;
        visited.addAll(d.visited);
    }

    static String cellKey(String dim, int x, int z) {
        return dim + "|" + Math.floorDiv(x, CELL) + "|" + Math.floorDiv(z, CELL);
    }

    /** Remember a sighting. Nearby sightings of the same type update one spot instead of adding a new one. */
    public synchronized void record(String type, String dim, int x, int y, int z, int count, long now) {
        List<Spot> list = data.spots.get(type);
        if (list == null) {
            if (data.spots.size() >= MAX_TYPES) return;
            list = new ArrayList<>();
            data.spots.put(type, list);
        }
        for (int i = 0; i < list.size(); i++) {
            Spot s = list.get(i);
            if (s.dim().equals(dim) && s.dist(x, y, z) < MIN_SPOT_SEPARATION) {
                Spot n = new Spot(type, dim, x, y, z, Math.max(count, 1), now);
                if (!n.equals(s)) { list.set(i, n); dirty = true; }
                return;
            }
        }
        list.add(new Spot(type, dim, x, y, z, Math.max(count, 1), now));
        if (list.size() > SPOTS_PER_TYPE) list.remove(list.stream().min(Comparator.comparingLong(Spot::seen)).orElseThrow());
        dirty = true;
    }

    /** Forget spots of a type near a position (went there, it was gone). */
    public synchronized int forget(String dim, int x, int y, int z, double radius, Predicate<String> type) {
        int removed = 0;
        for (var e : data.spots.entrySet()) {
            if (!type.test(e.getKey())) continue;
            int before = e.getValue().size();
            e.getValue().removeIf(s -> s.dim().equals(dim) && s.dist(x, y, z) <= radius);
            removed += before - e.getValue().size();
        }
        data.spots.values().removeIf(List::isEmpty);
        if (removed > 0) dirty = true;
        return removed;
    }

    public synchronized Optional<Spot> nearest(String dim, double x, double y, double z, Predicate<String> type) {
        Spot best = null;
        double bd = Double.MAX_VALUE;
        for (var e : data.spots.entrySet()) {
            if (!type.test(e.getKey())) continue;
            for (Spot s : e.getValue()) {
                if (!s.dim().equals(dim)) continue;
                double d = s.dist(x, y, z);
                if (d < bd) { bd = d; best = s; }
            }
        }
        return Optional.ofNullable(best);
    }

    public synchronized boolean knows(String dim, Predicate<String> type) {
        return nearest(dim, 0, 0, 0, type).isPresent();
    }

    public synchronized void markVisited(String dim, int x, int z) {
        String k = cellKey(dim, x, z);
        if (visited.add(k)) {
            data.visited.add(k);
            while (data.visited.size() > MAX_VISITED_CELLS) visited.remove(data.visited.removeFirst());
            dirty = true;
        }
    }

    public synchronized boolean visited(String dim, int x, int z) { return visited.contains(cellKey(dim, x, z)); }

    public synchronized int visitedCount() { return visited.size(); }

    public synchronized int spotCount() { return data.spots.values().stream().mapToInt(List::size).sum(); }

    /** Short text for the prompt / status: nearest known spot per type, closest first. */
    public synchronized String summary(String dim, double x, double y, double z, int maxTypes) {
        List<Spot> nearest = new ArrayList<>();
        for (var e : data.spots.entrySet()) {
            e.getValue().stream().filter(s -> s.dim().equals(dim)).min(Comparator.comparingDouble(s -> s.dist(x, y, z))).ifPresent(nearest::add);
        }
        nearest.sort(Comparator.comparingDouble(s -> s.dist(x, y, z)));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(maxTypes, nearest.size()); i++) {
            Spot s = nearest.get(i);
            if (i > 0) sb.append("; ");
            sb.append(s.type()).append(" @ ").append(s.x()).append(' ').append(s.y()).append(' ').append(s.z())
                    .append(" (").append(Math.round(s.dist(x, y, z))).append("m)");
        }
        return sb.toString();
    }

    public synchronized boolean isDirty() { return dirty; }

    public synchronized void save() {
        if (!dirty || file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(data));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (IOException e) {
            AiPlayersMod.LOGGER.warn("Could not save agent map {}: {}", file, e.toString());
        }
    }
}
