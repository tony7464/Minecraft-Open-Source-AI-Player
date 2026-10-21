package com.joserayos.aiplayers.agent;

import com.joserayos.aiplayers.agent.goals.FollowOwnerGoal;
import com.joserayos.aiplayers.brain.AgentBrain;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import com.joserayos.aiplayers.perception.BlockInterestCache;
import com.joserayos.aiplayers.skill.combat.Combat;
import com.joserayos.aiplayers.skill.combat.DefendGoal;
import com.joserayos.aiplayers.skill.combat.Reflexes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.joserayos.aiplayers.task.AgentTask;
import com.joserayos.aiplayers.task.TaskExecutor;
import com.joserayos.aiplayers.task.TaskStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The body of an AI agent: a server-side PathfinderMob rendered with the player model.
 * All world interaction happens on the server thread; the {@link AgentBrain} only talks to models off-thread.
 */
public class AgentEntity extends PathfinderMob implements InventoryCarrier {
    private static final EntityDataAccessor<Boolean> DATA_THINKING =
            SynchedEntityData.defineId(AgentEntity.class, EntityDataSerializers.BOOLEAN);

    private String agentName = "Agent";
    private @Nullable UUID ownerId;
    private String profileId = "";
    private String persona = "";
    private AgentMode mode = AgentMode.FOLLOW;
    private final AgentBrain brain = new AgentBrain(this);
    private final SimpleContainer inventory = new SimpleContainer(36);
    private final TaskExecutor executor = new TaskExecutor(this, this::onTaskFinished);
    private final Deque<String> recentEvents = new ArrayDeque<>();
    private String lastResult = "none";
    private @Nullable UUID followTarget;
    private boolean pickupAllowed;
    private int pickedUpCount;
    private final BlockInterestCache blockCache = new BlockInterestCache();
    private final Reflexes reflexes = new Reflexes(this);
    private @Nullable Vec3 guardPos;
    private int guardRadius = 10;

