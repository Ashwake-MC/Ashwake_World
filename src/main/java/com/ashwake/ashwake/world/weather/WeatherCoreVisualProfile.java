package com.ashwake.ashwake.world.weather;

import java.util.EnumMap;

public record WeatherCoreVisualProfile(
        int skyTint,
        int fogTint,
        int overlayColor,
        float overlayAlpha,
        float fogDistanceScale,
        float cloudDarkness,
        float starScale,
        float cameraWobbleDegrees,
        ParticleTheme particleTheme,
        SoundTheme soundTheme) {
    private static final EnumMap<WeatherCoreState, WeatherCoreVisualProfile> BY_STATE =
            new EnumMap<>(WeatherCoreState.class);

    static {
        BY_STATE.put(
                WeatherCoreState.DAWN_BLESSING,
                new WeatherCoreVisualProfile(
                        0xF6C98A,
                        0xE9B878,
                        0xD98A3A,
                        0.08F,
                        0.96F,
                        0.28F,
                        0.92F,
                        0.0F,
                        ParticleTheme.WARM_EMBERS,
                        SoundTheme.CALM_HUM));
        BY_STATE.put(
                WeatherCoreState.CLEAR_SKIES,
                new WeatherCoreVisualProfile(
                        0xB8D3F5,
                        0xC6DBF2,
                        0x9FB8D6,
                        0.03F,
                        1.00F,
                        0.10F,
                        0.85F,
                        0.0F,
                        ParticleTheme.CLEAR_MOTES,
                        SoundTheme.NEAR_SILENT));
        BY_STATE.put(
                WeatherCoreState.EMBER_WARMTH,
                new WeatherCoreVisualProfile(
                        0xC88454,
                        0xB76D45,
                        0xB86034,
                        0.10F,
                        0.90F,
                        0.36F,
                        0.90F,
                        0.0F,
                        ParticleTheme.COPPER_SWIRL,
                        SoundTheme.FORGE_CRACKLE));
        BY_STATE.put(
                WeatherCoreState.PROSPEROUS_DRIZZLE,
                new WeatherCoreVisualProfile(
                        0xAFC3DE,
                        0xB5B9BC,
                        0x97A7BE,
                        0.09F,
                        0.88F,
                        0.42F,
                        0.96F,
                        0.0F,
                        ParticleTheme.MIST_MOTES,
                        SoundTheme.SOFT_RAIN));
        BY_STATE.put(
                WeatherCoreState.TAILWIND,
                new WeatherCoreVisualProfile(
                        0x98C9DF,
                        0x9FC8D9,
                        0x7CB0CA,
                        0.06F,
                        0.94F,
                        0.22F,
                        0.88F,
                        0.0F,
                        ParticleTheme.WIND_STREAKS,
                        SoundTheme.WHOOSH_CHIMES));

        BY_STATE.put(
                WeatherCoreState.ASHWAKE_STORM,
                new WeatherCoreVisualProfile(
                        0x4D4A48,
                        0x56504B,
                        0x2B201A,
                        0.20F,
                        0.62F,
                        0.84F,
                        1.06F,
                        0.0F,
                        ParticleTheme.ASH_FLECKS,
                        SoundTheme.STORM_RUMBLE));
        BY_STATE.put(
                WeatherCoreState.NIGHTFALL_LOCK,
                new WeatherCoreVisualProfile(
                        0x2E3A61,
                        0x303A59,
                        0x1A1A2C,
                        0.18F,
                        0.74F,
                        0.70F,
                        1.34F,
                        0.0F,
                        ParticleTheme.DARK_STARS,
                        SoundTheme.REST_DENIED));
        BY_STATE.put(
                WeatherCoreState.DREAD_FOG,
                new WeatherCoreVisualProfile(
                        0x5A6359,
                        0x616961,
                        0x2F3B33,
                        0.17F,
                        0.58F,
                        0.78F,
                        1.02F,
                        0.0F,
                        ParticleTheme.DREAD_MOTES,
                        SoundTheme.MUFFLED_WIND));
        BY_STATE.put(
                WeatherCoreState.GRAVITY_FLUX,
                new WeatherCoreVisualProfile(
                        0x6A5B7E,
                        0x705E86,
                        0x33273D,
                        0.15F,
                        0.82F,
                        0.58F,
                        1.08F,
                        1.05F,
                        ParticleTheme.ORBIT_SPARKS,
                        SoundTheme.WARBLE_HUM));

        BY_STATE.put(
                WeatherCoreState.ECLIPSE_MINUTE,
                new WeatherCoreVisualProfile(
                        0x222A4A,
                        0x2B2F49,
                        0x171423,
                        0.24F,
                        0.56F,
                        0.88F,
                        1.50F,
                        0.0F,
                        ParticleTheme.ECLIPSE_VIGNETTE,
                        SoundTheme.DEEP_BASS));
        BY_STATE.put(
                WeatherCoreState.SKYFRACTURE_PULSE,
                new WeatherCoreVisualProfile(
                        0x8EB5DE,
                        0x86ABC9,
                        0x283F57,
                        0.20F,
                        0.68F,
                        0.62F,
                        1.24F,
                        0.35F,
                        ParticleTheme.STATIC_SPARKS,
                        SoundTheme.SHARP_RING));
    }

    public static WeatherCoreVisualProfile forState(WeatherCoreState state) {
        WeatherCoreVisualProfile profile = BY_STATE.get(state);
        if (profile != null) {
            return profile;
        }
        return BY_STATE.get(WeatherCoreState.DAWN_BLESSING);
    }

    public enum ParticleTheme {
        WARM_EMBERS,
        CLEAR_MOTES,
        COPPER_SWIRL,
        MIST_MOTES,
        WIND_STREAKS,
        ASH_FLECKS,
        DARK_STARS,
        DREAD_MOTES,
        ORBIT_SPARKS,
        ECLIPSE_VIGNETTE,
        STATIC_SPARKS
    }

    public enum SoundTheme {
        CALM_HUM,
        NEAR_SILENT,
        FORGE_CRACKLE,
        SOFT_RAIN,
        WHOOSH_CHIMES,
        STORM_RUMBLE,
        REST_DENIED,
        MUFFLED_WIND,
        WARBLE_HUM,
        DEEP_BASS,
        SHARP_RING
    }
}
