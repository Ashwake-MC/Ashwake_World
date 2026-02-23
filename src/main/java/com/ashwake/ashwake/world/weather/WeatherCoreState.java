package com.ashwake.ashwake.world.weather;

import java.util.List;
import java.util.Locale;

public enum WeatherCoreState {
    DAWN_BLESSING("dawn_blessing", Category.GOOD, 12, 0, false, false, -1),
    CLEAR_SKIES("clear_skies", Category.GOOD, 12, 0, false, false, -1),
    EMBER_WARMTH("ember_warmth", Category.GOOD, 11, 0, false, false, -1),
    PROSPEROUS_DRIZZLE("prosperous_drizzle", Category.GOOD, 10, 0, false, false, -1),
    TAILWIND("tailwind", Category.GOOD, 10, 0, false, false, -1),

    ASHWAKE_STORM("ashwake_storm", Category.BAD, 12, 0, false, false, -1),
    NIGHTFALL_LOCK("nightfall_lock", Category.BAD, 12, 0, true, true, -1),
    DREAD_FOG("dread_fog", Category.BAD, 11, 0, false, false, -1),
    GRAVITY_FLUX("gravity_flux", Category.BAD, 10, 0, false, false, -1),

    ECLIPSE_MINUTE("eclipse_minute", Category.RARE, 0, 70, true, true, 90 * 20),
    SKYFRACTURE_PULSE("skyfracture_pulse", Category.RARE, 0, 30, false, false, 60 * 20);

    public enum Category {
        GOOD,
        BAD,
        RARE
    }

    private static final List<WeatherCoreState> NORMAL_POOL = List.of(
            DAWN_BLESSING,
            CLEAR_SKIES,
            EMBER_WARMTH,
            PROSPEROUS_DRIZZLE,
            TAILWIND,
            ASHWAKE_STORM,
            NIGHTFALL_LOCK,
            DREAD_FOG,
            GRAVITY_FLUX);
    private static final List<WeatherCoreState> RARE_POOL = List.of(ECLIPSE_MINUTE, SKYFRACTURE_PULSE);

    private final String id;
    private final Category category;
    private final int normalWeight;
    private final int rareWeight;
    private final boolean sleepLocked;
    private final boolean keepsNightLocked;
    private final int durationOverrideTicks;

    WeatherCoreState(
            String id,
            Category category,
            int normalWeight,
            int rareWeight,
            boolean sleepLocked,
            boolean keepsNightLocked,
            int durationOverrideTicks) {
        this.id = id;
        this.category = category;
        this.normalWeight = normalWeight;
        this.rareWeight = rareWeight;
        this.sleepLocked = sleepLocked;
        this.keepsNightLocked = keepsNightLocked;
        this.durationOverrideTicks = durationOverrideTicks;
    }

    public String id() {
        return id;
    }

    public Category category() {
        return category;
    }

    public int normalWeight() {
        return normalWeight;
    }

    public int rareWeight() {
        return rareWeight;
    }

    public boolean sleepLocked() {
        return sleepLocked;
    }

    public boolean keepsNightLocked() {
        return keepsNightLocked;
    }

    public int durationOverrideTicks() {
        return durationOverrideTicks;
    }

    public boolean isRarePoolState() {
        return category == Category.RARE;
    }

    public String translationKey() {
        return "ashwake.weather.state." + id;
    }

    public static WeatherCoreState fromId(String id) {
        if (id == null || id.isBlank()) {
            return DAWN_BLESSING;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        for (WeatherCoreState state : values()) {
            if (state.id.equals(normalized)) {
                return state;
            }
        }
        return DAWN_BLESSING;
    }

    public static List<WeatherCoreState> normalPool() {
        return NORMAL_POOL;
    }

    public static List<WeatherCoreState> rarePool() {
        return RARE_POOL;
    }
}
