package com.joserayos.aiplayers.command;

import java.util.ArrayList;
import java.util.List;

/** Parses leading {@code @name} mentions: "@Ava @Bo meet at the chest" -> [Ava, Bo] + "meet at the chest". */
public final class MentionParser {
    public record Parsed(List<String> names, String message) {
        public boolean isEmpty() { return names.isEmpty(); }
        public boolean mentionsAll() { return names.stream().anyMatch(n -> n.equalsIgnoreCase("all")); }
    }

    private MentionParser() {}

    public static Parsed parse(String raw) {
        List<String> names = new ArrayList<>();
        String rest = raw == null ? "" : raw.strip();
        while (rest.startsWith("@")) {
            int end = 1;
            while (end < rest.length() && isNameChar(rest.charAt(end))) end++;
            if (end == 1) break;
            String name = rest.substring(1, end);
            if (!names.stream().anyMatch(n -> n.equalsIgnoreCase(name))) names.add(name);
            rest = rest.substring(end);
            // allow "@Ava, @Bo:" separators
            rest = rest.replaceFirst("^[\\s,:]+", "");
        }
        return new Parsed(List.copyOf(names), rest.strip());
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
