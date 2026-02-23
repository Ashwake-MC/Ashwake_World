package com.ashwake.ashwake.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AshwakeConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SEARCH_RADIUS = BUILDER
            .comment("Placement search radius around vanilla spawn.")
            .defineInRange("searchRadius", 2500, 1500, 4000);

    public static final ModConfigSpec.IntValue SAMPLE_STEP = BUILDER
            .comment("Distance between placement samples when searching in rings.")
            .defineInRange("sampleStep", 64, 24, 256);

    public static final ModConfigSpec.BooleanValue REASSERT_SPAWN = BUILDER
            .comment("Re-apply saved volcano spawn on each server start.")
            .define("reassertSpawn", true);

    public static final ModConfigSpec.IntValue OUTER_RADIUS = BUILDER
            .comment("Outer volcano radius.")
            .defineInRange("outerRadius", 47, 30, 96);

    public static final ModConfigSpec.IntValue HEIGHT = BUILDER
            .comment("Volcano height from base plane.")
            .defineInRange("height", 52, 24, 128);

    public static final ModConfigSpec.IntValue CALDERA_RADIUS_TOP = BUILDER
            .comment("Top crater radius.")
            .defineInRange("calderaRadiusTop", 14, 6, 32);

    public static final ModConfigSpec.DoubleValue SLOPE_ALPHA = BUILDER
            .comment("Cone profile exponent.")
            .defineInRange("slopeAlpha", 1.25D, 0.5D, 3.5D);

    public static final ModConfigSpec.DoubleValue NOISE_AMPLITUDE = BUILDER
            .comment("Exterior shape noise amplitude.")
            .defineInRange("noiseAmplitude", 3.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.IntValue HUB_CENTER_OFFSET = BUILDER
            .comment("Hub center Y offset from volcano origin.")
            .defineInRange("hubCenterOffset", -10, -40, 0);

    public static final ModConfigSpec.IntValue HUB_RADIUS = BUILDER
            .comment("Hub cavern horizontal radius.")
            .defineInRange("hubRadius", 21, 10, 40);

    public static final ModConfigSpec.IntValue HUB_HEIGHT = BUILDER
            .comment("Hub cavern vertical size.")
            .defineInRange("hubHeight", 20, 8, 48);

    public static final ModConfigSpec.IntValue RING_RADIUS = BUILDER
            .comment("Ring corridor radius.")
            .defineInRange("ringRadius", 17, 8, 36);

    public static final ModConfigSpec.IntValue RING_WIDTH = BUILDER
            .comment("Ring corridor radius thickness.")
            .defineInRange("ringWidth", 3, 2, 6);

    public static final ModConfigSpec.IntValue ENTRANCE_WIDTH = BUILDER
            .comment("Main entrance width.")
            .defineInRange("entranceWidth", 5, 3, 9);

    public static final ModConfigSpec.IntValue ENTRANCE_HEIGHT = BUILDER
            .comment("Main entrance height.")
            .defineInRange("entranceHeight", 5, 3, 9);

    public static final ModConfigSpec.IntValue STALL_COUNT = BUILDER
            .comment("Number of market stalls around the ring.")
            .defineInRange("stallCount", 8, 0, 16);

    public static final ModConfigSpec.BooleanValue ENABLE_SECRET_TUNNEL = BUILDER
            .comment("Generate secret tunnel and relic room.")
            .define("enableSecretTunnel", true);

    public static final ModConfigSpec.BooleanValue ENABLE_RELIC_ROOM = BUILDER
            .comment("Generate relic room at the end of the secret tunnel.")
            .define("enableRelicRoom", true);

    public static final ModConfigSpec.IntValue PLATFORM_SIZE = BUILDER
            .comment("Spawn platform size.")
            .defineInRange("platformSize", 5, 3, 9);

    public static final ModConfigSpec.IntValue CHUNK_LOAD_RADIUS = BUILDER
            .comment("Chunk radius to force-load around the origin while building.")
            .defineInRange("chunkLoadRadius", 6, 3, 12);

    public static final ModConfigSpec.IntValue VENT_MIN = BUILDER
            .comment("Minimum vent count.")
            .defineInRange("ventMin", 2, 0, 6);

    public static final ModConfigSpec.IntValue VENT_MAX = BUILDER
            .comment("Maximum vent count.")
            .defineInRange("ventMax", 4, 0, 8);

    public static final ModConfigSpec.IntValue SURFACE_MIN_Y = BUILDER
            .comment("Reject placement candidates below this surface Y.")
            .defineInRange("surfaceMinY", 55, -64, 320);

    public static final ModConfigSpec.IntValue SLOPE_SAMPLE_RADIUS = BUILDER
            .comment("Radius used to measure terrain slope during placement scoring.")
            .defineInRange("slopeSampleRadius", 32, 8, 96);

    public static final ModConfigSpec.IntValue SLOPE_VARIANCE_LIMIT = BUILDER
            .comment("Height variance threshold where slope penalty applies.")
            .defineInRange("slopeVarianceLimit", 18, 4, 64);

    public static final ModConfigSpec.BooleanValue ENABLE_WORLD_CORE_ORB = BUILDER
            .comment("Spawn the Ashwake World Core orb entity in the volcano hub.")
            .define("enableWorldCoreOrb", true);

    public static final ModConfigSpec.IntValue WORLD_CORE_PARTICLE_RATE = BUILDER
            .comment("Ticks between World Core particle emissions (lower = more particles).")
            .defineInRange("worldCoreParticleRate", 3, 1, 20);

    public static final ModConfigSpec.BooleanValue WORLD_CORE_SOUND_ENABLED = BUILDER
            .comment("Play ambient hum sounds from the World Core.")
            .define("worldCoreSoundEnabled", true);

    public static final ModConfigSpec.IntValue WEATHER_CORE_CYCLE_MINUTES = BUILDER
            .comment("Weather Core cycle length in minutes.")
            .defineInRange("weatherCoreCycleMinutes", 10, 1, 60);

    public static final ModConfigSpec.IntValue WEATHER_CORE_OMEN_SECONDS = BUILDER
            .comment("Weather Core omen warning duration in seconds.")
            .defineInRange("weatherCoreOmenSeconds", 45, 5, 180);

    public static final ModConfigSpec.IntValue WEATHER_CORE_SAFE_RADIUS = BUILDER
            .comment("Safe radius around hub center where bad effects are reduced.")
            .defineInRange("weatherCoreSafeRadius", 128, 16, 512);

    public static final ModConfigSpec.IntValue WEATHER_CORE_RARE_GATE_PERCENT = BUILDER
            .comment("Chance to roll from rare Weather Core states each cycle.")
            .defineInRange("weatherCoreRareGatePercent", 5, 0, 100);

    public static final ModConfigSpec.BooleanValue WEATHER_CORE_SLEEP_LOCK_ENABLED = BUILDER
            .comment("Allow Weather Core states to deny sleeping.")
            .define("weatherCoreSleepLockEnabled", true);

    public static final ModConfigSpec.BooleanValue WEATHER_CORE_DISABLE_LIGHTNING_SAFE_RADIUS = BUILDER
            .comment("Disable lightning spawns inside Weather Core safe radius during storms.")
            .define("weatherCoreDisableLightningInSafeRadius", true);

    public static final ModConfigSpec.BooleanValue WEATHER_CORE_REDUCE_DEBUFFS_SAFE_RADIUS = BUILDER
            .comment("Reduce negative Weather Core effects in safe radius and sheltered spaces.")
            .define("weatherCoreReduceDebuffsInSafeRadius", true);

    public static final ModConfigSpec.BooleanValue WEATHER_CORE_ALLOW_BLINDNESS_PULSE = BUILDER
            .comment("Allow Dread Fog blindness pulses.")
            .define("weatherCoreAllowBlindnessPulse", false);

    public static final ModConfigSpec.BooleanValue WEATHER_CORE_ALLOW_NAUSEA_PULSE = BUILDER
            .comment("Allow Skyfracture nausea pulses.")
            .define("weatherCoreAllowNauseaPulse", false);

    public static final ModConfigSpec.BooleanValue INTRO_POPUP_ENABLED = BUILDER
            .comment("Enable first-join Ashwake story intro popups.")
            .define("introPopupEnabled", true);

    public static final ModConfigSpec.IntValue INTRO_POPUP_VERSION = BUILDER
            .comment("Intro content version. Increase to re-show intro for players on next join.")
            .defineInRange("introPopupVersion", 1, 1, 999);

    public static final ModConfigSpec.IntValue INTRO_POPUP_SHOW_DELAY_TICKS = BUILDER
            .comment("Delay before opening intro screen after join (client ticks).")
            .defineInRange("introPopupShowDelayTicks", 50, 20, 200);

    public static final ModConfigSpec.BooleanValue INTRO_POPUP_ALLOW_DONT_SHOW_AGAIN = BUILDER
            .comment("Allow the intro screen to show 'Don't show again' checkbox.")
            .define("introPopupAllowDontShowAgain", true);

    public static final ModConfigSpec.BooleanValue INTRO_POPUP_ALLOW_LEARN_MORE = BUILDER
            .comment("Allow intro screen to open the Learn More page.")
            .define("introPopupAllowLearnMore", true);

    public static final ModConfigSpec.BooleanValue INTRO_POPUP_ALLOW_DISABLE_BUTTON = BUILDER
            .comment("Allow intro screen to show 'Disable tutorial popups' button.")
            .define("introPopupAllowDisableButton", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private AshwakeConfig() {
    }

    public static Settings snapshot() {
        return new Settings(
                SEARCH_RADIUS.get(),
                SAMPLE_STEP.get(),
                REASSERT_SPAWN.get(),
                OUTER_RADIUS.get(),
                HEIGHT.get(),
                CALDERA_RADIUS_TOP.get(),
                SLOPE_ALPHA.get(),
                NOISE_AMPLITUDE.get(),
                HUB_CENTER_OFFSET.get(),
                HUB_RADIUS.get(),
                HUB_HEIGHT.get(),
                RING_RADIUS.get(),
                RING_WIDTH.get(),
                ENTRANCE_WIDTH.get(),
                ENTRANCE_HEIGHT.get(),
                STALL_COUNT.get(),
                ENABLE_SECRET_TUNNEL.get(),
                ENABLE_RELIC_ROOM.get(),
                PLATFORM_SIZE.get(),
                CHUNK_LOAD_RADIUS.get(),
                VENT_MIN.get(),
                VENT_MAX.get(),
                SURFACE_MIN_Y.get(),
                SLOPE_SAMPLE_RADIUS.get(),
                SLOPE_VARIANCE_LIMIT.get(),
                ENABLE_WORLD_CORE_ORB.get(),
                WORLD_CORE_PARTICLE_RATE.get(),
                WORLD_CORE_SOUND_ENABLED.get(),
                WEATHER_CORE_CYCLE_MINUTES.get(),
                WEATHER_CORE_OMEN_SECONDS.get(),
                WEATHER_CORE_SAFE_RADIUS.get(),
                WEATHER_CORE_RARE_GATE_PERCENT.get(),
                WEATHER_CORE_SLEEP_LOCK_ENABLED.get(),
                WEATHER_CORE_DISABLE_LIGHTNING_SAFE_RADIUS.get(),
                WEATHER_CORE_REDUCE_DEBUFFS_SAFE_RADIUS.get(),
                WEATHER_CORE_ALLOW_BLINDNESS_PULSE.get(),
                WEATHER_CORE_ALLOW_NAUSEA_PULSE.get(),
                INTRO_POPUP_ENABLED.get(),
                INTRO_POPUP_VERSION.get(),
                INTRO_POPUP_SHOW_DELAY_TICKS.get(),
                INTRO_POPUP_ALLOW_DONT_SHOW_AGAIN.get(),
                INTRO_POPUP_ALLOW_LEARN_MORE.get(),
                INTRO_POPUP_ALLOW_DISABLE_BUTTON.get());
    }

    public record Settings(
            int searchRadius,
            int sampleStep,
            boolean reassertSpawn,
            int outerRadius,
            int height,
            int calderaRadiusTop,
            double slopeAlpha,
            double noiseAmplitude,
            int hubCenterOffset,
            int hubRadius,
            int hubHeight,
            int ringRadius,
            int ringWidth,
            int entranceWidth,
            int entranceHeight,
            int stallCount,
            boolean enableSecretTunnel,
            boolean enableRelicRoom,
            int platformSize,
            int chunkLoadRadius,
            int ventMin,
            int ventMax,
            int surfaceMinY,
            int slopeSampleRadius,
            int slopeVarianceLimit,
            boolean enableWorldCoreOrb,
            int worldCoreParticleRate,
            boolean worldCoreSoundEnabled,
            int weatherCoreCycleMinutes,
            int weatherCoreOmenSeconds,
            int weatherCoreSafeRadius,
            int weatherCoreRareGatePercent,
            boolean weatherCoreSleepLockEnabled,
            boolean weatherCoreDisableLightningInSafeRadius,
            boolean weatherCoreReduceDebuffsInSafeRadius,
            boolean weatherCoreAllowBlindnessPulse,
            boolean weatherCoreAllowNauseaPulse,
            boolean introPopupEnabled,
            int introPopupVersion,
            int introPopupShowDelayTicks,
            boolean introPopupAllowDontShowAgain,
            boolean introPopupAllowLearnMore,
            boolean introPopupAllowDisableButton) {
    }
}
