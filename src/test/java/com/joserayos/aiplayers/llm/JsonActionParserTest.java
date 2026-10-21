package com.joserayos.aiplayers.llm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JsonActionParserTest {
    @Test
    void standardShape() {
        var p = JsonActionParser.parse("{\"say\":\"On it\",\"actions\":[{\"tool\":\"go_to\",\"args\":{\"x\":1,\"y\":2,\"z\":3}}]}").orElseThrow();
        assertEquals("On it", p.say());
        assertEquals("go_to", p.calls().getFirst().name());
        assertEquals(3, p.calls().getFirst().arguments().get("z").getAsInt());
    }

    @Test
    void codeFenceProseAndThinking() {
        var p = JsonActionParser.parse("<think>plan</think>Sure!\n```json\n{\"say\":\"ok\",\"actions\":[{\"name\":\"stay\",\"arguments\":\"{}\"}]}\n```").orElseThrow();
        assertEquals("ok", p.say());
        assertEquals("stay", p.calls().getFirst().name());
    }

    @Test
    void inlineArgsAndSingleAction() {
        var p = JsonActionParser.parse("{\"tool\":\"give_item\",\"item\":\"bread\",\"count\":2}").orElseThrow();
        assertEquals("give_item", p.calls().getFirst().name());
        assertEquals("bread", p.calls().getFirst().arguments().get("item").getAsString());
        assertFalse(p.calls().getFirst().arguments().has("tool"));
    }

    @Test
    void invalid() {
        assertTrue(JsonActionParser.parse("no json here").isEmpty());
        assertTrue(JsonActionParser.parse("{broken").isEmpty());
        assertTrue(JsonActionParser.looksLikeJson("{\"say\":"));
        assertFalse(JsonActionParser.looksLikeJson("hello"));
    }

    @Test
    void emptyActions() {
        var p = JsonActionParser.parse("{\"say\":\"hi\",\"actions\":[]}").orElseThrow();
        assertTrue(p.calls().isEmpty());
    }
}
