package com.joserayos.aiplayers.agent.autonomy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CurriculumTest {
    static Map<String, Integer> inv(Object... kv) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    @Test
    void progressesThroughTheTechTree() {
        assertEquals("#logs", Curriculum.next(inv()).item());
        assertEquals("crafting_table", Curriculum.next(inv("#logs", 4)).item());
        assertEquals("wooden_pickaxe", Curriculum.next(inv("#logs", 4, "crafting_table", 1)).item());
        assertEquals("cobblestone", Curriculum.next(inv("wooden_pickaxe", 1)).item());
        assertEquals("stone_pickaxe", Curriculum.next(inv("wooden_pickaxe", 1, "cobblestone", 3)).item());
        assertEquals("stone_sword", Curriculum.next(inv("stone_pickaxe", 1, "cobblestone", 5)).item());
        assertEquals("#food", Curriculum.next(inv("stone_pickaxe", 1, "stone_sword", 1)).item());
        assertEquals("@shelter", Curriculum.next(inv("stone_pickaxe", 1, "stone_sword", 1, "#food", 8,
                "cobblestone", 50, "#planks", 30, "#doors", 1)).item());
        assertEquals("raw_iron", Curriculum.next(inv("stone_pickaxe", 1, "stone_sword", 1, "#food", 8, "@shelter", 1)).item());
    }

    @Test
    void goalRoundTripAndCheck() {
        Goal g = new Goal("get logs | lots", "#logs", 4);
        Goal back = Goal.parse(g.serialize());
        assertEquals(g, back);
        assertFalse(back.satisfied(inv("#logs", 3)));
        assertTrue(back.satisfied(inv("#logs", 4)));
        assertTrue(Goal.parse("").isNone());
        assertFalse(new Goal("explore", "", 0).verifiable());
    }

    @Test
    void everyEarlyGoalHasAFallbackAction() {
        assertEquals("collect_block", Curriculum.fallbackCall(Curriculum.next(inv()))[0]);
        assertEquals("craft_item", Curriculum.fallbackCall(Curriculum.next(inv("#logs", 4)))[0]);
        assertEquals("craft_item", Curriculum.fallbackCall(Curriculum.next(inv("wooden_pickaxe", 1, "cobblestone", 3)))[0]);
        assertEquals(null, Curriculum.fallbackCall(Curriculum.next(inv("stone_pickaxe", 1, "stone_sword", 1))));
    }
}
