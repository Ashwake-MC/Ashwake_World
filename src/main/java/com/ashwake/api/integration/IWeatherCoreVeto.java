package com.ashwake.api.integration;

import com.ashwake.api.WeatherCoreSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

@FunctionalInterface
public interface IWeatherCoreVeto {
    boolean canSelect(ServerLevel level, ResourceLocation stateId, WeatherCoreSnapshot current);
}
