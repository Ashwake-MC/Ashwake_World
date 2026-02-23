package com.ashwake.ashwake.world.weather;

import java.util.Locale;

public enum WeatherCorePhase {
    ACTIVE("active"),
    OMEN("omen");

    private final String id;

    WeatherCorePhase(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static WeatherCorePhase fromId(String id) {
        if (id == null || id.isBlank()) {
            return ACTIVE;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        for (WeatherCorePhase phase : values()) {
            if (phase.id.equals(normalized)) {
                return phase;
            }
        }
        return ACTIVE;
    }
}
