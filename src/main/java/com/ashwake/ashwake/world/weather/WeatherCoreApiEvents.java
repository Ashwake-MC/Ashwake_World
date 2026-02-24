package com.ashwake.ashwake.world.weather;

import com.ashwake.api.WeatherCorePhase;
import com.ashwake.api.WeatherCoreSnapshot;
import com.ashwake.api.event.WeatherCoreClientUpdateEvent;
import com.ashwake.api.event.WeatherCoreOmenStartEvent;
import com.ashwake.api.event.WeatherCorePhaseChangeEvent;
import com.ashwake.api.event.WeatherCoreSleepLockEvent;
import com.ashwake.api.event.WeatherCoreStateChangeEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;

public final class WeatherCoreApiEvents {
    private WeatherCoreApiEvents() {
    }

    public static void fireStateChange(
            ServerLevel level,
            WeatherCoreSnapshot previous,
            WeatherCoreSnapshot current) {
        NeoForge.EVENT_BUS.post(new WeatherCoreStateChangeEvent(level, previous, current));
    }

    public static void fireOmenStart(ServerLevel level, WeatherCoreSnapshot current, int omenTicks) {
        NeoForge.EVENT_BUS.post(new WeatherCoreOmenStartEvent(level, current, omenTicks));
    }

    public static void firePhaseChange(
            ServerLevel level,
            WeatherCorePhase previousPhase,
            WeatherCorePhase newPhase,
            WeatherCoreSnapshot snapshot) {
        NeoForge.EVENT_BUS.post(new WeatherCorePhaseChangeEvent(level, previousPhase, newPhase, snapshot));
    }

    public static void fireSleepLock(ServerLevel level, boolean enabled, WeatherCoreSnapshot snapshot) {
        NeoForge.EVENT_BUS.post(new WeatherCoreSleepLockEvent(level, enabled, snapshot));
    }

    public static void fireClientUpdate(WeatherCoreSnapshot snapshot) {
        NeoForge.EVENT_BUS.post(new WeatherCoreClientUpdateEvent(snapshot));
    }
}
