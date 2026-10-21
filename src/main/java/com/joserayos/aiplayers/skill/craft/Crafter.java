package com.joserayos.aiplayers.skill.craft;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.skill.ItemResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.jspecify.annotations.Nullable;

/**
 * Crafting planner. Works on a simulated inventory (item -> count) so a whole chain
 * (logs -> planks -> sticks -> pickaxe) is checked before anything is consumed.
 */
public final class Crafter {
    public static final int MAX_DEPTH = 4;

    /** A crafting recipe flattened to what the planner needs. */
    public record Simple(RecipeHolder<?> holder, Item output, int outputCount, List<Ingredient> ingredients, boolean needsTable) {}

    /** One crafting operation: run `recipe` `times` times, feeding it these items. */
    public record Step(Simple recipe, int times, Map<Item, Integer> inputs) {
        public String describe() {
            return "craft " + recipe.outputCount() * times + " " + ItemResolver.id(recipe.output());
        }
    }

    public record Plan(List<Step> steps, boolean needsTable, String missing) {
        public boolean ok() { return missing.isEmpty(); }
    }

    private final List<Simple> recipes;
    private String firstMissing = "";

    public Crafter(ServerLevel level) {
        this.recipes = load(level);
    }

    static List<Simple> load(ServerLevel level) {
        List<Simple> out = new ArrayList<>();
        for (RecipeHolder<?> h : level.recipeAccess().getRecipes()) {
            Recipe<?> r = h.value();
            if (r.getType() != RecipeType.CRAFTING) continue;
            ItemStack result;
            List<Ingredient> ingredients;
            boolean table;
            if (r instanceof ShapedRecipe s) {
                result = s.assemble(CraftingInput.EMPTY);
                ingredients = s.getIngredients().stream().flatMap(java.util.Optional::stream).toList();
                table = s.getWidth() > 2 || s.getHeight() > 2;
            } else if (r instanceof ShapelessRecipe s) {
                result = s.assemble(CraftingInput.EMPTY);
                ingredients = s.placementInfo().ingredients();
                table = ingredients.size() > 4;
            } else {
                continue; // special recipes (dyeing, repair, fireworks...) are not supported
            }
            if (result.isEmpty() || ingredients.isEmpty()) continue;
            out.add(new Simple(h, result.getItem(), result.getCount(), ingredients, table));
        }
        // Fewer ingredients first: tends to choose the "normal" recipe over exotic ones.
        out.sort(Comparator.comparingInt(s -> s.ingredients().size()));
        return out;
    }

    public List<Simple> recipesFor(Item item) {
        return recipes.stream().filter(s -> s.output() == item).toList();
    }

