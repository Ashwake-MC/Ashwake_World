package com.ashwake.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record WeatherCoreSnapshot(
        ResourceLocation stateId,
        WeatherCorePolarity polarity,
        WeatherCorePhase phase,
        int ticksRemainingInPhase,
        int ticksUntilNextChange,
        boolean sleepDisabled,
        int safeRadiusBlocks,
        BlockPos hubCenter,
        boolean lightningSuppressedInSafeZone,
        long worldTime,
        boolean isRaining,
        boolean isThundering,
        ResourceLocation nextStatePreview,
        float intensityForPlayer) {

    public WeatherCoreSnapshot {
        ticksRemainingInPhase = Math.max(0, ticksRemainingInPhase);
        ticksUntilNextChange = Math.max(0, ticksUntilNextChange);
        safeRadiusBlocks = Math.max(0, safeRadiusBlocks);
        hubCenter = hubCenter == null ? BlockPos.ZERO : hubCenter.immutable();
    }
}