    public AgentEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.getNavigation().setCanOpenDoors(true);
        this.getNavigation().setCanFloat(true);
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0, 6.0F, 3.0F));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                LivingEntity attacker = AgentEntity.this.getLastHurtByMob();
                return attacker != null && Combat.mayAttack(AgentEntity.this, attacker) && super.canUse();
            }
        });
        this.targetSelector.addGoal(2, new DefendGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_THINKING, false);
    }

    // ---- identity ----

    public String getAgentName() { return agentName; }

    public void setAgentName(String name) {
        this.agentName = name;
        refreshNameTag();
    }

    public @Nullable UUID getOwnerId() { return ownerId; }

    public void setOwnerId(@Nullable UUID ownerId) { this.ownerId = ownerId; }

    public boolean isOwnedBy(Player player) { return ownerId != null && ownerId.equals(player.getUUID()); }

    public @Nullable ServerPlayer getOwnerPlayer() {
        if (ownerId == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(ownerId);
    }

    public String getProfileId() { return profileId; }

    public void setProfileId(String profileId) {
        this.profileId = profileId == null ? "" : profileId;
        refreshNameTag();
    }

    public String getPersona() { return persona; }

    public void setPersona(String persona) { this.persona = persona == null ? "" : persona; }

    public AgentMode getMode() { return mode; }

    public void setMode(AgentMode mode) {
        this.mode = mode;
        if (!mode.follows()) this.getNavigation().stop();
        if (mode != AgentMode.GUARD) this.guardPos = null;
    }

    public AgentBrain getAgentBrain() { return brain; }

    public boolean isThinking() { return entityData.get(DATA_THINKING); }

    public void setThinking(boolean thinking) {
        if (isThinking() != thinking) {
            entityData.set(DATA_THINKING, thinking);
            refreshNameTag();
        }
    }

    /** Whether a skill task is currently running or queued. */
    public boolean hasActiveTask() { return !executor.isIdle(); }

    public TaskExecutor getExecutor() { return executor; }

    /** Short description of what the agent is doing, for /ai list and /ai status. */
    public String taskSummary() { return executor.summary(); }

    /** Kill switch: cancel the pending model call, tasks and movement. */
    public void stopEverything(String reason) {
        brain.cancel(reason);
        executor.clear(reason);
        getNavigation().stop();
    }

    private void onTaskFinished(AgentTask task, TaskStatus status, String message) {
        lastResult = task.id() + " " + status.name() + ": " + message;
        addEvent(lastResult);
        brain.onTaskResult(task, status, message);
    }

    // ---- events (for perception) ----

    public void addEvent(String event) {
        long t = level().getGameTime();
        recentEvents.addLast("[t" + t / 20 + "s] " + event);
        while (recentEvents.size() > 10) recentEvents.removeFirst();
    }

    public List<String> recentEvents() {
        long now = level().getGameTime() / 20;
        List<String> out = new ArrayList<>();
        for (String e : recentEvents) {
            int close = e.indexOf("s] ");
            long at = Long.parseLong(e.substring(2, close));
            out.add(e.substring(close + 3) + " (" + (now - at) + "s ago)");
        }
        return out;
    }

    public String lastResult() { return lastResult; }

    // ---- following ----

    /** Who FOLLOW mode follows: a chosen player, or the owner by default. */
    public @Nullable ServerPlayer getFollowTargetPlayer() {
        if (followTarget != null && level() instanceof ServerLevel sl) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(followTarget);
            if (p != null) return p;
        }
        return getOwnerPlayer();
    }

    public void setFollowTarget(@Nullable UUID target) { this.followTarget = target; }

    // ---- inventory ----

    @Override
    public SimpleContainer getInventory() { return inventory; }

    public int countItem(Item item) {
        int n = 0;
        for (ItemStack s : inventory.getItems()) if (s.is(item)) n += s.getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack s = getItemBySlot(slot);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** Remove up to {@code count} of an item (inventory first, then hands). */
    public ItemStack removeItems(Item item, int count) {
        ItemStack removed = inventory.removeItemType(item, count);
        int missing = count - removed.getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            if (missing <= 0) break;
            ItemStack held = getItemBySlot(slot);
            if (held.is(item)) {
                int take = Math.min(missing, held.getCount());
                if (removed.isEmpty()) removed = held.copyWithCount(0);
                held.shrink(take);
                removed.grow(take);
                missing -= take;
            }
        }
        return removed;
    }

    /** Add to inventory; returns what didn't fit. */
    public ItemStack addItem(ItemStack stack) { return inventory.addItem(stack); }

    public boolean canHold(ItemStack stack) { return inventory.canAddItem(stack); }

    public boolean isInventoryFull() {
        for (ItemStack s : inventory.getItems()) if (s.isEmpty()) return false;
        return true;
    }

    /** Inventory grouped by item, e.g. ["oak_log x6", "bread x3"]. */
    public List<String> inventorySummary() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack s : inventory.getItems()) {
            if (!s.isEmpty()) counts.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum);
        }
        List<String> out = new ArrayList<>();
        counts.forEach((k, v) -> out.add(k + " x" + v));
        return out;
    }

    public Reflexes getReflexes() { return reflexes; }

    public boolean isFleeing() { return reflexes.isFleeing(); }

    public int getGuardRadius() { return guardRadius; }

    /** Guard a player (follow + protect) or a fixed position. */
    public void startGuard(@Nullable ServerPlayer player, @Nullable Vec3 pos, int radius) {
        this.guardRadius = radius;
        setMode(AgentMode.GUARD);
        this.guardPos = pos;
        if (player != null) setFollowTarget(player.getUUID());
    }

    /** What GUARD mode protects: the fixed spot, else the followed player, else the agent itself. */
    public Vec3 guardCenter() {
        if (guardPos != null) return guardPos;
        ServerPlayer p = getFollowTargetPlayer();
        return p != null && p.level() == level() ? p.position() : position();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && damage > 0) {
            reflexes.onHurt();
            String from = source.getEntity() != null ? source.getEntity().getName().getString() : source.getMsgId();
            addEvent("took " + Math.round(damage) + " damage from " + from + " (health " + Math.round(getHealth()) + "/" + Math.round(getMaxHealth()) + ")");
        }
        return hurt;
    }

    public BlockInterestCache getBlockCache() { return blockCache; }

    public void setPickupAllowed(boolean allowed) { this.pickupAllowed = allowed; }

    public int drainPickedUpCount() {
        int n = pickedUpCount;
        pickedUpCount = 0;
        return n;
    }

    private void collectNearbyItems(ServerLevel level) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(1.0, 0.5, 1.0))) {
            if (item.isRemoved() || item.getItem().isEmpty() || item.hasPickUpDelay()) continue;
            ItemStack stack = item.getItem();
            if (!inventory.canAddItem(stack)) continue;
            int before = stack.getCount();
            ItemStack rest = inventory.addItem(stack.copy());
            int taken = before - rest.getCount();
            if (taken <= 0) continue;
            take(item, taken);
            pickedUpCount += taken;
            if (rest.isEmpty()) item.discard(); else item.setItem(rest);
        }
    }

    private void refreshNameTag() {
        String label = agentName + (profileId.isEmpty() ? "" : " [" + profileId + "]") + (isThinking() ? " ..." : "");
        setCustomName(Component.literal(label).withStyle(ChatFormatting.AQUA));
        setCustomNameVisible(true);
    }

    // ---- movement helpers ----

    public boolean shouldTeleportTo(ServerPlayer target) {
        int d = AiPlayersConfig.get(AiPlayersConfig.TELEPORT_DISTANCE);
        return d > 0 && distanceToSqr(target) >= (double) d * d;
    }

    /** Teleport to a safe walkable spot around {@code target}, like a tamed wolf. */
    public boolean teleportNear(BlockPos target) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = random.nextIntBetweenInclusive(-3, 3);
            int dz = random.nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            int dy = random.nextIntBetweenInclusive(-1, 1);
            BlockPos pos = target.offset(dx, dy, dz);
            if (WalkNodeEvaluator.getPathTypeStatic(this, pos) != PathType.WALKABLE) continue;
            if (!level().noCollision(this, getBoundingBox().move(pos.subtract(blockPosition())))) continue;
            snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, getYRot(), getXRot());
            getNavigation().stop();
            return true;
        }
        return false;
    }

    // ---- lifecycle ----

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl && isAlive()) {
            reflexes.tick(sl);
            LivingEntity target = getTarget();
            if (target != null && (!target.isAlive() || target.distanceToSqr(this) > 48 * 48)) {
                setTarget(null);
                target = null;
            }
            // Tasks pause while fighting; combat goals own the navigation.
            if (target == null && !reflexes.isFleeing()) executor.tick();
            blockCache.tick(sl, blockPosition());
            if (pickupAllowed) collectNearbyItems(sl);
            brain.serverTick();
        }
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) { return false; }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl) {
            AgentChat.announce(sl.getServer(), Component.literal(agentName + " died: ")
                    .append(source.getLocalizedDeathMessage(this)).withStyle(ChatFormatting.GRAY));
            brain.cancel("died");
            executor.clear("died");
        }
        super.die(source);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        if (AiPlayersConfig.get(AiPlayersConfig.DROP_INVENTORY_ON_DEATH)) {
            for (ItemStack s : inventory.removeAllItems()) spawnAtLocation(level, s);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        brain.cancel("removed");
        super.remove(reason);
    }

    // ---- persistence ----

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("AgentName", agentName);
        output.storeNullable("Owner", UUIDUtil.CODEC, ownerId);
        output.putString("Profile", profileId);
        output.putString("Persona", persona);
        output.putString("Mode", mode.id());
        writeInventoryToTag(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.agentName = input.getStringOr("AgentName", "Agent");
        this.ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        this.profileId = input.getStringOr("Profile", "");
        this.persona = input.getStringOr("Persona", "");
        this.mode = AgentMode.parse(input.getStringOr("Mode", "follow")).orElse(AgentMode.FOLLOW);
        readInventoryFromTag(input);
        refreshNameTag();
    }

    public Optional<ServerLevel> serverLevel() {
        return level() instanceof ServerLevel sl ? Optional.of(sl) : Optional.empty();
    }
}