    public static Map<Item, Integer> snapshot(AgentEntity agent) {
        Map<Item, Integer> inv = new HashMap<>();
        var c = agent.getInventory();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty()) inv.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return inv;
    }

    /** Plans crafting `count` of `target`; `tableAvailable` = a table is nearby or already owned. */
    public Plan plan(Map<Item, Integer> inventory, Item target, int count, boolean tableAvailable) {
        Map<Item, Integer> inv = new HashMap<>(inventory);
        List<Step> steps = new ArrayList<>();
        firstMissing = "";
        int have = inv.getOrDefault(target, 0);
        if (!ensure(inv, target, count + have, 0, steps)) {
            return new Plan(List.of(), false, firstMissing.isEmpty() ? "no recipe for " + ItemResolver.id(target) : firstMissing);
        }
        boolean needsTable = steps.stream().anyMatch(s -> s.recipe().needsTable());
        if (needsTable && !tableAvailable) {
            // Make a crafting table first (planned against the original inventory, then the rest again).
            Map<Item, Integer> inv2 = new HashMap<>(inventory);
            List<Step> steps2 = new ArrayList<>();
            firstMissing = "";
            if (!ensure(inv2, Items.CRAFTING_TABLE, 1, 0, steps2)) {
                return new Plan(List.of(), true, "need a crafting table: " + firstMissing);
            }
            inv2.merge(Items.CRAFTING_TABLE, -1, Integer::sum); // it gets placed
            if (!ensure(inv2, target, count + have, 0, steps2)) {
                return new Plan(List.of(), true, firstMissing);
            }
            return new Plan(steps2, true, "");
        }
        return new Plan(steps, needsTable, "");
    }

    /** Ensure the simulated inventory holds at least `count` of `item`, appending steps. */
    private boolean ensure(Map<Item, Integer> inv, Item item, int count, int depth, List<Step> steps) {
        int have = inv.getOrDefault(item, 0);
        if (have >= count) return true;
        if (depth >= MAX_DEPTH) {
            miss(item, count - have);
            return false;
        }
        int need = count - have;
        List<Simple> options = recipesFor(item);
        if (options.isEmpty()) {
            miss(item, need);
            return false;
        }
        for (Simple r : options) {
            Map<Item, Integer> trial = new HashMap<>(inv);
            List<Step> trialSteps = new ArrayList<>();
            if (tryRecipe(trial, r, need, depth, trialSteps)) {
                inv.clear();
                inv.putAll(trial);
                steps.addAll(trialSteps);
                return true;
            }
        }
        return false;
    }

    private boolean tryRecipe(Map<Item, Integer> inv, Simple r, int need, int depth, List<Step> steps) {
        int times = (need + r.outputCount() - 1) / r.outputCount();
        // Group identical ingredients ("3 planks") so one item choice covers all of them.
        Map<Ingredient, Integer> groups = new LinkedHashMap<>();
        for (Ingredient ing : r.ingredients()) groups.merge(ing, 1, Integer::sum);
        Map<Item, Integer> inputs = new LinkedHashMap<>();
        for (var e : groups.entrySet()) {
            int required = e.getValue() * times;
            List<Item> candidates = e.getKey().items().map(Holder::value).filter(i -> i != r.output()).toList();
            if (candidates.isEmpty()) return false;
            Item chosen = null;
            for (Item c : candidates) {
                if (inv.getOrDefault(c, 0) >= required) { chosen = c; break; }
            }
            if (chosen == null) {
                // Prefer candidates we have some of, or can craft from what we have.
                List<Item> ordered = new ArrayList<>(candidates);
                ordered.sort(Comparator.comparingInt((Item c) -> -inv.getOrDefault(c, 0)));
                for (Item c : ordered.subList(0, Math.min(ordered.size(), 12))) {
                    Map<Item, Integer> trial = new HashMap<>(inv);
                    List<Step> sub = new ArrayList<>();
                    if (ensure(trial, c, required, depth + 1, sub)) {
                        inv.clear();
                        inv.putAll(trial);
                        steps.addAll(sub);
                        chosen = c;
                        break;
                    }
                }
                if (chosen == null) {
                    if (depth == 0 || firstMissing.isEmpty()) miss(candidates.getFirst(), required - inv.getOrDefault(candidates.getFirst(), 0), candidates.size() > 1);
                    return false;
                }
            }
            inv.merge(chosen, -required, Integer::sum);
            inputs.merge(chosen, required, Integer::sum);
        }
        inv.merge(r.output(), r.outputCount() * times, Integer::sum);
        steps.add(new Step(r, times, inputs));
        return true;
    }

    private void miss(Item item, int n) { miss(item, n, false); }

    private void miss(Item item, int n, boolean anyVariant) {
        String name = ItemResolver.id(item);
        if (anyVariant) name = "any " + name.replaceFirst("^(oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry|pale_oak|bamboo|crimson|warped)_", "");
        firstMissing = "missing " + n + " " + name;
    }

    /** Executes a step against the real inventory. Returns null on success, or an error. */
    public static @Nullable String execute(AgentEntity agent, Step step) {
        for (var e : step.inputs().entrySet()) {
            if (agent.countItem(e.getKey()) < e.getValue()) return "ran out of " + ItemResolver.id(e.getKey());
        }
        for (var e : step.inputs().entrySet()) agent.removeItems(e.getKey(), e.getValue());
        int total = step.recipe().outputCount() * step.times();
        ItemStack out = new ItemStack(step.recipe().output(), total);
        while (!out.isEmpty()) {
            ItemStack part = out.split(out.getMaxStackSize());
            ItemStack rest = agent.addItem(part);
            if (!rest.isEmpty()) agent.spawnAtLocation((ServerLevel) agent.level(), rest);
        }
        return null;
    }

    /** Items the agent could craft right now (directly, one level deep), for list_craftable. */
    public List<String> craftableNow(AgentEntity agent, boolean tableAvailable) {
        Map<Item, Integer> inv = snapshot(agent);
        List<String> out = new ArrayList<>();
        java.util.Set<Item> seen = new java.util.HashSet<>();
        for (Simple r : recipes) {
            if (seen.contains(r.output()) || r.needsTable() && !tableAvailable) continue;
            Map<Item, Integer> trial = new HashMap<>(inv);
            if (tryRecipe(trial, r, r.outputCount(), MAX_DEPTH - 1, new ArrayList<>())) {
                seen.add(r.output());
                out.add(ItemResolver.id(r.output()));
            }
        }
        return out;
    }
}
