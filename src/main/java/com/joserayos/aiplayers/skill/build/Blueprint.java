package com.joserayos.aiplayers.skill.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A structure to build, relative to an origin (min corner). Each cell has a material spec:
 * a block id ("cobblestone"), an item tag ("#minecraft:planks"), "any_building", or "air" (clear).
 * Cells not listed are left untouched.
 *
 * <p>JSON format (layers bottom to top, each layer is rows along +z, characters along +x):
 * <pre>{"name":"hut","description":"...","palette":{"#":"cobblestone","P":"#minecraft:planks","D":"#minecraft:wooden_doors","_":"air"},
 *  "layers":[["#####","#___#", ...], ...]}</pre>
 * A space or '.' means "keep whatever is there".
 */
public record Blueprint(String name, String description, List<Cell> cells) {
    public record Cell(int x, int y, int z, String spec) {}

    public static Blueprint fromJson(JsonObject o) {
        String name = o.get("name").getAsString();
        String desc = o.has("description") ? o.get("description").getAsString() : "";
        Map<Character, String> palette = new HashMap<>();
        for (var e : o.getAsJsonObject("palette").entrySet()) {
            if (e.getKey().length() != 1) throw new IllegalArgumentException("palette keys must be single characters");
            palette.put(e.getKey().charAt(0), e.getValue().getAsString());
        }
        List<Cell> cells = new ArrayList<>();
        JsonArray layers = o.getAsJsonArray("layers");
        for (int y = 0; y < layers.size(); y++) {
            JsonArray rows = layers.get(y).getAsJsonArray();
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z).getAsString();
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    if (c == ' ' || c == '.') continue;
                    String spec = palette.get(c);
                    if (spec == null) throw new IllegalArgumentException("blueprint " + name + ": '" + c + "' is not in the palette");
                    cells.add(new Cell(x, y, z, spec));
                }
            }
        }
        if (cells.size() > 4096) throw new IllegalArgumentException("blueprint " + name + " is too big (max 4096 cells)");
        return new Blueprint(name, desc, List.copyOf(cells));
    }

    /** Simple parametric shapes (all solid unless hollow). */
    public static Blueprint shape(String shape, String material, int width, int height, int length) {
        List<Cell> cells = new ArrayList<>();
        switch (shape) {
            case "wall" -> {
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) cells.add(new Cell(x, y, 0, material));
            }
            case "floor" -> {
                for (int z = 0; z < length; z++) for (int x = 0; x < width; x++) cells.add(new Cell(x, 0, z, material));
            }
            case "pillar" -> {
                for (int y = 0; y < height; y++) cells.add(new Cell(0, y, 0, material));
            }
            case "box" -> {
                // Hollow box: walls + roof, interior cleared, 2-high doorway in the middle of the z=0 wall.
                int doorX = width / 2;
                for (int y = 0; y <= height; y++) {
                    for (int z = 0; z < length; z++) {
                        for (int x = 0; x < width; x++) {
                            boolean edge = x == 0 || z == 0 || x == width - 1 || z == length - 1;
                            boolean roof = y == height;
                            boolean door = z == 0 && x == doorX && y < 2;
                            if (roof || edge && !door) cells.add(new Cell(x, y, z, material));
                            else cells.add(new Cell(x, y, z, "air"));
                        }
                    }
                }
            }
            default -> throw new IllegalArgumentException("unknown shape '" + shape + "' (use wall, floor, pillar or box)");
        }
        return new Blueprint(shape, shape + " of " + material, cells);
    }
}
