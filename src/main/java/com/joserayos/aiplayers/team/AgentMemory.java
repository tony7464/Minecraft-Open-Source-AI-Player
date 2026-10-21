package com.joserayos.aiplayers.team;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.joserayos.aiplayers.AiPlayersMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Long-term memory for one agent: named notes plus a short rolling summary of older conversation.
 * Stored as JSON in {@code <world>/aiplayers/memory/<agent uuid>.json} so it survives restarts.
 */
public final class AgentMemory {
    public static final int MAX_NOTES = 40;
    public static final int MAX_NOTE_CHARS = 300;
    public static final int MAX_SUMMARY_LINES = 8;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<UUID, AgentMemory> CACHE = new ConcurrentHashMap<>();

    /** JSON shape. */
    static final class Data {
        Map<String, String> notes = new LinkedHashMap<>();
        List<String> summary = new ArrayList<>();
    }

    private final Path file;
    private final Data data;

    private AgentMemory(Path file, Data data) {
        this.file = file;
        this.data = data;
    }

    public static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("aiplayers").resolve("memory");
    }

    public static AgentMemory of(MinecraftServer server, UUID agent) {
        return CACHE.computeIfAbsent(agent, id -> load(dir(server).resolve(id + ".json")));
    }

    static AgentMemory load(Path file) {
        Data d = null;
        if (Files.exists(file)) {
            try {
                d = GSON.fromJson(Files.readString(file), Data.class);
            } catch (IOException | RuntimeException e) {
                AiPlayersMod.LOGGER.warn("Could not read agent memory {}: {}", file, e.toString());
            }
        }
        if (d == null) d = new Data();
        if (d.notes == null) d.notes = new LinkedHashMap<>();
        if (d.summary == null) d.summary = new ArrayList<>();
        return new AgentMemory(file, d);
    }

    /** Forget cached instances (server stop). Files stay. */
    public static void unloadAll() { CACHE.clear(); }

    static String key(String k) {
        return k.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 _-]", "").replace(' ', '_');
    }

    public synchronized String remember(String key, String text) {
        String k = key(key);
        if (k.isEmpty()) throw new IllegalArgumentException("empty key");
        String t = text.strip();
        if (t.length() > MAX_NOTE_CHARS) t = t.substring(0, MAX_NOTE_CHARS);
        data.notes.remove(k); // re-insert so it becomes the most recent
        data.notes.put(k, t);
        while (data.notes.size() > MAX_NOTES) data.notes.remove(data.notes.keySet().iterator().next());
        save();
        return k;
    }

    public synchronized boolean forget(String key) {
        boolean removed = data.notes.remove(key(key)) != null;
        if (removed) save();
        return removed;
    }

    public synchronized Map<String, String> notes() { return new LinkedHashMap<>(data.notes); }

    /** Notes whose key or text contains any query word. */
    public synchronized List<String> recall(String query) {
        String[] words = query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> out = new ArrayList<>();
        for (var e : data.notes.entrySet()) {
            String hay = (e.getKey() + " " + e.getValue()).toLowerCase(Locale.ROOT);
            for (String w : words) {
                if (w.length() >= 2 && hay.contains(w)) {
                    out.add(e.getKey() + ": " + e.getValue());
                    break;
                }
            }
        }
        return out;
    }

    public synchronized void addSummary(String line) {
        String l = line.strip();
        if (l.isEmpty()) return;
        if (l.length() > 200) l = l.substring(0, 200) + "...";
        data.summary.add(l);
        while (data.summary.size() > MAX_SUMMARY_LINES) data.summary.removeFirst();
        save();
    }

    public synchronized List<String> summary() { return List.copyOf(data.summary); }

    public synchronized void clear() {
        data.notes.clear();
        data.summary.clear();
        save();
    }

    /** Prompt section; most recent notes first, capped. */
    public synchronized String promptText(int maxNotes) {
        if (data.notes.isEmpty() && data.summary.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        if (!data.notes.isEmpty()) {
            List<Map.Entry<String, String>> list = new ArrayList<>(data.notes.entrySet());
            sb.append("[MEMORY] ");
            int n = 0;
            for (int i = list.size() - 1; i >= 0 && n < maxNotes; i--, n++) {
                if (n > 0) sb.append("; ");
                sb.append(list.get(i).getKey()).append(": ").append(list.get(i).getValue());
            }
            sb.append('\n');
        }
        if (!data.summary.isEmpty()) sb.append("[EARLIER] ").append(String.join(" | ", data.summary)).append('\n');
        return sb.toString();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(data));
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            AiPlayersMod.LOGGER.warn("Could not save agent memory {}: {}", file, e.toString());
        }
    }
}
