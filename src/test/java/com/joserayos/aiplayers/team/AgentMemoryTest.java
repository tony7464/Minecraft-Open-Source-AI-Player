package com.joserayos.aiplayers.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentMemoryTest {
    @TempDir Path dir;

    @Test
    void notesSurviveReload() {
        Path f = dir.resolve("a.json");
        AgentMemory m = AgentMemory.load(f);
        assertEquals("base_location", m.remember("Base Location!", "100 64 -20"));
        m.addSummary("Jose: get logs");
        AgentMemory again = AgentMemory.load(f);
        assertEquals("100 64 -20", again.notes().get("base_location"));
        assertEquals(1, again.summary().size());
        assertTrue(again.promptText(5).contains("base_location: 100 64 -20"));
    }

    @Test
    void capsNotesAndRecalls() {
        AgentMemory m = AgentMemory.load(dir.resolve("b.json"));
        for (int i = 0; i < AgentMemory.MAX_NOTES + 5; i++) m.remember("k" + i, "value " + i);
        assertEquals(AgentMemory.MAX_NOTES, m.notes().size());
        assertTrue(m.notes().containsKey("k" + (AgentMemory.MAX_NOTES + 4)));
        assertEquals(1, m.recall("value 7").stream().filter(s -> s.startsWith("k7:")).count());
        assertTrue(m.forget("k10"));
    }
}
