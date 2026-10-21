package com.joserayos.aiplayers.skill.craft;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.skill.ItemResolver;
import com.joserayos.aiplayers.skill.build.Placement;
import com.joserayos.aiplayers.skill.movement.Navigation;
import com.joserayos.aiplayers.task.AgentTask;
import com.joserayos.aiplayers.task.TaskStatus;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Smelts items in a nearby furnace (placing one from the inventory if needed). */
public class SmeltTask implements AgentTask {
    private final String requested;
    private final int count;
    private @Nullable Item input;
    private @Nullable Item output;
    private @Nullable BlockPos furnace;
    private boolean loaded;
    private int collected;
    private int repath;
    private int idleTicks;
    private String result = "";

    public SmeltTask(String requested, int count) {
        this.requested = requested;
        this.count = count;
    }

    @Override public String id() { return "smelt"; }
    @Override public String describe() { return "smelt " + count + " " + requested; }
    @Override public String resultMessage() { return result; }
    @Override public String progress() { return collected + "/" + count + " smelted"; }
    @Override public int timeoutTicks() { return count * 200 + 1200; }
    @Override public @Nullable Vec3 movementGoal() { return loaded || furnace == null ? null : Vec3.atCenterOf(furnace); }

    @Override
    public void start(AgentEntity agent) {
        ServerLevel level = (ServerLevel) agent.level();
        Optional<Item> named = ItemResolver.item(requested);
        if (named.isEmpty()) {
            result = "unknown item '" + requested + "'";
            return;
        }
        Item item = named.get();
        // Either the thing to put in (raw_iron) or the thing wanted out (iron_ingot).
        Optional<ItemStack> asInput = smeltResult(level, item);
        if (asInput.isPresent()) {
            input = item;
            output = asInput.get().getItem();
        } else {
            for (RecipeHolder<?> h : level.recipeAccess().getRecipes()) {
                if (!(h.value() instanceof SmeltingRecipe r)) continue;
                if (r.assemble(new SingleRecipeInput(ItemStack.EMPTY)).getItem() != item) continue;
                for (var candidate : r.input().items().toList()) {
                    if (agent.countItem(candidate.value()) > 0) {
                        input = candidate.value();
                        output = item;
                        break;
                    }
                }
                if (input != null) break;
            }
            if (input == null) {
                result = ItemResolver.id(item) + " can't be smelted, and I have nothing that smelts into it";
                return;
            }
        }
        if (agent.countItem(input) < count) {
            result = "I only have " + agent.countItem(input) + " " + ItemResolver.id(input) + ", need " + count;
            input = null;
        }
    }

    static Optional<ItemStack> smeltResult(ServerLevel level, Item item) {
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(item)), level)
                .map(h -> h.value().assemble(new SingleRecipeInput(new ItemStack(item))));
    }

    /** Best fuel in the inventory that isn't the input. */
    private @Nullable Item pickFuel(AgentEntity agent) {
        var fuels = agent.level().fuelValues();
        Item best = null;
        int bestTotal = 0;
        var c = agent.getInventory();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty() || s.getItem() == input || s.is(Items.LAVA_BUCKET)) continue;
            int burn = fuels.burnDuration(s);
            if (burn <= 0) continue;
            int total = burn * agent.countItem(s.getItem());
            if (total > bestTotal) {
                bestTotal = total;
                best = s.getItem();
            }
        }
        return best;
    }

    @Override
    public TaskStatus tick(AgentEntity agent) {
        if (input == null) return TaskStatus.FAILED;
        ServerLevel level = (ServerLevel) agent.level();
        if (furnace == null || !level.getBlockState(furnace).is(Blocks.FURNACE)) {
            furnace = Placement.findNearest(agent, 12, s -> s.is(Blocks.FURNACE)).orElse(null);
            if (furnace == null) {
                if (agent.countItem(Items.FURNACE) == 0) {
                    result = "no furnace nearby and I don't have one (craft_item furnace needs 8 cobblestone)";
                    return TaskStatus.FAILED;
                }
                BlockPos spot = Placement.spotNear(agent).orElse(null);
                String err = spot == null ? "no room" : Placement.place(agent, spot, Items.FURNACE);
                if (err != null) {
                    result = "couldn't place a furnace: " + err;
                    return TaskStatus.FAILED;
                }
                furnace = spot;
            }
        }
        if (agent.getEyePosition().distanceTo(Vec3.atCenterOf(furnace)) > 4.0) {
            if (--repath <= 0) {
                repath = 20;
                Navigation.moveToward(agent, Vec3.atCenterOf(furnace), 1.0);
            }
            return TaskStatus.RUNNING;
        }
        agent.getNavigation().stop();
        agent.getLookControl().setLookAt(Vec3.atCenterOf(furnace));
        if (!(level.getBlockEntity(furnace) instanceof AbstractFurnaceBlockEntity be)) {
            result = "the furnace is gone";
            return TaskStatus.FAILED;
        }
        Container c = be;
        if (!loaded) {
            ItemStack in = c.getItem(0);
            if (!in.isEmpty() && !in.is(input)) {
                result = "the furnace is busy smelting " + ItemResolver.id(in.getItem());
                return TaskStatus.FAILED;
            }
            Item fuel = pickFuel(agent);
            int burn = fuel == null ? 0 : level.fuelValues().burnDuration(new ItemStack(fuel));
            ItemStack fuelSlot = c.getItem(1);
            int existingFuelTicks = fuelSlot.isEmpty() ? 0 : level.fuelValues().burnDuration(fuelSlot) * fuelSlot.getCount();
            int needTicks = Math.max(0, count * 200 - existingFuelTicks);
            if (needTicks > 0) {
                if (fuel == null) {
                    result = "I have no fuel (coal, charcoal, planks or logs)";
                    return TaskStatus.FAILED;
                }
                int fuelCount = (needTicks + burn - 1) / burn;
                if (agent.countItem(fuel) < fuelCount) {
                    result = "not enough fuel: need " + fuelCount + " " + ItemResolver.id(fuel);
                    return TaskStatus.FAILED;
                }
                if (!fuelSlot.isEmpty() && !fuelSlot.is(fuel)) {
                    result = "the furnace has different fuel in it";
                    return TaskStatus.FAILED;
                }
                ItemStack f = agent.removeItems(fuel, fuelCount);
                if (fuelSlot.isEmpty()) c.setItem(1, f);
                else fuelSlot.grow(f.getCount());
            }
            ItemStack add = agent.removeItems(input, count);
            if (in.isEmpty()) c.setItem(0, add);
            else in.grow(add.getCount());
            c.setChanged();
            loaded = true;
            return TaskStatus.RUNNING;
        }
        ItemStack out = c.getItem(2);
        if (!out.isEmpty() && out.is(output)) {
            int take = Math.min(out.getCount(), count - collected);
            ItemStack rest = agent.addItem(out.split(take));
            if (!rest.isEmpty()) agent.spawnAtLocation(level, rest);
            collected += take;
            idleTicks = 0;
            c.setChanged();
        } else if (++idleTicks > 400) {
            result = "the furnace stopped (out of fuel?); got " + collected + "/" + count;
            return TaskStatus.FAILED;
        }
        if (collected >= count) {
            result = "smelted " + collected + " " + ItemResolver.id(output);
            return TaskStatus.SUCCESS;
        }
        return TaskStatus.RUNNING;
    }
}
