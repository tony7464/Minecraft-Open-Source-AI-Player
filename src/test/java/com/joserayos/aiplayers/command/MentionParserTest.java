package com.joserayos.aiplayers.command;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MentionParserTest {
    @Test
    void singleMention() {
        var p = MentionParser.parse("@Ava follow me");
        assertEquals(List.of("Ava"), p.names());
        assertEquals("follow me", p.message());
    }

    @Test
    void multipleMentionsWithSeparators() {
        var p = MentionParser.parse("@Ava, @Bo: meet at the chest");
        assertEquals(List.of("Ava", "Bo"), p.names());
        assertEquals("meet at the chest", p.message());
    }

    @Test
    void all() {
        var p = MentionParser.parse("@all stop");
        assertTrue(p.mentionsAll());
        assertEquals("stop", p.message());
    }

    @Test
    void noMention() {
        assertTrue(MentionParser.parse("hello @Ava").isEmpty());
        assertTrue(MentionParser.parse("@ hi").isEmpty());
    }

    @Test
    void duplicatesRemoved() {
        assertEquals(List.of("Ava"), MentionParser.parse("@Ava @ava hi").names());
    }
}
