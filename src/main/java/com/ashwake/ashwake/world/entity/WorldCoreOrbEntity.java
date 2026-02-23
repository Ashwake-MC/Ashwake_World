package com.ashwake.ashwake.world.entity;

import com.ashwake.ashwake.config.AshwakeConfig;
import com.ashwake.ashwake.registry.AshwakeEntities;
import com.ashwake.ashwake.world.weather.WeatherCoreClientCache;
import com.ashwake.ashwake.world.weather.WeatherCorePhase;
import com.ashwake.ashwake.world.weather.WeatherCoreState;
import com.ashwake.ashwake.world.weather.WeatherCoreVisualProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class WorldCoreOrbEntity extends Entity {
    private static final int NO_ANCHOR = Integer.MIN_VALUE;
    private static final EntityDataAccessor<Integer> DATA_ANCHOR_X =
            SynchedEntityData.defineId(WorldCoreOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANCHOR_Y =
            SynchedEntityData.defineId(WorldCoreOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANCHOR_Z =
            SynchedEntityData.defineId(WorldCoreOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(WorldCoreOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ACTIVE =
            SynchedEntityData.defineId(WorldCoreOrbEntity.class, EntityDataSerializers.BOOLEAN);

    private int bobSalt;
    private int clientParticleWindow = Integer.MIN_VALUE;
    private int clientParticleCount;

    public WorldCoreOrbEntity(EntityType<? extends WorldCoreOrbEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = true;
        this.setNoGravity(true);
    }

    public static WorldCoreOrbEntity createAt(ServerLevel level, BlockPos anchor) {
        WorldCoreOrbEntity orb = new WorldCoreOrbEntity(AshwakeEntities.WORLD_CORE_ORB.get(), level);
        orb.setAnchorPos(anchor);
        orb.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
        return orb;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ANCHOR_X, NO_ANCHOR);
        builder.define(DATA_ANCHOR_Y, NO_ANCHOR);
        builder.define(DATA_ANCHOR_Z, NO_ANCHOR);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_ACTIVE, true);
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
            this.bobSalt = (int) Math.floorMod(this.getUUID().getLeastSignificantBits(), 509L);
        }

        BlockPos anchor = getAnchorPos();
        double bob = Math.sin((this.tickCount + this.bobSalt) * 0.05D) * 0.20D;
        this.setPos(anchor.getX() + 0.5D, anchor.getY() + bob, anchor.getZ() + 0.5D);
        this.setOldPosAndRot();

        if (this.level().isClientSide) {
            tickClientEffects();
        }
    }

    private void tickClientEffects() {
        WeatherCoreClientCache.Snapshot snapshot = WeatherCoreClientCache.snapshot();
        WeatherCoreVisualProfile profile = WeatherCoreVisualProfile.forState(snapshot.state());
        Player focusPlayer = resolveFocusPlayer();
        if (focusPlayer == null) {
            return;
        }
        boolean insideHub = isInsideHub(focusPlayer);
        int particleBudget = insideHub ? 15 : 30;

        int interval = Math.max(1, AshwakeConfig.WORLD_CORE_PARTICLE_RATE.get());
        if ((this.tickCount % interval) != 0) {
            maybePlayAmbientHum(profile, snapshot);
            return;
        }

        boolean omen = snapshot.phase() == WeatherCorePhase.OMEN;
        ParticlePair pair = resolveParticlePair(profile.particleTheme());
        ParticleOptions primary = pair.primary();
        ParticleOptions secondary = pair.secondary();
        if (omen) {
            primary = ParticleTypes.ENCHANT;
            secondary = ParticleTypes.CRIT;
        }

        double t = this.tickCount + this.random.nextDouble();
        int requested = switch (profile.particleTheme()) {
            case CLEAR_MOTES -> 1;
            case WIND_STREAKS, MIST_MOTES -> 1 + this.random.nextInt(2);
            case ASH_FLECKS, DREAD_MOTES, ORBIT_SPARKS, STATIC_SPARKS -> 2 + this.random.nextInt(2);
            case ECLIPSE_VIGNETTE -> 2 + this.random.nextInt(3);
            default -> 1 + this.random.nextInt(2);
        };
        requested = Math.max(1, (int) Math.round(requested * (0.75F + (snapshot.intensity() * 0.55F))));
        if (omen) {
            requested = (int) Math.ceil(requested * 1.30D); // +30% omen density ramp.
        }
        int burstCap = Math.max(1, (int) Math.floor(1.5D * interval));
        int count = Math.min(requested, burstCap);
        for (int i = 0; i < count; i++) {
            if (!consumeParticleBudget(particleBudget)) {
                break;
            }
            double ti = t + (i * 0.6D);
            double radius = 0.9D + (0.3D * Math.sin(ti * 0.07D));
            double angle = ti * 0.25D;
            double yOffset = (ti * 0.02D) % 6.0D;
            double px = this.getX() + (Math.cos(angle) * radius);
            double py = this.getY() - 2.0D + yOffset;
            double pz = this.getZ() + (Math.sin(angle) * radius);
            double vx = -Math.sin(angle) * 0.012D;
            double vy = 0.02D + (this.random.nextDouble() * 0.02D);
            double vz = Math.cos(angle) * 0.012D;

            this.level().addParticle(primary, px, py, pz, vx, vy, vz);
            if (this.random.nextFloat() < 0.40F && consumeParticleBudget(particleBudget)) {
                this.level().addParticle(secondary, px, py, pz, vx * 0.45D, vy * 0.35D, vz * 0.45D);
            }
        }

        if (snapshot.switchBurstStrength() > 0.02F) {
            emitSwitchBurst(snapshot.switchBurstState(), particleBudget, snapshot.switchBurstStrength());
        }

        maybePlayAmbientHum(profile, snapshot);
    }

    private Player resolveFocusPlayer() {
        Player close = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 48.0D, false);
        if (close != null) {
            return close;
        }
        Player interior = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 96.0D, false);
        if (interior != null && !this.level().canSeeSky(interior.blockPosition())) {
            return interior;
        }
        return null;
    }

    private boolean isInsideHub(Player player) {
        if (this.level().canSeeSky(player.blockPosition())) {
            return false;
        }
        double maxDistance = 72.0D;
        return player.distanceToSqr(this.getX(), this.getY(), this.getZ()) <= (maxDistance * maxDistance);
    }

    private ParticlePair resolveParticlePair(WeatherCoreVisualProfile.ParticleTheme theme) {
        return switch (theme) {
            case CLEAR_MOTES -> new ParticlePair(ParticleTypes.END_ROD, ParticleTypes.CRIT);
            case COPPER_SWIRL -> new ParticlePair(ParticleTypes.SMALL_FLAME, ParticleTypes.SMOKE);
            case MIST_MOTES -> new ParticlePair(ParticleTypes.ENCHANT, ParticleTypes.SMOKE);
            case WIND_STREAKS -> new ParticlePair(ParticleTypes.CLOUD, ParticleTypes.END_ROD);
            case ASH_FLECKS -> new ParticlePair(ParticleTypes.ASH, ParticleTypes.SMOKE);
            case DARK_STARS -> new ParticlePair(ParticleTypes.END_ROD, ParticleTypes.SOUL);
            case DREAD_MOTES -> new ParticlePair(ParticleTypes.ASH, ParticleTypes.SOUL);
            case ORBIT_SPARKS -> new ParticlePair(ParticleTypes.ELECTRIC_SPARK, ParticleTypes.CRIT);
            case ECLIPSE_VIGNETTE -> new ParticlePair(ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.END_ROD);
            case STATIC_SPARKS -> new ParticlePair(ParticleTypes.ELECTRIC_SPARK, ParticleTypes.ENCHANT);
            default -> new ParticlePair(ParticleTypes.SMALL_FLAME, ParticleTypes.LAVA);
        };
    }

    private void emitSwitchBurst(WeatherCoreState state, int budget, float strength) {
        int count = Math.max(4, (int) Math.ceil(8.0F * strength));
        ParticlePair burst = switch (state.category()) {
            case GOOD -> new ParticlePair(ParticleTypes.SMALL_FLAME, ParticleTypes.LAVA);
            case BAD -> new ParticlePair(ParticleTypes.ASH, ParticleTypes.SMOKE);
            case RARE -> new ParticlePair(ParticleTypes.ELECTRIC_SPARK, ParticleTypes.END_ROD);
        };

        for (int i = 0; i < count; i++) {
            if (!consumeParticleBudget(budget)) {
                break;
            }
            double angle = (Math.PI * 2.0D) * i / count;
            double radius = 1.4D + (0.25D * this.random.nextDouble());
            double px = this.getX() + (Math.cos(angle) * radius);
            double py = this.getY() + 0.2D + (this.random.nextDouble() * 0.6D);
            double pz = this.getZ() + (Math.sin(angle) * radius);
            double vx = Math.cos(angle) * 0.03D;
            double vz = Math.sin(angle) * 0.03D;
            this.level().addParticle(burst.primary(), px, py, pz, vx, 0.03D, vz);
            if (this.random.nextFloat() < 0.5F && consumeParticleBudget(budget)) {
                this.level().addParticle(burst.secondary(), px, py, pz, vx * 0.4D, 0.02D, vz * 0.4D);
            }
        }
    }

    private void maybePlayAmbientHum(WeatherCoreVisualProfile profile, WeatherCoreClientCache.Snapshot snapshot) {
        if (!AshwakeConfig.WORLD_CORE_SOUND_ENABLED.get()) {
            return;
        }
        int interval = switch (profile.soundTheme()) {
            case NEAR_SILENT -> 180;
            case STORM_RUMBLE, DEEP_BASS -> 80;
            case SHARP_RING -> 70;
            case WARBLE_HUM -> 84;
            default -> 100;
        };
        if ((this.tickCount % interval) != 0) {
            return;
        }

        float basePitch = switch (profile.soundTheme()) {
            case NEAR_SILENT -> 1.35F;
            case FORGE_CRACKLE -> 1.05F;
            case SOFT_RAIN -> 0.92F;
            case WHOOSH_CHIMES -> 1.22F;
            case STORM_RUMBLE -> 0.74F;
            case REST_DENIED -> 0.82F;
            case MUFFLED_WIND -> 0.86F;
            case WARBLE_HUM -> 1.06F;
            case DEEP_BASS -> 0.68F;
            case SHARP_RING -> 1.32F;
            default -> 1.12F;
        };

        float volume = switch (profile.soundTheme()) {
            case NEAR_SILENT -> 0.08F;
            case STORM_RUMBLE, DEEP_BASS -> 0.22F;
            default -> 0.16F;
        };
        if (snapshot.phase() == WeatherCorePhase.OMEN) {
            basePitch += 0.10F + (0.24F * snapshot.omenProgress()); // Omen pitch ramp-up.
            volume += 0.05F;
        }

        this.level().playLocalSound(
                this.getX(),
                this.getY(),
                this.getZ(),
                SoundEvents.BEACON_AMBIENT,
                SoundSource.AMBIENT,
                volume,
                basePitch,
                false);
    }

    private boolean consumeParticleBudget(int maxPerSecond) {
        int secondWindow = this.tickCount / 20;
        if (secondWindow != this.clientParticleWindow) {
            this.clientParticleWindow = secondWindow;
            this.clientParticleCount = 0;
        }
        if (this.clientParticleCount >= maxPerSecond) {
            return false;
        }
        this.clientParticleCount++;
        return true;
    }

    private record ParticlePair(ParticleOptions primary, ParticleOptions secondary) {
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.ashwake.world_core_pulse"));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
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
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("anchor_x") && tag.contains("anchor_y") && tag.contains("anchor_z")) {
            this.entityData.set(DATA_ANCHOR_X, tag.getInt("anchor_x"));
            this.entityData.set(DATA_ANCHOR_Y, tag.getInt("anchor_y"));
            this.entityData.set(DATA_ANCHOR_Z, tag.getInt("anchor_z"));
        }
        this.entityData.set(DATA_VARIANT, tag.getInt("variant"));
        this.entityData.set(DATA_ACTIVE, tag.getBoolean("active"));
        this.bobSalt = tag.getInt("bob_salt");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        BlockPos anchor = getAnchorPos();
        tag.putInt("anchor_x", anchor.getX());
        tag.putInt("anchor_y", anchor.getY());
        tag.putInt("anchor_z", anchor.getZ());
        tag.putInt("variant", getVariant());
        tag.putBoolean("active", isActiveCore());
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

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(DATA_VARIANT, variant);
    }

    public boolean isActiveCore() {
        return this.entityData.get(DATA_ACTIVE);
    }

    public void setActiveCore(boolean active) {
        this.entityData.set(DATA_ACTIVE, active);
    }
}
