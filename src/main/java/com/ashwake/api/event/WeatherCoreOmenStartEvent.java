package com.ashwake.api.event;

import com.ashwake.api.WeatherCoreSnapshot;
import net.minecraft.server.level.ServerLevel;

public final class WeatherCoreOmenStartEvent extends WeatherCoreEvent {
    private final WeatherCoreSnapshot current;
    private final int omenTicks;

    public WeatherCoreOmenStartEvent(ServerLevel level, WeatherCoreSnapshot current, int omenTicks) {
        super(level);
        this.current = current;
        this.omenTicks = omenTicks;
    }

    public WeatherCoreSnapshot getCurrent() {
        return current;
    }

    public int getOmenTicks() {
        return omenTicks;
    }
}
