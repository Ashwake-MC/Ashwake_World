package com.ashwake.api.event;

import com.ashwake.api.WeatherCorePhase;
import com.ashwake.api.WeatherCoreSnapshot;
import net.minecraft.server.level.ServerLevel;

public final class WeatherCorePhaseChangeEvent extends WeatherCoreEvent {
    private final WeatherCorePhase previousPhase;
    private final WeatherCorePhase newPhase;
    private final WeatherCoreSnapshot snapshot;

    public WeatherCorePhaseChangeEvent(
            ServerLevel level,
            WeatherCorePhase previousPhase,
            WeatherCorePhase newPhase,
            WeatherCoreSnapshot snapshot) {
        super(level);
        this.previousPhase = previousPhase;
        this.newPhase = newPhase;
        this.snapshot = snapshot;
    }

    public WeatherCorePhase getPreviousPhase() {
        return previousPhase;
    }

    public WeatherCorePhase getNewPhase() {
        return newPhase;
    }

    public WeatherCoreSnapshot getSnapshot() {
        return snapshot;
    }
}
