package com.ashwake.api.event;

import com.ashwake.api.WeatherCoreSnapshot;
import net.neoforged.bus.api.Event;

public final class WeatherCoreClientUpdateEvent extends Event {
    private final WeatherCoreSnapshot snapshot;

    public WeatherCoreClientUpdateEvent(WeatherCoreSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public WeatherCoreSnapshot getSnapshot() {
        return snapshot;
    }
}
