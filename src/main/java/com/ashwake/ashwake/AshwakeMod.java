package com.ashwake.ashwake;

import com.ashwake.ashwake.config.AshwakeConfig;
import com.ashwake.ashwake.network.WeatherCoreNetwork;
import com.ashwake.ashwake.registry.AshwakeEntities;
import com.ashwake.ashwake.world.WorldBootstrap;
import com.ashwake.ashwake.world.intro.AshwakeIntroManager;
import com.ashwake.ashwake.world.weather.WeatherCoreManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(AshwakeMod.MODID)
public final class AshwakeMod {
    public static final String MODID = "ashwake";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AshwakeMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, AshwakeConfig.SPEC);
        AshwakeEntities.register(modEventBus);
        modEventBus.addListener(WeatherCoreNetwork::onRegisterPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(WorldBootstrap::onServerStarted);
        NeoForge.EVENT_BUS.addListener(WorldBootstrap::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(AshwakeIntroManager::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(AshwakeIntroManager::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(WeatherCoreManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(WeatherCoreManager::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(WeatherCoreManager::onCanPlayerSleep);
        NeoForge.EVENT_BUS.addListener(WeatherCoreManager::onEntityJoinLevel);
    }
}
