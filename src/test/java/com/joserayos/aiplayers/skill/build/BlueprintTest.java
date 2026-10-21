package com.joserayos.aiplayers.skill.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BlueprintTest {
    static Blueprint load(String name) throws Exception {
        return Blueprint.fromJson(JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/aiplayers/blueprints/" + name + ".json"))).getAsJsonObject());
    }

    @Test
    void hutHasExpectedMaterials() throws Exception {
        Blueprint hut = load("hut");
        assertEquals(46, hut.cells().stream().filter(c -> c.spec().equals("cobblestone")).count());
        assertEquals(25, hut.cells().stream().filter(c -> c.spec().equals("#minecraft:planks")).count());
        assertEquals(1, hut.cells().stream().filter(c -> c.spec().equals("#minecraft:wooden_doors")).count());
    }

    @Test
    void shapes() {
        assertEquals(12, Blueprint.shape("wall", "stone", 4, 3, 1).cells().size());
        assertEquals(5, Blueprint.shape("pillar", "stone", 1, 5, 1).cells().size());
        assertThrows(IllegalArgumentException.class, () -> Blueprint.shape("dome", "stone", 3, 3, 3));
    }
}
