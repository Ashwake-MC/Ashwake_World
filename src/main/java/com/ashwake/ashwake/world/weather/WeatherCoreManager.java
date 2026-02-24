package com.ashwake.ashwake.world.weather;

import com.ashwake.api.WeatherCoreSnapshot;
import com.ashwake.ashwake.AshwakeMod;
import com.ashwake.ashwake.config.AshwakeConfig;
import com.ashwake.ashwake.network.WeatherCoreNetwork;
import com.ashwake.ashwake.world.AshwakeWorldData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class WeatherCoreManager {
    private static final long EFFECT_INTERVAL_TICKS = 100L;
    private static final long RESYNC_INTERVAL_TICKS = 20L;
    private static final long NIGHT_LOCK_INTERVAL_TICKS = 40L;
    private static final int MIN_CYCLE_TICKS = 20 * 20;
    private static final int MIN_OMEN_TICKS = 20 * 4;
    private static final Holder<MobEffect> EFFECT_SPEED = effect("speed");
    private static final Holder<MobEffect> EFFECT_HASTE = effect("haste");
    private static final Holder<MobEffect> EFFECT_REGENERATION = effect("regeneration");
    private static final Holder<MobEffect> EFFECT_SLOW_FALLING = effect("slow_falling");
    private static final Holder<MobEffect> EFFECT_JUMP_BOOST = effect("jump_boost");
    private static final Holder<MobEffect> EFFECT_NIGHT_VISION = effect("night_vision");
    private static final Holder<MobEffect> EFFECT_FIRE_RESISTANCE = effect("fire_resistance");
    private static final Holder<MobEffect> EFFECT_SATURATION = effect("saturation");
    private static final Holder<MobEffect> EFFECT_LUCK = effect("luck");
    private static final Holder<MobEffect> EFFECT_RESISTANCE = effect("resistance");
    private static final Holder<MobEffect> EFFECT_SLOWNESS = effect("slowness");
    private static final Holder<MobEffect> EFFECT_WEAKNESS = effect("weakness");
    private static final Holder<MobEffect> EFFECT_MINING_FATIGUE = effect("mining_fatigue");
    private static final Holder<MobEffect> EFFECT_DARKNESS = effect("darkness");
    private static final Holder<MobEffect> EFFECT_BLINDNESS = effect("blindness");
    private static final Holder<MobEffect> EFFECT_STRENGTH = effect("strength");
    private static final Holder<MobEffect> EFFECT_GLOWING = effect("glowing");
    private static final Holder<MobEffect> EFFECT_NAUSEA = effect("nausea");

    private WeatherCoreManager() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        AshwakeConfig.Settings settings = AshwakeConfig.snapshot();
        if (!settings.enableWorldCoreOrb()) {
            return;
        }

        ServerLevel level = event.getServer().overworld();
        if (level == null) {
            return;
        }

        AshwakeWorldData worldData = AshwakeWorldData.get(level);
        if (!worldData.isPlaced()) {
            return;
        }

        tickWeatherCore(level, worldData, settings);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!Level.OVERWORLD.equals(player.serverLevel().dimension())) {
            return;
        }

        AshwakeConfig.Settings settings = AshwakeConfig.snapshot();
        if (!settings.enableWorldCoreOrb()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        AshwakeWorldData worldData = AshwakeWorldData.get(level);
        if (!worldData.isPlaced()) {
            return;
        }

        WeatherCoreSavedData data = WeatherCoreSavedData.get(level);
        if (!data.isInitialized()) {
            return;
        }

        WeatherCoreState current = data.getCurrentState();
        boolean sleepLocked = isSleepLocked(settings, current);
        float intensity = computeSyncIntensity(worldData, settings, data, current, player);
        WeatherCoreNetwork.syncToPlayer(player, data, sleepLocked, intensity);
    }

    public static void onCanPlayerSleep(CanPlayerSleepEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return;
        }

        AshwakeConfig.Settings settings = AshwakeConfig.snapshot();
        if (!settings.enableWorldCoreOrb() || !settings.weatherCoreSleepLockEnabled()) {
            return;
        }

        AshwakeWorldData worldData = AshwakeWorldData.get(level);
        if (!worldData.isPlaced()) {
            return;
        }

        WeatherCoreSavedData data = WeatherCoreSavedData.get(level);
        if (!data.isInitialized() || !isSleepLocked(settings, data.getCurrentState())) {
            return;
        }

        BlockState state = event.getState();
        if (!state.isAir() && state.getBlock() instanceof BedBlock) {
            event.setProblem(BedSleepingProblem.OTHER_PROBLEM);
            event.getEntity().sendSystemMessage(Component.translatable("message.ashwake.core_denies_rest"));
        }
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof LightningBolt bolt)) {
            return;
        }
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return;
        }

        AshwakeConfig.Settings settings = AshwakeConfig.snapshot();
        if (!settings.enableWorldCoreOrb() || !settings.weatherCoreDisableLightningInSafeRadius()) {
            return;
        }

        AshwakeWorldData worldData = AshwakeWorldData.get(level);
        if (!worldData.isPlaced()) {
            return;
        }

        WeatherCoreSavedData data = WeatherCoreSavedData.get(level);
        if (!data.isInitialized() || data.getCurrentState() != WeatherCoreState.ASHWAKE_STORM) {
            return;
        }

        if (isInsideSafeRadius(worldData, bolt.position(), settings.weatherCoreSafeRadius())) {
            event.setCanceled(true);
        }
    }

    private static void tickWeatherCore(ServerLevel level, AshwakeWorldData worldData, AshwakeConfig.Settings settings) {
        WeatherCoreSavedData data = WeatherCoreSavedData.get(level);

        if (!data.isInitialized()) {
            WeatherCoreState initial = rollNextState(level, settings, null, null);
            startState(level, worldData, settings, data, initial, null);
            AshwakeMod.LOGGER.info("Ashwake Weather Core initialized with state {}", initial.id());
        }

        if (data.getPhase() == WeatherCorePhase.ACTIVE
                && data.getTicksRemaining() > 0
                && data.getTicksRemaining() <= data.getOmenTicks()) {
            enterOmen(level, worldData, settings, data);
        }

        if (data.getTicksRemaining() <= 0) {
            advanceState(level, worldData, settings, data);
        }

        WeatherCoreState currentState = data.getCurrentState();
        if (currentState.keepsNightLocked() && (level.getGameTime() % NIGHT_LOCK_INTERVAL_TICKS) == 0L) {
            level.setDayTime(18000L + Math.floorMod(level.getDayTime(), 24000L));
        }

        long gameTime = level.getGameTime();
        if ((gameTime % EFFECT_INTERVAL_TICKS) == 0L) {
            applyStateEffects(level, worldData, settings, currentState);
        }
        if ((gameTime % RESYNC_INTERVAL_TICKS) == 0L) {
            syncStateToAllPlayers(level, worldData, settings, data, currentState);
        }

        data.setTicksRemaining(Math.max(0, data.getTicksRemaining() - 1));
    }

    private static void enterOmen(
            ServerLevel level,
            AshwakeWorldData worldData,
            AshwakeConfig.Settings settings,
            WeatherCoreSavedData data) {
        if (data.getPhase() == WeatherCorePhase.OMEN) {
            return;
        }

        WeatherCoreSnapshot previousSnapshot = WeatherCoreApiBridge.createServerSnapshot(level, worldData, settings, data, -1.0F);
        WeatherCoreState next = rollNextState(level, settings, data.getCurrentState(), previousSnapshot);
        data.setPhase(WeatherCorePhase.OMEN);
        data.setNextState(next);

        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(Component.translatable("message.ashwake.core_stirs"), true);
        }

        BlockPos corePos = worldData.getWorldCorePos();
        if (corePos != null && !corePos.equals(BlockPos.ZERO)) {
            level.playSound(
                    null,
                    corePos.getX() + 0.5D,
                    corePos.getY() + 0.5D,
                    corePos.getZ() + 0.5D,
                    net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                    net.minecraft.sounds.SoundSource.AMBIENT,
                    0.45F,
                    1.35F);
        }

        WeatherCoreSnapshot currentSnapshot = WeatherCoreApiBridge.createServerSnapshot(level, worldData, settings, data, -1.0F);
        if (previousSnapshot.phase() != currentSnapshot.phase()) {
            WeatherCoreApiEvents.firePhaseChange(level, previousSnapshot.phase(), currentSnapshot.phase(), currentSnapshot);
        }
        WeatherCoreApiEvents.fireOmenStart(level, currentSnapshot, data.getOmenTicks());

        syncStateToAllPlayers(level, worldData, settings, data, data.getCurrentState());
    }

    private static void advanceState(
            ServerLevel level,
            AshwakeWorldData worldData,
            AshwakeConfig.Settings settings,
            WeatherCoreSavedData data) {
        WeatherCoreState previous = data.getCurrentState();
        WeatherCoreState next = data.getNextState();
        if (next == null) {
            next = rollNextState(
                    level,
                    settings,
                    previous,
                    WeatherCoreApiBridge.createServerSnapshot(level, worldData, settings, data, -1.0F));
        }

        if (previous == WeatherCoreState.ECLIPSE_MINUTE && data.getEclipseRestoreDayTime() >= 0L) {
            level.setDayTime(data.getEclipseRestoreDayTime());
            data.setEclipseRestoreDayTime(-1L);
        }

        startState(level, worldData, settings, data, next, previous);
        AshwakeMod.LOGGER.info("Ashwake Weather Core switched to {}", next.id());
    }

    private static void startState(
            ServerLevel level,
            AshwakeWorldData worldData,
            AshwakeConfig.Settings settings,
            WeatherCoreSavedData data,
            WeatherCoreState state,
            WeatherCoreState previous) {
        WeatherCoreSnapshot previousSnapshot = data.isInitialized()
                ? WeatherCoreApiBridge.createServerSnapshot(level, worldData, settings, data, -1.0F)
                : null;

        int totalTicks = resolveTotalTicks(settings, state);
        int omenTicks = resolveOmenTicks(settings, totalTicks);

        if (!data.isInitialized()) {
            data.initializeCycle(state, totalTicks, totalTicks, omenTicks);
        } else {
            data.setCurrentState(state);
            data.setPhase(WeatherCorePhase.ACTIVE);
            data.setNextState(null);
            data.setTotalTicks(totalTicks);
            data.setOmenTicks(omenTicks);
            data.setTicksRemaining(totalTicks);
        }

        applyWorldStateStart(level, settings, data, state, previous);
        applySwitchBurst(level, settings, state);

        WeatherCoreSnapshot currentSnapshot = WeatherCoreApiBridge.createServerSnapshot(level, worldData, settings, data, -1.0F);
        if (previousSnapshot != null) {
            if (!previousSnapshot.stateId().equals(currentSnapshot.stateId())) {
                WeatherCoreApiEvents.fireStateChange(level, previousSnapshot, currentSnapshot);
            }
            if (previousSnapshot.phase() != currentSnapshot.phase()) {
                WeatherCoreApiEvents.firePhaseChange(level, previousSnapshot.phase(), currentSnapshot.phase(), currentSnapshot);
            }
            if (previousSnapshot.sleepDisabled() != currentSnapshot.sleepDisabled()) {
                WeatherCoreApiEvents.fireSleepLock(level, currentSnapshot.sleepDisabled(), currentSnapshot);
            }
        }

        syncStateToAllPlayers(level, worldData, settings, data, state);
    }

    private static int resolveTotalTicks(AshwakeConfig.Settings settings, WeatherCoreState state) {
        if (state.durationOverrideTicks() > 0) {
            return state.durationOverrideTicks();
        }
        int configured = settings.weatherCoreCycleMinutes() * 60 * 20;
        return Math.max(MIN_CYCLE_TICKS, configured);
    }

    private static int resolveOmenTicks(AshwakeConfig.Settings settings, int totalTicks) {
        int configured = settings.weatherCoreOmenSeconds() * 20;
        int capped = Math.min(configured, Math.max(MIN_OMEN_TICKS, totalTicks / 3));
        return Mth.clamp(capped, MIN_OMEN_TICKS, Math.max(MIN_OMEN_TICKS, totalTicks - 20));
    }

    private static WeatherCoreState rollNextState(
            ServerLevel level,
            AshwakeConfig.Settings settings,
            WeatherCoreState previous,
            WeatherCoreSnapshot currentSnapshot) {
        RandomSource random = level.getRandom();
        int rareGate = Mth.clamp(settings.weatherCoreRareGatePercent(), 0, 100);
        List<WeatherCoreState> pool;
        if (random.nextInt(100) < rareGate) {
            pool = WeatherCoreState.rarePool();
        } else {
            pool = WeatherCoreState.normalPool();
        }

        boolean rarePool = pool == WeatherCoreState.rarePool();
        WeatherCoreState picked = weightedPick(random, level, pool, rarePool, currentSnapshot);
        if (previous != null && picked == previous && pool.size() > 1) {
            WeatherCoreState rerolled = weightedPick(random, level, pool, rarePool, currentSnapshot);
            if (rerolled != previous) {
                picked = rerolled;
            }
        }
        return picked;
    }

    private static WeatherCoreState weightedPick(
            RandomSource random,
            ServerLevel level,
            List<WeatherCoreState> pool,
            boolean rarePool,
            WeatherCoreSnapshot currentSnapshot) {
        List<WeightedState> weightedStates = new ArrayList<>();
        float totalWeight = 0.0F;

        for (WeatherCoreState state : pool) {
            ResourceLocation stateId = WeatherCoreApiBridge.toStateId(state);
            if (!WeatherCoreIntegrationRegistry.canSelect(level, stateId, currentSnapshot)) {
                continue;
            }

            float baseWeight = rarePool ? state.rareWeight() : state.normalWeight();
            float modifiedWeight = WeatherCoreIntegrationRegistry.applyWeightModifiers(level, stateId, baseWeight);
            if (modifiedWeight <= 0.0F) {
                continue;
            }

            weightedStates.add(new WeightedState(state, modifiedWeight));
            totalWeight += modifiedWeight;
        }

        if (weightedStates.isEmpty() || totalWeight <= 0.0F) {
            if (currentSnapshot != null && currentSnapshot.stateId() != null) {
                WeatherCoreState fallbackState = WeatherCoreState.fromId(currentSnapshot.stateId().getPath());
                if (pool.contains(fallbackState)) {
                    return fallbackState;
                }
            }
            return pool.get(random.nextInt(pool.size()));
        }

        float roll = random.nextFloat() * totalWeight;
        float cursor = 0.0F;
        for (WeightedState weightedState : weightedStates) {
            cursor += weightedState.weight();
            if (roll < cursor) {
                return weightedState.state();
            }
        }
        return weightedStates.get(weightedStates.size() - 1).state();
    }

    private static void applyWorldStateStart(
            ServerLevel level,
            AshwakeConfig.Settings settings,
            WeatherCoreSavedData data,
            WeatherCoreState state,
            WeatherCoreState previous) {
        int weatherTicks = Math.max(200, data.getTotalTicks());
        switch (state) {
            case DAWN_BLESSING -> {
                level.setDayTime(1000L + Math.floorMod(level.getDayTime(), 24000L));
                level.setWeatherParameters(weatherTicks, 0, false, false);
            }
            case CLEAR_SKIES, TAILWIND -> level.setWeatherParameters(weatherTicks, 0, false, false);
            case EMBER_WARMTH -> {
                if (level.isRaining()) {
                    level.setWeatherParameters(weatherTicks, 0, false, false);
                }
            }
            case PROSPEROUS_DRIZZLE, DREAD_FOG -> level.setWeatherParameters(0, weatherTicks, true, false);
            case ASHWAKE_STORM -> level.setWeatherParameters(0, weatherTicks, true, true);
            case NIGHTFALL_LOCK -> level.setDayTime(18000L + Math.floorMod(level.getDayTime(), 24000L));
            case GRAVITY_FLUX -> {
                if (level.getRandom().nextBoolean()) {
                    level.setWeatherParameters(weatherTicks, 0, false, false);
                } else {
                    level.setWeatherParameters(0, weatherTicks, true, false);
                }
            }
            case ECLIPSE_MINUTE -> {
                data.setEclipseRestoreDayTime(level.getDayTime());
                level.setDayTime(18000L + Math.floorMod(level.getDayTime(), 24000L));
                level.setWeatherParameters(weatherTicks, 0, false, false);
            }
            case SKYFRACTURE_PULSE -> {
                if (previous == WeatherCoreState.ECLIPSE_MINUTE && data.getEclipseRestoreDayTime() >= 0L) {
                    level.setDayTime(data.getEclipseRestoreDayTime());
                    data.setEclipseRestoreDayTime(-1L);
                }
            }
        }

        if (state.keepsNightLocked()) {
            level.setDayTime(18000L + Math.floorMod(level.getDayTime(), 24000L));
        }
        if (!settings.weatherCoreDisableLightningInSafeRadius() && state == WeatherCoreState.ASHWAKE_STORM) {
            level.setWeatherParameters(0, weatherTicks, true, true);
        }
    }

    private static void applySwitchBurst(ServerLevel level, AshwakeConfig.Settings settings, WeatherCoreState state) {
        AshwakeWorldData worldData = AshwakeWorldData.get(level);
        List<ServerPlayer> players = new ArrayList<>(level.players());
        for (ServerPlayer player : players) {
            boolean reduced = settings.weatherCoreReduceDebuffsInSafeRadius()
                    && isInsideSafeRadius(worldData, player.position(), settings.weatherCoreSafeRadius());

            switch (state) {
                case ECLIPSE_MINUTE -> {
                    applyEffect(player, EFFECT_NIGHT_VISION, 180, 0);
                    applyEffect(player, EFFECT_STRENGTH, 100, 0);
                    if (!reduced) {
                        applyEffect(player, EFFECT_DARKNESS, 40, 0);
                    }
                }
                case SKYFRACTURE_PULSE -> {
                    applyEffect(player, EFFECT_RESISTANCE, 80, 0);
                    applyEffect(player, EFFECT_GLOWING, 40, 0);
                    if (settings.weatherCoreAllowNauseaPulse() && !reduced) {
                        applyEffect(player, EFFECT_NAUSEA, 12, 0);
                    } else if (!reduced) {
                        applyEffect(player, EFFECT_DARKNESS, 20, 0);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void applyStateEffects(
            ServerLevel level,
            AshwakeWorldData worldData,
            AshwakeConfig.Settings settings,
            WeatherCoreState state) {
        long gameTime = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            boolean inSafeRadius = isInsideSafeRadius(worldData, player.position(), settings.weatherCoreSafeRadius());
            boolean sheltered = !level.canSeeSky(player.blockPosition());
            boolean reduceDebuffs = settings.weatherCoreReduceDebuffsInSafeRadius() && (inSafeRadius || sheltered);

            switch (state) {
                case DAWN_BLESSING -> {
                    applyEffect(player, EFFECT_SPEED, 140, 0);
                    applyEffect(player, EFFECT_HASTE, 140, 0);
                    applyEffect(player, EFFECT_REGENERATION, 60, 0);
                }
                case CLEAR_SKIES -> {
                    applyEffect(player, EFFECT_SLOW_FALLING, 100, 0);
                    applyEffect(player, EFFECT_JUMP_BOOST, 120, 0);
                    applyEffect(player, EFFECT_NIGHT_VISION, 160, 0);
                }
                case EMBER_WARMTH -> {
                    applyEffect(player, EFFECT_FIRE_RESISTANCE, 240, 0);
                    applyEffect(player, EFFECT_HASTE, 120, 0);
                    applyEffect(player, EFFECT_SATURATION, 1, 0);
                }
                case PROSPEROUS_DRIZZLE -> {
                    applyEffect(player, EFFECT_LUCK, 240, 0);
                    applyEffect(player, EFFECT_SPEED, 120, 0);
                }
                case TAILWIND -> {
                    applyEffect(player, EFFECT_SPEED, 240, 0);
                    applyEffect(player, EFFECT_JUMP_BOOST, 100, 0);
                    applyEffect(player, EFFECT_SLOW_FALLING, 40, 0);
                }
                case ASHWAKE_STORM -> {
                    applyEffect(player, EFFECT_RESISTANCE, 80, 0);
                    if (!reduceDebuffs) {
                        applyEffect(player, EFFECT_SLOWNESS, 120, 0);
                        applyEffect(player, EFFECT_WEAKNESS, 120, 0);
                        applyEffect(player, EFFECT_MINING_FATIGUE, 100, 0);
                    }
                }
                case NIGHTFALL_LOCK -> {
                    applyEffect(player, EFFECT_RESISTANCE, 120, 0);
                    if (!reduceDebuffs) {
                        applyEffect(player, EFFECT_SLOWNESS, 100, 0);
                        if ((gameTime % (45L * 20L)) == 0L) {
                            applyEffect(player, EFFECT_DARKNESS, 60, 0);
                        }
                    }
                }
                case DREAD_FOG -> {
                    applyEffect(player, EFFECT_LUCK, 120, 0);
                    if (!reduceDebuffs) {
                        applyEffect(player, EFFECT_WEAKNESS, 150, 0);
                        if (settings.weatherCoreAllowBlindnessPulse() && (gameTime % (60L * 20L)) == 0L) {
                            applyEffect(player, EFFECT_BLINDNESS, 20, 0);
                        }
                    } else {
                        applyEffect(player, EFFECT_WEAKNESS, 60, 0);
                    }
                }
                case GRAVITY_FLUX -> {
                    applyEffect(player, EFFECT_JUMP_BOOST, 120, 1);
                    if ((gameTime % (60L * 20L)) < 100L) {
                        applyEffect(player, EFFECT_SLOW_FALLING, 40, 0);
                    }
                }
                case ECLIPSE_MINUTE -> {
                    applyEffect(player, EFFECT_NIGHT_VISION, 180, 0);
                    applyEffect(player, EFFECT_STRENGTH, 100, 0);
                }
                case SKYFRACTURE_PULSE -> {
                    applyEffect(player, EFFECT_RESISTANCE, 100, 0);
                    applyEffect(player, EFFECT_GLOWING, 60, 0);
                }
            }
        }
    }

    private static void applyEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int duration, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, true, true));
    }

    private static boolean isInsideSafeRadius(AshwakeWorldData worldData, Vec3 pos, int safeRadius) {
        BlockPos origin = worldData.getOrigin();
        if (origin == null || origin.equals(BlockPos.ZERO)) {
            return false;
        }
        double dx = pos.x - (origin.getX() + 0.5D);
        double dz = pos.z - (origin.getZ() + 0.5D);
        double d2 = (dx * dx) + (dz * dz);
        return d2 <= ((double) safeRadius * safeRadius);
    }

    private static boolean isSleepLocked(AshwakeConfig.Settings settings, WeatherCoreState state) {
        return settings.weatherCoreSleepLockEnabled() && state.sleepLocked();
    }

    private static void syncStateToAllPlayers(
            ServerLevel level,
            AshwakeWorldData worldData,
            AshwakeConfig.Settings settings,
            WeatherCoreSavedData data,
            WeatherCoreState state) {
        boolean sleepLocked = isSleepLocked(settings, state);
        WeatherCoreNetwork.syncToAll(
                level,
                data,
                sleepLocked,
                player -> computeSyncIntensity(worldData, settings, data, state, player));
    }

    private static float computeSyncIntensity(
            AshwakeWorldData worldData,
            AshwakeConfig.Settings settings,
            WeatherCoreSavedData data,
            WeatherCoreState state,
            ServerPlayer player) {
        float base = switch (state.category()) {
            case GOOD -> 0.58F;
            case BAD -> 0.86F;
            case RARE -> 1.0F;
        };

        if (data.getPhase() == WeatherCorePhase.OMEN) {
            base = Math.min(1.0F, base + 0.08F);
        }

        if (settings.weatherCoreReduceDebuffsInSafeRadius()
                && isInsideSafeRadius(worldData, player.position(), settings.weatherCoreSafeRadius())) {
            base = Math.min(base, 0.35F);
        }

        if (!player.serverLevel().canSeeSky(player.blockPosition())) {
            base *= 0.92F;
        }

        return Mth.clamp(base, 0.0F, 1.0F);
    }

    private static Holder<MobEffect> effect(String path) {
        return BuiltInRegistries.MOB_EFFECT
                .getHolder(ResourceLocation.withDefaultNamespace(path))
                .orElseThrow(() -> new IllegalStateException("Missing effect: " + path));
    }

    private record WeightedState(WeatherCoreState state, float weight) {
    }
}
