package com.ashwake.api.event;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

public abstract class WeatherCoreEvent extends Event {
    private final ServerLevel level;

    protected WeatherCoreEvent(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel getLevel() {
        return level;
    }
}
