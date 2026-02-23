package com.ashwake.ashwake.world;

import com.ashwake.ashwake.AshwakeMod;
import com.ashwake.ashwake.config.AshwakeConfig;
import com.ashwake.ashwake.world.entity.RuneDiscEntity;
import com.ashwake.ashwake.world.entity.WorldCoreOrbEntity;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

public final class WorldBootstrap {
    private static final String PLAYER_INITIALIZED_TAG = "ashwake_spawn_initialized";

    private WorldBootstrap() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null) {
            return;
        }

        AshwakeConfig.Settings settings = AshwakeConfig.snapshot();
        AshwakeWorldData worldData = AshwakeWorldData.get(overworld);

        if (worldData.isPlaced()) {
            if (settings.reassertSpawn()) {
                BlockPos savedSpawn = worldData.getSpawnPos();
                if (savedSpawn != null && savedSpawn != BlockPos.ZERO) {
                    overworld.setDefaultSpawnPos(savedSpawn, 0.0F);
                    AshwakeMod.LOGGER.info("Ashwake spawn reasserted at {}", savedSpawn);
                }
            }
            ensureWorldCoreOrb(overworld, settings, worldData);
            ensureHubRuneDisc(overworld, settings, worldData);
            return;
        }

        VolcanoPlacementFinder.PlacementResult placement = VolcanoPlacementFinder.findPlacement(overworld, settings);
        VolcanoGenerator generator = new VolcanoGenerator(overworld, overworld.getSeed(), settings, placement.biomeId());
        BlockPos spawnPos = generator.generate(placement.origin());
        BlockPos corePos = generator.getWorldCoreAnchor();

        overworld.setDefaultSpawnPos(spawnPos, 0.0F);
        worldData.markPlaced(
                placement.origin(),
                spawnPos,
                settings.outerRadius(),
                settings.height(),
                overworld.getSeed(),
                placement.biomeId().toString());
        if (corePos != null && !corePos.equals(BlockPos.ZERO)) {
            worldData.markWorldCore(corePos, null);
        }
        ensureWorldCoreOrb(overworld, settings, worldData);
        ensureHubRuneDisc(overworld, settings, worldData);

        AshwakeMod.LOGGER.info(
                "Ashwake volcano generated at origin {} in biome {} (score {}), spawn set to {}",
                placement.origin(),
                placement.biomeId(),
                String.format("%.2f", placement.score()),
                spawnPos);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return;
        }

        AshwakeWorldData worldData = AshwakeWorldData.get(level);
        if (!worldData.isPlaced()) {
            return;
        }

        CompoundTag persistent = player.getPersistentData();
        if (persistent.getBoolean(PLAYER_INITIALIZED_TAG)) {
            return;
        }

        BlockPos spawnPos = worldData.getSpawnPos();
        if (spawnPos == null || spawnPos.equals(BlockPos.ZERO)) {
            return;
        }

        player.teleportTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
        persistent.putBoolean(PLAYER_INITIALIZED_TAG, true);

        AshwakeMod.LOGGER.info("Moved player {} to Ashwake interior spawn {}", player.getGameProfile().getName(), spawnPos);
    }

    private static void ensureWorldCoreOrb(ServerLevel level, AshwakeConfig.Settings settings, AshwakeWorldData worldData) {
        if (!settings.enableWorldCoreOrb()) {
            return;
        }
        if (!worldData.isPlaced()) {
            return;
        }

        BlockPos origin = worldData.getOrigin();
        if (origin == null || origin.equals(BlockPos.ZERO)) {
            return;
        }

        VolcanoGenerator decorator = new VolcanoGenerator(
                level,
                level.getSeed(),
                settings,
                ResourceLocation.fromNamespaceAndPath(AshwakeMod.MODID, "world_core"));
        BlockPos desiredCorePos = worldData.getWorldCorePos();
        if (desiredCorePos == null || desiredCorePos.equals(BlockPos.ZERO)) {
            desiredCorePos = decorator.ensureWorldCoreInfrastructure(origin, BlockPos.ZERO);
            worldData.markWorldCore(desiredCorePos, worldData.getWorldCoreUuid());
        } else {
            decorator.removeLegacyCoreShadowPedestal(origin, desiredCorePos);
            decorator.enforceCoreSightline(origin, desiredCorePos);
        }

        WorldCoreOrbEntity existing = findExistingCore(level, worldData, desiredCorePos);
        if (existing != null) {
            existing.setAnchorPos(desiredCorePos);
            if (!worldData.hasWorldCore()
                    || !desiredCorePos.equals(worldData.getWorldCorePos())
                    || !existing.getUUID().equals(worldData.getWorldCoreUuid())) {
                worldData.markWorldCore(desiredCorePos, existing.getUUID());
            }
            return;
        }

        WorldCoreOrbEntity spawned = WorldCoreOrbEntity.createAt(level, desiredCorePos);
        if (level.addFreshEntity(spawned)) {
            worldData.markWorldCore(desiredCorePos, spawned.getUUID());
            AshwakeMod.LOGGER.info("Spawned Ashwake World Core Orb at {}", desiredCorePos);
        }
    }

    private static WorldCoreOrbEntity findExistingCore(ServerLevel level, AshwakeWorldData worldData, BlockPos desiredCorePos) {
        Entity entityByUuid = worldData.getWorldCoreUuid() == null ? null : level.getEntity(worldData.getWorldCoreUuid());
        if (entityByUuid instanceof WorldCoreOrbEntity orb && !orb.isRemoved()) {
            return orb;
        }

        AABB scan = new AABB(desiredCorePos).inflate(28.0D);
        List<WorldCoreOrbEntity> nearby = level.getEntitiesOfClass(WorldCoreOrbEntity.class, scan, e -> !e.isRemoved());
        if (nearby.isEmpty()) {
            return null;
        }

        nearby.sort(Comparator.comparingDouble(e -> e.distanceToSqr(desiredCorePos.getX(), desiredCorePos.getY(), desiredCorePos.getZ())));
        WorldCoreOrbEntity keeper = nearby.get(0);
        for (int i = 1; i < nearby.size(); i++) {
            nearby.get(i).discard();
        }
        return keeper;
    }

    private static void ensureHubRuneDisc(ServerLevel level, AshwakeConfig.Settings settings, AshwakeWorldData worldData) {
        if (!worldData.isPlaced()) {
            return;
        }

        BlockPos origin = worldData.getOrigin();
        if (origin == null || origin.equals(BlockPos.ZERO)) {
            return;
        }

        int hubFloorY = origin.getY() + settings.hubCenterOffset() - 1;
        BlockPos desiredDiscPos = new BlockPos(origin.getX(), hubFloorY, origin.getZ());

        RuneDiscEntity existing = findExistingRuneDisc(level, worldData, desiredDiscPos);
        if (existing != null) {
            existing.setAnchorPos(desiredDiscPos);
            if (!worldData.hasRuneDisc()
                    || !desiredDiscPos.equals(worldData.getRuneDiscPos())
                    || !existing.getUUID().equals(worldData.getRuneDiscUuid())) {
                worldData.markRuneDisc(desiredDiscPos, existing.getUUID());
            }
            return;
        }

        RuneDiscEntity spawned = RuneDiscEntity.createAt(level, desiredDiscPos);
        if (level.addFreshEntity(spawned)) {
            worldData.markRuneDisc(desiredDiscPos, spawned.getUUID());
            AshwakeMod.LOGGER.info("Spawned Ashwake Living Rune Disc at {}", desiredDiscPos);
        }
    }

    private static RuneDiscEntity findExistingRuneDisc(ServerLevel level, AshwakeWorldData worldData, BlockPos desiredDiscPos) {
        Entity entityByUuid = worldData.getRuneDiscUuid() == null ? null : level.getEntity(worldData.getRuneDiscUuid());
        if (entityByUuid instanceof RuneDiscEntity disc && !disc.isRemoved()) {
            return disc;
        }

        AABB scan = new AABB(desiredDiscPos).inflate(24.0D);
        List<RuneDiscEntity> nearby = level.getEntitiesOfClass(RuneDiscEntity.class, scan, e -> !e.isRemoved());
        if (nearby.isEmpty()) {
            return null;
        }

        nearby.sort(Comparator.comparingDouble(e -> e.distanceToSqr(desiredDiscPos.getX(), desiredDiscPos.getY(), desiredDiscPos.getZ())));
        RuneDiscEntity keeper = nearby.get(0);
        for (int i = 1; i < nearby.size(); i++) {
            nearby.get(i).discard();
        }
        return keeper;
    }
}
