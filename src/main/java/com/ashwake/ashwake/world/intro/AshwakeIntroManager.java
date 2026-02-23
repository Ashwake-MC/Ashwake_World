package com.ashwake.ashwake.world.intro;

import com.ashwake.ashwake.config.AshwakeConfig;
import com.ashwake.ashwake.network.IntroGuiClosedPayload;
import com.ashwake.ashwake.network.WeatherCoreNetwork;
import com.ashwake.ashwake.world.AshwakeWorldData;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class AshwakeIntroManager {
    private static final String TAG_INTRO_SEEN = "ashwake_intro_seen";
    private static final String TAG_INTRO_VERSION_SEEN = "ashwake_intro_version_seen";
    private static final String TAG_TUTORIAL_POPUPS_DISABLED = "ashwake_intro_popups_disabled";

    private AshwakeIntroManager() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!Level.OVERWORLD.equals(player.serverLevel().dimension())) {
            return;
        }

        AshwakeConfig.Settings settings = AshwakeConfig.snapshot();
        if (!settings.introPopupEnabled()) {
            return;
        }

        AshwakeWorldData worldData = AshwakeWorldData.get(player.serverLevel());
        if (!worldData.isPlaced()) {
            return;
        }

        if (!shouldShowIntro(player, settings)) {
            return;
        }

        sendIntro(player, settings);
    }

    public static void onIntroGuiClosed(IntroGuiClosedPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        context.enqueueWork(() -> applyCloseAck(player, payload));
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ashwake")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("intro")
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    resetIntroFlags(target);
                                    ctx.getSource()
                                            .sendSuccess(
                                                    () -> Component.translatable("command.ashwake.intro.reset.success", target.getDisplayName()),
                                                    true);
                                    return Command.SINGLE_SUCCESS;
                                })))
                        .then(Commands.literal("show")
                                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    forceShowIntro(target);
                                    ctx.getSource()
                                            .sendSuccess(
                                                    () -> Component.translatable("command.ashwake.intro.show.success", target.getDisplayName()),
                                                    true);
                                    return Command.SINGLE_SUCCESS;
                                })))));
    }

    private static boolean shouldShowIntro(ServerPlayer player, AshwakeConfig.Settings settings) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(TAG_TUTORIAL_POPUPS_DISABLED)) {
            return false;
        }

        int seenVersion = data.getInt(TAG_INTRO_VERSION_SEEN);
        boolean seen = data.getBoolean(TAG_INTRO_SEEN);
        return !seen || seenVersion < settings.introPopupVersion();
    }

    private static void sendIntro(ServerPlayer player, AshwakeConfig.Settings settings) {
        WeatherCoreNetwork.sendOpenIntroToPlayer(
                player,
                settings.introPopupVersion(),
                settings.introPopupShowDelayTicks(),
                settings.introPopupAllowDontShowAgain(),
                settings.introPopupAllowLearnMore(),
                settings.introPopupAllowDisableButton());
    }

    private static void applyCloseAck(ServerPlayer player, IntroGuiClosedPayload payload) {
        CompoundTag data = player.getPersistentData();
        if (payload.disableTutorialPopups()) {
            data.putBoolean(TAG_TUTORIAL_POPUPS_DISABLED, true);
        }

        boolean markSeen = payload.dontShowAgain() || payload.disableTutorialPopups();
        if (markSeen) {
            data.putBoolean(TAG_INTRO_SEEN, true);
            data.putInt(TAG_INTRO_VERSION_SEEN, Math.max(1, payload.introVersion()));
        } else {
            data.putBoolean(TAG_INTRO_SEEN, false);
            data.putInt(TAG_INTRO_VERSION_SEEN, 0);
        }
    }

    private static void forceShowIntro(ServerPlayer player) {
        AshwakeConfig.Settings settings = AshwakeConfig.snapshot();
        sendIntro(player, settings);
    }

    private static void resetIntroFlags(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean(TAG_INTRO_SEEN, false);
        data.putInt(TAG_INTRO_VERSION_SEEN, 0);
        data.putBoolean(TAG_TUTORIAL_POPUPS_DISABLED, false);
    }
}
