package com.ashwake.api;

import com.ashwake.api.integration.IGuidanceProvider;
import com.ashwake.api.integration.IWeatherCoreVeto;
import com.ashwake.api.integration.IWeatherCoreWeightModifier;
import com.ashwake.ashwake.config.AshwakeConfig;
import com.ashwake.ashwake.world.weather.WeatherCoreApiBridge;
import com.ashwake.ashwake.world.weather.WeatherCoreIntegrationRegistry;
import com.ashwake.ashwake.world.weather.WeatherCoreState;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

public final class AshwakeApi {
    public static final int API_VERSION = 1;
    public static final String MOD_ID = "ashwake";

    private static final Set<ResourceLocation> SLEEP_LOCK_STATES = Arrays.stream(WeatherCoreState.values())
            .filter(WeatherCoreState::sleepLocked)
            .map(WeatherCoreApiBridge::toStateId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    private static final AshwakeConfigView CONFIG_VIEW = new AshwakeConfigView() {
        @Override
        public int getCycleMinutes() {
            return AshwakeConfig.snapshot().weatherCoreCycleMinutes();
        }

        @Override
        public int getOmenSeconds() {
            return AshwakeConfig.snapshot().weatherCoreOmenSeconds();
        }

        @Override
        public int getSafeRadius() {
            return AshwakeConfig.snapshot().weatherCoreSafeRadius();
        }

        @Override
        public boolean isSleepLockEnabled() {
            return AshwakeConfig.snapshot().weatherCoreSleepLockEnabled();
        }

        @Override
        public Set<ResourceLocation> getSleepLockStates() {
            return Set.copyOf(SLEEP_LOCK_STATES);
        }

        @Override
        public boolean isSystemEnabled() {
            return AshwakeConfig.snapshot().enableWorldCoreOrb();
        }
    };

    private AshwakeApi() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static int getApiVersion() {
        return API_VERSION;
    }

    public static WeatherCoreSnapshot getSnapshot(ServerLevel level) {
        return WeatherCoreApiBridge.getServerSnapshot(level);
    }

    public static WeatherCoreSnapshot getClientSnapshot() {
        return WeatherCoreApiBridge.getClientSnapshot();
    }

    public static AshwakeConfigView getConfigView() {
        return CONFIG_VIEW;
    }

    public static boolean isOmen(ServerLevel level) {
        WeatherCoreSnapshot snapshot = getSnapshot(level);
        return snapshot != null && snapshot.phase() == WeatherCorePhase.OMEN;
    }

    public static boolean isSleepDisabled(ServerLevel level) {
        WeatherCoreSnapshot snapshot = getSnapshot(level);
        return snapshot != null && snapshot.sleepDisabled();
    }

    public static ResourceLocation getStateId(ServerLevel level) {
        WeatherCoreSnapshot snapshot = getSnapshot(level);
        return snapshot == null ? null : snapshot.stateId();
    }

    public static int getTicksUntilNextChange(ServerLevel level) {
        WeatherCoreSnapshot snapshot = getSnapshot(level);
        return snapshot == null ? -1 : snapshot.ticksUntilNextChange();
    }

    public static void registerWeightModifier(String modId, IWeatherCoreWeightModifier modifier) {
        WeatherCoreIntegrationRegistry.registerWeightModifier(modId, modifier);
    }

    public static void registerVeto(String modId, IWeatherCoreVeto veto) {
        WeatherCoreIntegrationRegistry.registerVeto(modId, veto);
    }

    public static void registerGuidanceProvider(String modId, IGuidanceProvider provider) {
        WeatherCoreIntegrationRegistry.registerGuidanceProvider(modId, provider);
    }

    public static Optional<Component> getGuidanceHint(ServerLevel level) {
        return WeatherCoreIntegrationRegistry.findGuidanceHint(level);
    }

    public static Optional<BlockPos> getGuidanceLocation(ServerLevel level) {
        return WeatherCoreIntegrationRegistry.findGuidanceLocation(level);
    }
}
