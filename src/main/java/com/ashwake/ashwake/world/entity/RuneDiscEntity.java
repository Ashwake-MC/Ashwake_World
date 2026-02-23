package com.ashwake.ashwake.world.entity;

import com.ashwake.ashwake.config.AshwakeConfig;
import com.ashwake.ashwake.registry.AshwakeEntities;
import com.ashwake.ashwake.world.weather.WeatherCoreClientCache;
import com.ashwake.ashwake.world.weather.WeatherCorePhase;
import com.ashwake.ashwake.world.weather.WeatherCoreState;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RuneDiscEntity extends Entity {
    public static final int STATE_IDLE = 0;
    public static final int STATE_ACTIVE = 1;
    public static final int STATE_LOCKED = 2;

    private static final int PARTICLE_CAP_PER_SECOND = 15;
    private static final int PULSE_PERIOD = 80;
    private static final int NO_ANCHOR = Integer.MIN_VALUE;
    private static final EntityDataAccessor<Integer> DATA_ANCHOR_X =
            SynchedEntityData.defineId(RuneDiscEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANCHOR_Y =
            SynchedEntityData.defineId(RuneDiscEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANCHOR_Z =
            SynchedEntityData.defineId(RuneDiscEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CORE_ACTIVE =
            SynchedEntityData.defineId(RuneDiscEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_RITUAL_STATE =
            SynchedEntityData.defineId(RuneDiscEntity.class, EntityDataSerializers.INT);

    private int bobSalt;
    private int clientParticleWindow = Integer.MIN_VALUE;
    private int clientParticleCount;

    public RuneDiscEntity(EntityType<? extends RuneDiscEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
        this.setNoGravity(true);
    }

    public static RuneDiscEntity createAt(ServerLevel level, BlockPos anchor) {
        RuneDiscEntity disc = new RuneDiscEntity(AshwakeEntities.RUNE_DISC.get(), level);
        disc.setAnchorPos(anchor);
        disc.setPos(anchor.getX() + 0.5D, anchor.getY() + 0.35D, anchor.getZ() + 0.5D);
        return disc;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ANCHOR_X, NO_ANCHOR);
        builder.define(DATA_ANCHOR_Y, NO_ANCHOR);
        builder.define(DATA_ANCHOR_Z, NO_ANCHOR);
        builder.define(DATA_CORE_ACTIVE, true);
        builder.define(DATA_RITUAL_STATE, STATE_IDLE);
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);

        if (!hasAnchor()) {
            setAnchorPos(this.blockPosition());
        }
        if (this.bobSalt == 0) {
            this.bobSalt = (int) Math.floorMod(this.getUUID().getMostSignificantBits(), 409L);
        }

        if (!this.level().isClientSide && ((this.tickCount % 40) == 0 || this.tickCount < 8)) {
            updateCoreLinkState();
        }

        BlockPos anchor = getAnchorPos();
        double bob = Math.sin((this.tickCount + this.bobSalt) * 0.045D) * 0.07D;
        this.setPos(anchor.getX() + 0.5D, anchor.getY() + 0.35D + bob, anchor.getZ() + 0.5D);
        this.setOldPosAndRot();

        if (this.level().isClientSide) {
            tickClientEffects();
        }
    }

    private void updateCoreLinkState() {
        BlockPos anchor = getAnchorPos();
        AABB scan = new AABB(anchor).inflate(26.0D, 16.0D, 26.0D);
        List<WorldCoreOrbEntity> nearby = this.level().getEntitiesOfClass(WorldCoreOrbEntity.class, scan, e -> !e.isRemoved());
        if (nearby.isEmpty()) {
            setCoreActiveLinked(false);
            setRitualState(STATE_IDLE);
            return;
        }
        nearby.sort(Comparator.comparingDouble(e -> e.distanceToSqr(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D)));
        boolean active = nearby.get(0).isActiveCore();
        setCoreActiveLinked(active);
        if (getRitualState() != STATE_LOCKED) {
            setRitualState(active ? STATE_ACTIVE : STATE_IDLE);
        }
    }

    private void tickClientEffects() {
        Player nearest = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 48.0D, false);
        if (nearest == null) {
            return;
        }
        WeatherCoreClientCache.Snapshot snapshot = WeatherCoreClientCache.snapshot();

        if ((this.tickCount % 3) == 0) {
            spawnSwirlParticles(snapshot);
        }
        if ((this.tickCount % PULSE_PERIOD) == 0) {
            spawnPulseBurst(snapshot);
        }
        if (snapshot.phase() == WeatherCorePhase.OMEN && (this.tickCount % (5 * 20)) == 0) {
            WeatherCoreState flashState = snapshot.nextState() == null ? snapshot.state() : snapshot.nextState();
            spawnOmenRuneFlash(flashState);
        }
        if (snapshot.switchBurstStrength() > 0.02F && (this.tickCount % 2) == 0) {
            spawnSwitchMomentBurst(snapshot.switchBurstState(), snapshot.switchBurstStrength());
        }
    }

    private void spawnSwirlParticles(WeatherCoreClientCache.Snapshot snapshot) {
        BlockPos anchor = getAnchorPos();
        double t = this.tickCount + this.random.nextDouble();
        int state = getRitualState();
        int particleCount = state == STATE_ACTIVE ? 2 : 1;
        double speed = state == STATE_ACTIVE ? 0.22D : 0.18D;
        if (snapshot.phase() == WeatherCorePhase.OMEN) {
            particleCount = Math.max(1, (int) Math.ceil(particleCount * 1.30D));
            speed += 0.03D;
        }

        for (int i = 0; i < particleCount; i++) {
            double angle = (t * speed) + (i * Math.PI);
            double radius = 5.2D + (Math.sin((t + i) * 0.08D) * 0.18D);
            double px = anchor.getX() + 0.5D + (Math.cos(angle) * radius);
            double py = anchor.getY() + 0.10D + (Math.sin((t + i) * 0.07D) * 0.04D);
            double pz = anchor.getZ() + 0.5D + (Math.sin(angle) * radius);
            double vx = -Math.sin(angle) * 0.010D;
            double vz = Math.cos(angle) * 0.010D;

            emitParticle(ParticleTypes.SMALL_FLAME, px, py, pz, vx, 0.011D, vz);
            if (this.random.nextFloat() < 0.35F) {
                emitParticle(ParticleTypes.LAVA, px, py, pz, vx * 0.25D, 0.015D, vz * 0.25D);
            }
            if (this.random.nextFloat() < 0.14F) {
                emitParticle(ParticleTypes.SMOKE, px, py, pz, vx * 0.40D, 0.01D, vz * 0.40D);
            }
        }
    }

    private void spawnPulseBurst(WeatherCoreClientCache.Snapshot snapshot) {
        BlockPos anchor = getAnchorPos();
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2.0D) * i / 8.0D;
            double radius = 5.0D + (this.random.nextDouble() * 0.25D);
            double px = anchor.getX() + 0.5D + (Math.cos(angle) * radius);
            double py = anchor.getY() + 0.18D;
            double pz = anchor.getZ() + 0.5D + (Math.sin(angle) * radius);
            emitParticle(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 0.0D, 0.018D, 0.0D);
            if (this.random.nextFloat() < 0.45F) {
                emitParticle(ParticleTypes.SMOKE, px, py, pz, 0.0D, 0.01D, 0.0D);
            }
        }

        for (int i = 0; i < 4; i++) {
            double angle = (Math.PI / 4.0D) + ((Math.PI * 2.0D) * i / 4.0D);
            double radius = 2.0D;
            double px = anchor.getX() + 0.5D + (Math.cos(angle) * radius);
            double py = anchor.getY() + 0.14D;
            double pz = anchor.getZ() + 0.5D + (Math.sin(angle) * radius);
            emitParticle(ParticleTypes.SMALL_FLAME, px, py, pz, 0.0D, 0.02D, 0.0D);
        }

        if (AshwakeConfig.WORLD_CORE_SOUND_ENABLED.get()) {
            float omenBoost = snapshot.phase() == WeatherCorePhase.OMEN ? (0.08F + (0.18F * snapshot.omenProgress())) : 0.0F;
            this.level().playLocalSound(
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    SoundEvents.BEACON_AMBIENT,
                    SoundSource.AMBIENT,
                    0.08F + omenBoost * 0.5F,
                    (getRitualState() == STATE_ACTIVE ? 0.92F : 0.80F) + omenBoost,
                    false);
        }
    }

    private void spawnOmenRuneFlash(WeatherCoreState nextState) {
        BlockPos anchor = getAnchorPos();
        ParticleOptions primary = switch (nextState.category()) {
            case GOOD -> ParticleTypes.SMALL_FLAME;
            case BAD -> ParticleTypes.ASH;
            case RARE -> ParticleTypes.ELECTRIC_SPARK;
        };
        ParticleOptions secondary = switch (nextState.category()) {
            case GOOD -> ParticleTypes.END_ROD;
            case BAD -> ParticleTypes.SOUL_FIRE_FLAME;
            case RARE -> ParticleTypes.ENCHANT;
        };

        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2.0D) * i / 8.0D;
            double radius = 5.1D;
            double px = anchor.getX() + 0.5D + (Math.cos(angle) * radius);
            double py = anchor.getY() + 0.16D;
            double pz = anchor.getZ() + 0.5D + (Math.sin(angle) * radius);
            emitParticle(primary, px, py, pz, 0.0D, 0.03D, 0.0D);
            emitParticle(secondary, px, py + 0.08D, pz, 0.0D, 0.015D, 0.0D);
        }
    }

    private void spawnSwitchMomentBurst(WeatherCoreState state, float strength) {
        BlockPos anchor = getAnchorPos();
        int count = Math.max(4, (int) Math.ceil(8.0F * strength));
        ParticleOptions particle = switch (state.category()) {
            case GOOD -> ParticleTypes.SMALL_FLAME;
            case BAD -> ParticleTypes.ASH;
            case RARE -> ParticleTypes.ELECTRIC_SPARK;
        };
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D) * i / count;
            double radius = 2.3D + (this.random.nextDouble() * 0.45D);
            double px = anchor.getX() + 0.5D + (Math.cos(angle) * radius);
            double py = anchor.getY() + 0.12D;
            double pz = anchor.getZ() + 0.5D + (Math.sin(angle) * radius);
            double vx = Math.cos(angle) * 0.02D;
            double vz = Math.sin(angle) * 0.02D;
            emitParticle(particle, px, py, pz, vx, 0.02D, vz);
        }
    }

    private boolean emitParticle(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
        if (!consumeParticleBudget()) {
            return false;
        }
        this.level().addParticle(particle, x, y, z, vx, vy, vz);
        return true;
    }

    private boolean consumeParticleBudget() {
        int window = this.tickCount / 20;
        if (window != this.clientParticleWindow) {
            this.clientParticleWindow = window;
            this.clientParticleCount = 0;
        }
        if (this.clientParticleCount >= PARTICLE_CAP_PER_SECOND) {
            return false;
        }
        this.clientParticleCount++;
        return true;
    }

    public float getSpinDegrees(float partialTick) {
        float speed = switch (getRitualState()) {
            case STATE_ACTIVE -> 1.55F;
            case STATE_LOCKED -> 0.35F;
            default -> 1.05F;
        };
        return (this.tickCount + partialTick) * speed;
    }

    public float getGlowAlpha(float partialTick) {
        float t = this.tickCount + partialTick;
        float base = switch (getRitualState()) {
            case STATE_ACTIVE -> 0.74F;
            case STATE_LOCKED -> 0.40F;
            default -> 0.58F;
        };
        float pulse = getPulseBoost(partialTick);
        return Mth.clamp(base + ((float) Math.sin(t * 0.11F) * 0.10F) + pulse, 0.28F, 0.98F);
    }

    private float getPulseBoost(float partialTick) {
        float phase = (this.tickCount + partialTick) % PULSE_PERIOD;
        if (phase > 10.0F) {
            return 0.0F;
        }
        float strength = 1.0F - (phase / 10.0F);
        return strength * (getRitualState() == STATE_ACTIVE ? 0.24F : 0.16F);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            Component message = switch (getRitualState()) {
                case STATE_ACTIVE -> Component.translatable("message.ashwake.ritual_active");
                case STATE_LOCKED -> Component.translatable("message.ashwake.ritual_locked");
                default -> Component.translatable("message.ashwake.ritual_dormant");
            };
            player.sendSystemMessage(message);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("anchor_x") && tag.contains("anchor_y") && tag.contains("anchor_z")) {
            this.entityData.set(DATA_ANCHOR_X, tag.getInt("anchor_x"));
            this.entityData.set(DATA_ANCHOR_Y, tag.getInt("anchor_y"));
            this.entityData.set(DATA_ANCHOR_Z, tag.getInt("anchor_z"));
        }
        this.entityData.set(DATA_CORE_ACTIVE, tag.getBoolean("core_active"));
        this.entityData.set(DATA_RITUAL_STATE, Mth.clamp(tag.getInt("ritual_state"), STATE_IDLE, STATE_LOCKED));
        this.bobSalt = tag.getInt("bob_salt");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        BlockPos anchor = getAnchorPos();
        tag.putInt("anchor_x", anchor.getX());
        tag.putInt("anchor_y", anchor.getY());
        tag.putInt("anchor_z", anchor.getZ());
        tag.putBoolean("core_active", isCoreActiveLinked());
        tag.putInt("ritual_state", getRitualState());
        tag.putInt("bob_salt", this.bobSalt);
    }

    public void setAnchorPos(BlockPos pos) {
        this.entityData.set(DATA_ANCHOR_X, pos.getX());
        this.entityData.set(DATA_ANCHOR_Y, pos.getY());
        this.entityData.set(DATA_ANCHOR_Z, pos.getZ());
    }

    public BlockPos getAnchorPos() {
        return new BlockPos(
                this.entityData.get(DATA_ANCHOR_X),
                this.entityData.get(DATA_ANCHOR_Y),
                this.entityData.get(DATA_ANCHOR_Z));
    }

    public boolean hasAnchor() {
        return this.entityData.get(DATA_ANCHOR_X) != NO_ANCHOR
                && this.entityData.get(DATA_ANCHOR_Y) != NO_ANCHOR
                && this.entityData.get(DATA_ANCHOR_Z) != NO_ANCHOR;
    }

    public boolean isCoreActiveLinked() {
        return this.entityData.get(DATA_CORE_ACTIVE);
    }

    public void setCoreActiveLinked(boolean active) {
        this.entityData.set(DATA_CORE_ACTIVE, active);
    }

    public int getRitualState() {
        return this.entityData.get(DATA_RITUAL_STATE);
    }

    public void setRitualState(int state) {
        this.entityData.set(DATA_RITUAL_STATE, Mth.clamp(state, STATE_IDLE, STATE_LOCKED));
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }
}
