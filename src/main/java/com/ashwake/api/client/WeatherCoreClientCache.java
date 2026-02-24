package com.ashwake.api.client;

import com.ashwake.api.WeatherCoreSnapshot;
import com.ashwake.ashwake.world.weather.WeatherCoreApiBridge;

public final class WeatherCoreClientCache {
    private WeatherCoreClientCache() {
    }

    public static WeatherCoreSnapshot get() {
        return WeatherCoreApiBridge.getClientSnapshot();
    }
}
