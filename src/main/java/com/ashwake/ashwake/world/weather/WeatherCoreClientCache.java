package com.ashwake.ashwake.world.weather;

import com.ashwake.ashwake.network.WeatherCoreSyncPayload;

public final class WeatherCoreClientCache {
    private static final int OMEN_RAMP_TICKS = 45 * 20;
    private static final int SWITCH_BURST_TICKS = 12; // 0.6s

    private static volatile WeatherCoreState state = WeatherCoreState.DAWN_BLESSING;
    private static volatile WeatherCorePhase phase = WeatherCorePhase.ACTIVE;
    private static volatile int ticksRemaining = 0;
    private static volatile int totalTicks = 0;
    private static volatile WeatherCoreState nextState = null;
    private static volatile boolean sleepLocked = false;
    private static volatile float intensity = 1.0F;
    private static volatile int omenElapsedTicks = 0;
    private static volatile int switchBurstTicksRemaining = 0;
    private static volatile WeatherCoreState switchBurstState = WeatherCoreState.DAWN_BLESSING;

    private WeatherCoreClientCache() {
    }

    public static void applySync(WeatherCoreSyncPayload payload) {
        WeatherCoreState previousState = state;
        WeatherCorePhase previousPhase = phase;

        state = WeatherCoreState.fromId(payload.stateId());
        phase = WeatherCorePhase.fromId(payload.phaseId());
        ticksRemaining = Math.max(0, payload.ticksRemaining());
        totalTicks = Math.max(0, payload.totalTicks());
        nextState = payload.nextStateId() == null || payload.nextStateId().isBlank()
                ? null
                : WeatherCoreState.fromId(payload.nextStateId());
        sleepLocked = payload.sleepLocked();
        intensity = Math.max(0.0F, Math.min(1.0F, payload.intensity()));

        if (state != previousState) {
            switchBurstState = state;
            switchBurstTicksRemaining = SWITCH_BURST_TICKS;
        }

        if (phase == WeatherCorePhase.OMEN) {
            if (previousPhase != WeatherCorePhase.OMEN) {
                omenElapsedTicks = 0;
            }
        } else {
            omenElapsedTicks = 0;
        }
    }

    public static void clientTick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
        if (phase == WeatherCorePhase.OMEN) {
            omenElapsedTicks = Math.min(OMEN_RAMP_TICKS, omenElapsedTicks + 1);
        }
        if (switchBurstTicksRemaining > 0) {
            switchBurstTicksRemaining--;
        }
    }

    public static Snapshot snapshot() {
        float omenProgress = phase == WeatherCorePhase.OMEN
                ? Math.min(1.0F, omenElapsedTicks / (float) OMEN_RAMP_TICKS)
                : 0.0F;
        float burstStrength = switchBurstTicksRemaining > 0
                ? switchBurstTicksRemaining / (float) SWITCH_BURST_TICKS
                : 0.0F;

        return new Snapshot(
                state,
                phase,
                ticksRemaining,
                totalTicks,
                nextState,
                sleepLocked,
                intensity,
                omenProgress,
                burstStrength,
                switchBurstState);
    }

    public record Snapshot(
            WeatherCoreState state,
            WeatherCorePhase phase,
            int ticksRemaining,
            int totalTicks,
            WeatherCoreState nextState,
            boolean sleepLocked,
            float intensity,
            float omenProgress,
            float switchBurstStrength,
            WeatherCoreState switchBurstState) {
    }
}
