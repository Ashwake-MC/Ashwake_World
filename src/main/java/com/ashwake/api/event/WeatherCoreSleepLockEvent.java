package com.ashwake.api.event;

import com.ashwake.api.WeatherCoreSnapshot;
import net.minecraft.server.level.ServerLevel;

public final class WeatherCoreSleepLockEvent extends WeatherCoreEvent {
    private final boolean enabled;
    private final WeatherCoreSnapshot snapshot;

    public WeatherCoreSleepLockEvent(ServerLevel level, boolean enabled, WeatherCoreSnapshot snapshot) {
        super(level);
        this.enabled = enabled;
        this.snapshot = snapshot;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public WeatherCoreSnapshot getSnapshot() {
        return snapshot;
    }
}
