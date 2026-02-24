package com.ashwake.ashwake.world.weather;

import com.ashwake.api.WeatherCoreSnapshot;
import com.ashwake.api.integration.IGuidanceProvider;
import com.ashwake.api.integration.IWeatherCoreVeto;
import com.ashwake.api.integration.IWeatherCoreWeightModifier;
import com.ashwake.ashwake.AshwakeMod;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public final class WeatherCoreIntegrationRegistry {
    private static final Map<String, IWeatherCoreWeightModifier> WEIGHT_MODIFIERS = new LinkedHashMap<>();
    private static final Map<String, IWeatherCoreVeto> VETOES = new LinkedHashMap<>();
    private static final Map<String, IGuidanceProvider> GUIDANCE_PROVIDERS = new LinkedHashMap<>();

    private WeatherCoreIntegrationRegistry() {
    }

    public static synchronized void registerWeightModifier(String modId, IWeatherCoreWeightModifier modifier) {
        String key = normalizeModId(modId);
        if (modifier == null) {
            throw new IllegalArgumentException("modifier cannot be null");
        }
        WEIGHT_MODIFIERS.put(key, modifier);
    }

    public static synchronized void registerVeto(String modId, IWeatherCoreVeto veto) {
        String key = normalizeModId(modId);
        if (veto == null) {
            throw new IllegalArgumentException("veto cannot be null");
        }
        VETOES.put(key, veto);
    }

    public static synchronized void registerGuidanceProvider(String modId, IGuidanceProvider provider) {
        String key = normalizeModId(modId);
        if (provider == null) {
            throw new IllegalArgumentException("provider cannot be null");
        }
        GUIDANCE_PROVIDERS.put(key, provider);
    }

    public static synchronized float applyWeightModifiers(ServerLevel level, ResourceLocation stateId, float baseWeight) {
        float currentWeight = Math.max(0.0F, baseWeight);
        for (Map.Entry<String, IWeatherCoreWeightModifier> entry : WEIGHT_MODIFIERS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            try {
                currentWeight = Math.max(0.0F, entry.getValue().modifyWeight(level, stateId, currentWeight));
            } catch (Exception exception) {
                AshwakeMod.LOGGER.warn(
                        "WeatherCore weight modifier from {} failed for {}",
                        entry.getKey(),
                        stateId,
                        exception);
            }
        }
        return currentWeight;
    }

    public static synchronized boolean canSelect(
            ServerLevel level,
            ResourceLocation stateId,
            WeatherCoreSnapshot currentSnapshot) {
        WeatherCoreSnapshot snapshot = currentSnapshot;
        if (snapshot == null) {
            snapshot = WeatherCoreApiBridge.getServerSnapshot(level);
            if (snapshot == null) {
                snapshot = WeatherCoreApiBridge.createDisabledSnapshot(level);
            }
        }

        for (Map.Entry<String, IWeatherCoreVeto> entry : VETOES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            try {
                if (!entry.getValue().canSelect(level, stateId, snapshot)) {
                    return false;
                }
            } catch (Exception exception) {
                AshwakeMod.LOGGER.warn("WeatherCore veto from {} failed for {}", entry.getKey(), stateId, exception);
            }
        }
        return true;
    }

    public static synchronized Optional<Component> findGuidanceHint(ServerLevel level) {
        for (Map.Entry<String, IGuidanceProvider> entry : GUIDANCE_PROVIDERS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            try {
                Optional<Component> hint = entry.getValue().getGuidanceHint(level);
                if (hint != null && hint.isPresent()) {
                    return hint;
                }
            } catch (Exception exception) {
                AshwakeMod.LOGGER.warn("Guidance hint provider from {} failed", entry.getKey(), exception);
            }
        }
        return Optional.empty();
    }

    public static synchronized Optional<BlockPos> findGuidanceLocation(ServerLevel level) {
        for (Map.Entry<String, IGuidanceProvider> entry : GUIDANCE_PROVIDERS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            try {
                Optional<BlockPos> location = entry.getValue().getGuidanceLocation(level);
                if (location != null && location.isPresent()) {
                    return Optional.of(location.get().immutable());
                }
            } catch (Exception exception) {
                AshwakeMod.LOGGER.warn("Guidance location provider from {} failed", entry.getKey(), exception);
            }
        }
        return Optional.empty();
    }

    private static String normalizeModId(String modId) {
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("modId cannot be blank");
        }
        return modId.trim().toLowerCase(Locale.ROOT);
    }
}
