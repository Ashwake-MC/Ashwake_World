package com.ashwake.ashwake.client.intro;

import com.ashwake.ashwake.client.intro.screen.AshwakeIntroScreen;
import com.ashwake.ashwake.network.OpenIntroGuiPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class IntroPopupClientController {
    private static OpenIntroGuiPayload pendingOpen;
    private static int ticksUntilOpen;

    private IntroPopupClientController() {
    }

    public static void queueOpen(OpenIntroGuiPayload payload) {
        pendingOpen = payload;
        ticksUntilOpen = Math.max(0, payload.openDelayTicks());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        OpenIntroGuiPayload pending = pendingOpen;
        if (pending == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || mc.player == null) {
            return;
        }
        if (mc.screen != null) {
            return;
        }

        if (ticksUntilOpen > 0) {
            ticksUntilOpen--;
            return;
        }

        pendingOpen = null;
        mc.setScreen(new AshwakeIntroScreen(pending));
    }
}
