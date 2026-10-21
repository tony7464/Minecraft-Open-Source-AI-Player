package com.joserayos.aiplayers.agent.autonomy;

import java.util.Map;

/**
 * Mini Voyager-style curriculum: the next sensible goal for a survival agent given what it has.
 * Pure function over an inventory summary so it is easy to unit test.
 * Order: wood -> crafting table -> wooden pickaxe -> stone pickaxe -> sword -> food -> shelter -> iron.
 */
public final class Curriculum {
    private Curriculum() {}

    static int n(Map<String, Integer> inv, String k) { return inv.getOrDefault(k, 0); }

    static boolean hasAny(Map<String, Integer> inv, String... ids) {
        for (String id : ids) if (n(inv, id) > 0) return true;
        return false;
    }

    /** A direct tool call that works toward a curriculum goal without the model (tool name + JSON args), or null. */
    public static String[] fallbackCall(Goal g) {
        String item = g.item();
        int n = Math.max(1, g.count());
        return switch (item) {
            case "#logs" -> new String[]{"collect_block", "{\"block\":\"log\",\"count\":" + n + "}"};
            case "#planks" -> new String[]{"collect_block", "{\"block\":\"log\",\"count\":" + ((n + 3) / 4) + "}"};
            case "cobblestone" -> new String[]{"collect_block", "{\"block\":\"stone\",\"count\":" + Math.min(n, 64) + "}"};
            case "raw_iron" -> new String[]{"collect_block", "{\"block\":\"iron_ore\",\"count\":" + n + "}"};
            case "iron_ingot" -> new String[]{"smelt", "{\"item\":\"iron_ingot\",\"count\":" + n + "}"};
            case "#doors" -> new String[]{"craft_item", "{\"item\":\"oak_door\"}"};
            case "@shelter" -> new String[]{"build_blueprint", "{\"name\":\"hut\"}"};
            case "crafting_table", "wooden_pickaxe", "stone_pickaxe", "stone_sword" -> new String[]{"craft_item", "{\"item\":\"" + item + "\"}"};
            default -> null; // food and free-choice goals need judgment
        };
    }

    /** Block a goal needs to find in the world (for collect goals), or null. */
    public static String neededBlock(Goal g) {
        String[] call = fallbackCall(g);
        if (call == null || !call[0].equals("collect_block")) return null;
        var m = java.util.regex.Pattern.compile("\"block\":\"([a-z_]+)\"").matcher(call[1]);
        return m.find() ? m.group(1) : null;
    }

    public static Goal next(Map<String, Integer> inv) {
        boolean stonePick = hasAny(inv, "stone_pickaxe", "iron_pickaxe", "diamond_pickaxe", "netherite_pickaxe");
        boolean anyPick = stonePick || hasAny(inv, "wooden_pickaxe");
        if (!anyPick && n(inv, "#logs") < 3 && n(inv, "#planks") < 8) {
            return new Goal("collect 4 logs from a nearby tree (collect_block log)", "#logs", 4);
        }
        if (!anyPick && n(inv, "crafting_table") == 0 && !hasAny(inv, "@table_nearby")) {
            return new Goal("craft a crafting table (craft_item crafting_table)", "crafting_table", 1);
        }
        if (!anyPick) {
            return new Goal("craft a wooden pickaxe (craft_item wooden_pickaxe)", "wooden_pickaxe", 1);
        }
        if (!stonePick) {
            if (n(inv, "cobblestone") < 3) return new Goal("mine 3 cobblestone with your pickaxe (collect_block stone)", "cobblestone", 3);
            return new Goal("craft a stone pickaxe (craft_item stone_pickaxe)", "stone_pickaxe", 1);
        }
        if (!hasAny(inv, "stone_sword", "iron_sword", "diamond_sword", "netherite_sword")) {
            if (n(inv, "cobblestone") < 2) return new Goal("mine 2 cobblestone (collect_block stone)", "cobblestone", 2);
            return new Goal("craft a stone sword (craft_item stone_sword)", "stone_sword", 1);
        }
        if (n(inv, "#food") < 5) {
            return new Goal("get at least 5 food: hunt cows/pigs/chickens (attack) and pick up drops, cook raw meat with smelt", "#food", 5);
        }
        if (n(inv, "@shelter") == 0) {
            if (n(inv, "cobblestone") < 46) return new Goal("mine 46 cobblestone for a hut (collect_block stone)", "cobblestone", 46);
            if (n(inv, "#planks") < 25) return new Goal("get 25 planks for the hut roof (collect logs, craft planks)", "#planks", 25);
            if (n(inv, "#doors") == 0) return new Goal("craft a wooden door (craft_item oak_door)", "#doors", 1);
            return new Goal("build a hut near here (build_blueprint hut) and set_location base", "@shelter", 1);
        }
        if (n(inv, "iron_ingot") < 3) {
            if (n(inv, "raw_iron") >= 3) return new Goal("smelt 3 raw iron (smelt iron_ingot)", "iron_ingot", 3);
            return new Goal("find and mine 3 iron ore (collect_block iron_ore), then smelt it", "raw_iron", 3);
        }
        return new Goal("free choice: improve the base, store spare items in a chest, gather resources the team needs", "", 0);
    }
}
