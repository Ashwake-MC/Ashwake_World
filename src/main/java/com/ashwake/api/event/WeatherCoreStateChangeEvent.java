package com.ashwake.api.event;

import com.ashwake.api.WeatherCoreSnapshot;
import net.minecraft.server.level.ServerLevel;

public final class WeatherCoreStateChangeEvent extends WeatherCoreEvent {
    private final WeatherCoreSnapshot previous;
    private final WeatherCoreSnapshot current;

    public WeatherCoreStateChangeEvent(ServerLevel level, WeatherCoreSnapshot previous, WeatherCoreSnapshot current) {
        super(level);
        this.previous = previous;
        this.current = current;
    }

    public WeatherCoreSnapshot getPrevious() {
        return previous;
    }

    public WeatherCoreSnapshot getCurrent() {
        return current;
    }
}
