package com.ashwake.ashwake.network;

import com.ashwake.ashwake.config.AshwakeConfig;
import com.ashwake.ashwake.world.AshwakeWorldData;
import com.ashwake.ashwake.world.intro.AshwakeIntroManager;
import com.ashwake.ashwake.world.weather.WeatherCoreApiBridge;
import com.ashwake.ashwake.world.weather.WeatherCoreClientCache;
import com.ashwake.ashwake.world.weather.WeatherCoreSavedData;
import com.ashwake.ashwake.world.weather.WeatherCoreState;
import net.minecraft.core.BlockPos;
import java.util.function.ToDoubleFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class WeatherCoreNetwork {
    private WeatherCoreNetwork() {
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("4");
        registrar.playToClient(
                WeatherCoreSyncPayload.TYPE,
                WeatherCoreSyncPayload.STREAM_CODEC,
                (payload, context) -> WeatherCoreClientCache.applySync(payload));
        registrar.playToClient(
                OpenIntroGuiPayload.TYPE,
                OpenIntroGuiPayload.STREAM_CODEC,
                (payload, context) -> IntroPopupClientBridge.handleOpen(payload));
        registrar.playToServer(
                IntroGuiClosedPayload.TYPE,
                IntroGuiClosedPayload.STREAM_CODEC,
                AshwakeIntroManager::onIntroGuiClosed);
    }

    public static void syncToAll(
            ServerLevel level,
            WeatherCoreSavedData data,
            boolean sleepLocked,
            ToDoubleFunction<ServerPlayer> intensityResolver) {
        for (ServerPlayer player : level.players()) {
            float intensity = (float) Math.max(0.0D, Math.min(1.0D, intensityResolver.applyAsDouble(player)));
            syncToPlayer(player, data, sleepLocked, intensity);
        }
    }

    public static void syncToPlayer(ServerPlayer player, WeatherCoreSavedData data, boolean sleepLocked, float intensity) {
        PacketDistributor.sendToPlayer(player, toPayload(player, data, sleepLocked, intensity));
    }

    public static void sendOpenIntroToPlayer(
            ServerPlayer player,
            int introVersion,
            int openDelayTicks,
            boolean allowDontShowAgain,
            boolean allowLearnMore,
            boolean allowDisableTutorialPopups) {
        PacketDistributor.sendToPlayer(
                player,
                new OpenIntroGuiPayload(
                        introVersion,
                        openDelayTicks,
                        allowDontShowAgain,
                        allowLearnMore,
                        allowDisableTutorialPopups));
    }

    public static void sendIntroGuiClosedFromClient(int introVersion, boolean dontShowAgain, boolean disableTutorialPopups) {
        PacketDistributor.sendToServer(new IntroGuiClosedPayload(introVersion, dontShowAgain, disableTutorialPopups));
    }

    private static WeatherCoreSyncPayload toPayload(
            ServerPlayer player,
            WeatherCoreSavedData data,
            boolean sleepLocked,
            float intensity) {
        ServerLevel level = player.serverLevel();
        AshwakeConfig.Settings settings = AshwakeConfig.snapshot();
        AshwakeWorldData worldData = AshwakeWorldData.get(level);
        WeatherCoreState current = data.getCurrentState();
        WeatherCoreState next = data.getNextState();
        BlockPos hubCenter = WeatherCoreApiBridge.resolveHubCenter(worldData);
        return new WeatherCoreSyncPayload(
                current.id(),
                data.getPhase().id(),
                data.getTicksRemaining(),
                data.getTotalTicks(),
                next == null ? "" : next.id(),
                sleepLocked,
                intensity,
                settings.weatherCoreSafeRadius(),
                hubCenter,
                settings.weatherCoreDisableLightningInSafeRadius(),
                level.getGameTime(),
                level.isRaining(),
                level.isThundering());
    }
}
