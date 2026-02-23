package com.ashwake.ashwake.client;

import com.ashwake.ashwake.AshwakeMod;
import com.ashwake.ashwake.client.hud.WeatherCoreHudOverlay;
import com.ashwake.ashwake.client.intro.IntroPopupClientController;
import com.ashwake.ashwake.client.render.RuneDiscRenderer;
import com.ashwake.ashwake.client.render.WorldCoreOrbRenderer;
import com.ashwake.ashwake.client.visual.WeatherCoreVisualEffects;
import com.ashwake.ashwake.network.IntroPopupClientBridge;
import com.ashwake.ashwake.registry.AshwakeEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = AshwakeMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AshwakeClientModEvents {
    private static boolean clientHooksRegistered;

    private AshwakeClientModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AshwakeEntities.RUNE_DISC.get(), RuneDiscRenderer::new);
        event.registerEntityRenderer(AshwakeEntities.WORLD_CORE_ORB.get(), WorldCoreOrbRenderer::new);
        if (!clientHooksRegistered) {
            IntroPopupClientBridge.setOpenHandler(IntroPopupClientController::queueOpen);
            NeoForge.EVENT_BUS.addListener(WeatherCoreHudOverlay::onClientTick);
            NeoForge.EVENT_BUS.addListener(IntroPopupClientController::onClientTick);
            NeoForge.EVENT_BUS.addListener(WeatherCoreVisualEffects::onComputeFogColor);
            NeoForge.EVENT_BUS.addListener(WeatherCoreVisualEffects::onRenderFog);
            NeoForge.EVENT_BUS.addListener(WeatherCoreVisualEffects::onComputeCameraAngles);
            clientHooksRegistered = true;
        }
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, WeatherCoreHudOverlay.LAYER_ID, WeatherCoreHudOverlay::renderLayer);
    }
}
