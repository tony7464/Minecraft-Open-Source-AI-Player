package com.joserayos.aiplayers.agent.autonomy;

import java.util.Map;

/**
 * A goal with an optional code check. {@code item} is an item id ("stone_pickaxe"), "#food" for any safe food,
 * "#logs" for any log, "@shelter" for a remembered shelter, or empty for unverifiable goals.
 */
public record Goal(String text, String item, int count) {
    public static final Goal NONE = new Goal("", "", 0);

    public boolean isNone() { return text.isEmpty(); }

    public boolean verifiable() { return !item.isEmpty() && count > 0; }

    /** Progress against an inventory summary (item id -> count, plus "#food", "#logs", "@shelter"). */
    public int have(Map<String, Integer> inv) {
        return inv.getOrDefault(item, 0);
    }

    public boolean satisfied(Map<String, Integer> inv) {
        return verifiable() && have(inv) >= count;
    }

    public String describe(Map<String, Integer> inv) {
        if (isNone()) return "none";
        return text + (verifiable() ? " [check: " + Math.min(have(inv), count) + "/" + count + " " + item + "]" : "");
    }

    public String serialize() { return item + "|" + count + "|" + text; }

    public static Goal parse(String s) {
        if (s == null || s.isEmpty()) return NONE;
        String[] p = s.split("\\|", 3);
        if (p.length < 3) return new Goal(s, "", 0);
        try {
            return new Goal(p[2], p[0], Integer.parseInt(p[1]));
        } catch (NumberFormatException e) {
            return new Goal(p[2], "", 0);
        }
    }
}
