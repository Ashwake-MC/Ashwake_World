package com.ashwake.api.integration;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

@FunctionalInterface
public interface IWeatherCoreWeightModifier {
    float modifyWeight(ServerLevel level, ResourceLocation stateId, float baseWeight);
}
