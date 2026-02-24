package com.ashwake.ashwake.world.weather;

import com.ashwake.api.WeatherCorePhase;
import com.ashwake.api.WeatherCorePolarity;
import com.ashwake.api.WeatherCoreSnapshot;
import com.ashwake.ashwake.AshwakeMod;
import com.ashwake.ashwake.config.AshwakeConfig;
import com.ashwake.ashwake.world.AshwakeWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class WeatherCoreApiBridge {
    private WeatherCoreApiBridge() {
    }

    public static WeatherCoreSnapshot getServerSnapshot(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) {
            return null;
        }

        AshwakeConfig.Settings settings = AshwakeConfig.snapshot();
        if (!settings.enableWorldCoreOrb()) {
            return null;
        }

        AshwakeWorldData worldData = AshwakeWorldData.get(level);
        if (!worldData.isPlaced()) {
            return null;
        }

        WeatherCoreSavedData data = WeatherCoreSavedData.get(level);
        if (!data.isInitialized()) {
            return null;
        }

        return createServerSnapshot(level, worldData, settings, data, -1.0F);
    }

    public static WeatherCoreSnapshot createServerSnapshot(
            ServerLevel level,
            AshwakeWorldData worldData,
            AshwakeConfig.Settings settings,
            WeatherCoreSavedData data,
            float intensityForPlayer) {
        WeatherCoreState current = data.getCurrentState();
        WeatherCoreState next = data.getNextState();
        return new WeatherCoreSnapshot(
                toStateId(current),
                toPolarity(current),
                toApiPhase(data.getPhase()),
                data.getTicksRemaining(),
                data.getTicksRemaining(),
                settings.weatherCoreSleepLockEnabled() && current.sleepLocked(),
                settings.weatherCoreSafeRadius(),
                resolveHubCenter(worldData),
                settings.weatherCoreDisableLightningInSafeRadius(),
                level.getGameTime(),
                level.isRaining(),
                level.isThundering(),
                next == null ? null : toStateId(next),
                intensityForPlayer);
    }

    public static WeatherCoreSnapshot getClientSnapshot() {
        WeatherCoreClientCache.Snapshot snapshot = WeatherCoreClientCache.snapshot();
        if (snapshot.totalTicks() <= 0) {
            return null;
        }

        WeatherCoreState current = snapshot.state();
        WeatherCoreState next = snapshot.nextState();
        return new WeatherCoreSnapshot(
                toStateId(current),
                toPolarity(current),
                toApiPhase(snapshot.phase()),
                snapshot.ticksRemaining(),
                snapshot.ticksRemaining(),
                snapshot.sleepLocked(),
                snapshot.safeRadiusBlocks(),
                snapshot.hubCenter(),
                snapshot.lightningSuppressedInSafeZone(),
                snapshot.worldTime(),
                snapshot.isRaining(),
                snapshot.isThundering(),
                next == null ? null : toStateId(next),
                snapshot.intensity());
    }

    public static WeatherCoreSnapshot createDisabledSnapshot(ServerLevel level) {
        AshwakeConfig.Settings settings = AshwakeConfig.snapshot();
        return new WeatherCoreSnapshot(
                toStateId(WeatherCoreState.DAWN_BLESSING),
                WeatherCorePolarity.GOOD,
                WeatherCorePhase.DISABLED,
                0,
                0,
                false,
                settings.weatherCoreSafeRadius(),
                BlockPos.ZERO,
                settings.weatherCoreDisableLightningInSafeRadius(),
                level.getGameTime(),
                level.isRaining(),
                level.isThundering(),
                null,
                -1.0F);
    }

    public static BlockPos resolveHubCenter(AshwakeWorldData worldData) {
        BlockPos worldCore = worldData.getWorldCorePos();
        if (worldCore != null && !worldCore.equals(BlockPos.ZERO)) {
            return worldCore.immutable();
        }
        BlockPos origin = worldData.getOrigin();
        if (origin != null && !origin.equals(BlockPos.ZERO)) {
            return origin.immutable();
        }
        return BlockPos.ZERO;
    }

    public static ResourceLocation toStateId(WeatherCoreState state) {
        return toStateId(state.id());
    }

    public static ResourceLocation toStateId(String statePath) {
        return ResourceLocation.fromNamespaceAndPath(AshwakeMod.MODID, statePath);
    }

    public static WeatherCorePolarity toPolarity(WeatherCoreState state) {
        return switch (state.category()) {
            case GOOD -> WeatherCorePolarity.GOOD;
            case BAD -> WeatherCorePolarity.BAD;
            case RARE -> WeatherCorePolarity.RARE;
        };
    }

    public static WeatherCorePhase toApiPhase(com.ashwake.ashwake.world.weather.WeatherCorePhase phase) {
        return switch (phase) {
            case ACTIVE -> WeatherCorePhase.ACTIVE;
            case OMEN -> WeatherCorePhase.OMEN;
        };
    }
}
