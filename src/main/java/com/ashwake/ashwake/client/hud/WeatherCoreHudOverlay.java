package com.ashwake.ashwake.client.hud;

import com.ashwake.ashwake.AshwakeMod;
import com.ashwake.ashwake.world.weather.WeatherCoreClientCache;
import com.ashwake.ashwake.world.weather.WeatherCorePhase;
import com.ashwake.ashwake.world.weather.WeatherCoreState;
import com.ashwake.ashwake.world.weather.WeatherCoreVisualProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class WeatherCoreHudOverlay {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(AshwakeMod.MODID, "weather_core_hud");

    private WeatherCoreHudOverlay() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null) {
            return;
        }
        WeatherCoreClientCache.clientTick();
    }

    public static void renderLayer(GuiGraphics guiGraphics, DeltaTracker partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }

        WeatherCoreClientCache.Snapshot snapshot = WeatherCoreClientCache.snapshot();
        if (snapshot.totalTicks() <= 0) {
            return;
        }
        WeatherCoreVisualProfile profile = WeatherCoreVisualProfile.forState(snapshot.state());
        renderColorGradeOverlay(guiGraphics, profile, snapshot);

        Font font = mc.font;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hud.ashwake.core_title"));

        Component stateName = Component.translatable(snapshot.state().translationKey());
        Component categoryName = Component.literal(snapshot.state().category().name());
        lines.add(Component.translatable("hud.ashwake.state", stateName, categoryName));
        lines.add(Component.translatable("hud.ashwake.time_left", formatTicks(snapshot.ticksRemaining())));

        if (snapshot.phase() == WeatherCorePhase.OMEN) {
            lines.add(Component.translatable("hud.ashwake.omen", formatTicks(snapshot.ticksRemaining())));
            WeatherCoreState next = snapshot.nextState();
            if (next != null) {
                lines.add(Component.translatable("hud.ashwake.next", Component.translatable(next.translationKey())));
            }
        }

        if (snapshot.sleepLocked()) {
            lines.add(Component.translatable("hud.ashwake.note", Component.translatable("hud.ashwake.note.sleep_disabled")));
        } else if (snapshot.state() == WeatherCoreState.ASHWAKE_STORM) {
            lines.add(Component.translatable("hud.ashwake.note", Component.translatable("hud.ashwake.note.storm")));
        } else if (snapshot.state().category() == WeatherCoreState.Category.GOOD) {
            lines.add(Component.translatable("hud.ashwake.note", Component.translatable("hud.ashwake.note.blessing")));
        }

        int maxWidth = 120;
        for (Component line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }
        int x = 8;
        int y = 8;
        int lineHeight = 10;
        int width = maxWidth + 10;
        int height = (lines.size() * lineHeight) + 8;

        int accent = switch (snapshot.state().category()) {
            case GOOD -> 0x66D78A;
            case BAD -> 0xD45A5A;
            case RARE -> 0xE4C35A;
        };

        guiGraphics.fill(x, y, x + width, y + height, 0x99000000);
        guiGraphics.fill(x, y, x + 3, y + height, 0xFF000000 | accent);

        int textY = y + 4;
        for (int i = 0; i < lines.size(); i++) {
            int color = (i == 0) ? 0xFFF2E4 : 0xFFECECEC;
            guiGraphics.drawString(font, lines.get(i), x + 6, textY, color, false);
            textY += lineHeight;
        }
    }

    private static void renderColorGradeOverlay(
            GuiGraphics guiGraphics,
            WeatherCoreVisualProfile profile,
            WeatherCoreClientCache.Snapshot snapshot) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        float alpha = profile.overlayAlpha() * snapshot.intensity();
        if (snapshot.phase() == WeatherCorePhase.OMEN) {
            alpha += 0.04F * snapshot.omenProgress();
        }
        alpha = Mth.clamp(alpha, 0.0F, 0.42F);
        if (alpha > 0.002F) {
            guiGraphics.fill(0, 0, width, height, argb(profile.overlayColor(), alpha));
        }

        float burst = snapshot.switchBurstStrength();
        if (burst > 0.001F) {
            int burstColor = switchBurstColor(snapshot.switchBurstState());
            float burstAlpha = Mth.clamp((0.24F * burst) + 0.04F, 0.0F, 0.30F);
            guiGraphics.fill(0, 0, width, height, argb(burstColor, burstAlpha));
        }
    }

    private static int switchBurstColor(WeatherCoreState state) {
        return switch (state.category()) {
            case GOOD -> 0xFFC27A;
            case BAD -> 0x514640;
            case RARE -> 0xD8ECFF;
        };
    }

    private static int argb(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static String formatTicks(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int minutes = seconds / 60;
        int rem = seconds % 60;
        return String.format("%02d:%02d", minutes, rem);
    }
}
