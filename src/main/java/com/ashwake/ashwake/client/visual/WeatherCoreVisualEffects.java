package com.ashwake.ashwake.client.visual;

import com.ashwake.ashwake.world.weather.WeatherCoreClientCache;
import com.ashwake.ashwake.world.weather.WeatherCorePhase;
import com.ashwake.ashwake.world.weather.WeatherCoreVisualProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class WeatherCoreVisualEffects {
    private WeatherCoreVisualEffects() {
    }

    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }

        WeatherCoreClientCache.Snapshot snapshot = WeatherCoreClientCache.snapshot();
        if (snapshot.totalTicks() <= 0) {
            return;
        }
        WeatherCoreVisualProfile profile = WeatherCoreVisualProfile.forState(snapshot.state());
        float intensity = effectiveIntensity(snapshot);
        float blend = Mth.clamp(0.14F + (0.42F * intensity), 0.0F, 0.64F);

        event.setRed(Mth.lerp(blend, event.getRed(), red(profile.fogTint())));
        event.setGreen(Mth.lerp(blend, event.getGreen(), green(profile.fogTint())));
        event.setBlue(Mth.lerp(blend, event.getBlue(), blue(profile.fogTint())));
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (event.getType() != FogType.NONE) {
            return;
        }

        WeatherCoreClientCache.Snapshot snapshot = WeatherCoreClientCache.snapshot();
        if (snapshot.totalTicks() <= 0) {
            return;
        }
        WeatherCoreVisualProfile profile = WeatherCoreVisualProfile.forState(snapshot.state());
        float intensity = effectiveIntensity(snapshot);

        float farScale = Mth.lerp(intensity, 1.0F, profile.fogDistanceScale());
        float nearTarget = Mth.clamp(profile.fogDistanceScale() * 0.92F, 0.42F, 1.25F);
        float nearScale = Mth.lerp(intensity, 1.0F, nearTarget);

        if (snapshot.phase() == WeatherCorePhase.OMEN) {
            float omenClamp = 1.0F - (0.05F * snapshot.omenProgress());
            farScale *= omenClamp;
            nearScale *= omenClamp;
        }

        event.scaleNearPlaneDistance(nearScale);
        event.scaleFarPlaneDistance(farScale);
        event.setCanceled(true);
    }

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        WeatherCoreClientCache.Snapshot snapshot = WeatherCoreClientCache.snapshot();
        if (snapshot.totalTicks() <= 0) {
            return;
        }
        WeatherCoreVisualProfile profile = WeatherCoreVisualProfile.forState(snapshot.state());
        float wobble = profile.cameraWobbleDegrees() * snapshot.intensity();
        if (snapshot.phase() == WeatherCorePhase.OMEN) {
            wobble += 0.18F * snapshot.omenProgress();
        }
        if (wobble <= 0.01F) {
            return;
        }

        double t = mc.level.getGameTime() + event.getPartialTick();
        float wobbleRoll = (float) Math.sin(t * 0.35D) * wobble;
        event.setRoll(event.getRoll() + wobbleRoll);
    }

    private static float effectiveIntensity(WeatherCoreClientCache.Snapshot snapshot) {
        float intensity = Mth.clamp(snapshot.intensity(), 0.0F, 1.0F);
        if (snapshot.phase() == WeatherCorePhase.OMEN) {
            intensity = Mth.clamp(intensity + (0.16F * snapshot.omenProgress()), 0.0F, 1.0F);
        }
        return intensity;
    }

    private static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    private static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    private static float blue(int rgb) {
        return (rgb & 0xFF) / 255.0F;
    }
}
