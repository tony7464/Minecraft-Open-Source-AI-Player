package com.joserayos.aiplayers.perception;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceMapTest {
    @TempDir Path dir;

    @Test
    void nearbySightingsMergeAndFarOnesAdd() {
        ResourceMap m = new ResourceMap(null);
        m.record("iron_ore", "overworld", 0, 10, 0, 3, 1);
        m.record("iron_ore", "overworld", 5, 10, 5, 4, 2);
        assertEquals(1, m.spotCount());
        m.record("iron_ore", "overworld", 200, 10, 0, 1, 3);
        assertEquals(2, m.spotCount());
        assertEquals(200, m.nearest("overworld", 190, 10, 0, t -> t.equals("iron_ore")).orElseThrow().x());
        assertTrue(m.nearest("the_nether", 0, 0, 0, t -> true).isEmpty());
    }

    @Test
    void spotsPerTypeAreCapped() {
        ResourceMap m = new ResourceMap(null);
        for (int i = 0; i < 30; i++) m.record("oak_log", "o", i * 100, 64, 0, 1, i);
        assertEquals(ResourceMap.SPOTS_PER_TYPE, m.spotCount());
        assertTrue(m.nearest("o", 0, 64, 0, t -> true).orElseThrow().x() >= 100 * (30 - ResourceMap.SPOTS_PER_TYPE), "oldest dropped");
    }

    @Test
    void forgetRemovesStaleSpots() {
        ResourceMap m = new ResourceMap(null);
        m.record("oak_log", "o", 10, 64, 10, 5, 1);
        assertEquals(1, m.forget("o", 12, 64, 12, 8, t -> t.endsWith("_log")));
        assertFalse(m.knows("o", t -> true));
    }

    @Test
    void visitedCellsAndPersistence() throws Exception {
        Path f = dir.resolve("a.json");
        ResourceMap m = new ResourceMap(f);
        m.markVisited("o", 10, 10);
        m.markVisited("o", 20, 20); // same 64x64 cell
        m.markVisited("o", -10, 10);
        m.record("crafting_table", "o", 1, 64, 2, 1, 5);
        assertEquals(2, m.visitedCount());
        m.save();
        assertTrue(Files.exists(f));
        ResourceMap back = new ResourceMap(f);
        assertEquals(2, back.visitedCount());
        assertTrue(back.visited("o", 30, 30));
        assertFalse(back.visited("o", 100, 30));
        assertEquals(1, back.spotCount());
        assertTrue(back.summary("o", 0, 64, 0, 5).contains("crafting_table @ 1 64 2"));
    }
}
