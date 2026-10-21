package com.joserayos.aiplayers.agent;

import com.joserayos.aiplayers.agent.goals.FollowOwnerGoal;
import com.joserayos.aiplayers.brain.AgentBrain;
import com.joserayos.aiplayers.config.AiPlayersConfig;
import java.util.Optional;
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
public class AgentEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> DATA_THINKING =
            SynchedEntityData.defineId(AgentEntity.class, EntityDataSerializers.BOOLEAN);

    private String agentName = "Agent";
    private @Nullable UUID ownerId;
    private String profileId = "";
    private String persona = "";
    private AgentMode mode = AgentMode.FOLLOW;
    private final AgentBrain brain = new AgentBrain(this);

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
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0, 6.0F, 3.0F));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
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
        if (mode != AgentMode.FOLLOW) this.getNavigation().stop();
    }

    public AgentBrain getAgentBrain() { return brain; }

    public boolean isThinking() { return entityData.get(DATA_THINKING); }

    public void setThinking(boolean thinking) {
        if (isThinking() != thinking) {
            entityData.set(DATA_THINKING, thinking);
            refreshNameTag();
        }
    }

    /** Whether a skill task is currently running (task framework arrives in M3). */
    public boolean hasActiveTask() { return false; }

    /** Short description of what the agent is doing, for /ai list and /ai status. */
    public String taskSummary() { return "task: none"; }

    /** Kill switch: cancel the pending model call, tasks and movement. */
    public void stopEverything(String reason) {
        brain.cancel(reason);
        getNavigation().stop();
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
        if (level() instanceof ServerLevel) {
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
        }
        super.die(source);
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
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.agentName = input.getStringOr("AgentName", "Agent");
        this.ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        this.profileId = input.getStringOr("Profile", "");
        this.persona = input.getStringOr("Persona", "");
        this.mode = AgentMode.parse(input.getStringOr("Mode", "follow")).orElse(AgentMode.FOLLOW);
        refreshNameTag();
    }

    public Optional<ServerLevel> serverLevel() {
        return level() instanceof ServerLevel sl ? Optional.of(sl) : Optional.empty();
    }
}
