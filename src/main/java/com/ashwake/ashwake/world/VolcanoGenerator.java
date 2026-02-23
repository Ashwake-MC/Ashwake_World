package com.ashwake.ashwake.world;

import com.ashwake.ashwake.config.AshwakeConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

public final class VolcanoGenerator {
    private static final int HUB_PLAZA_RADIUS = 10;
    private static final int TERRAIN_BLEND_RADIUS = 18;

    private final ServerLevel level;
    private final long worldSeed;
    private final AshwakeConfig.Settings settings;
    @SuppressWarnings("unused")
    private final ResourceLocation biome;
    private final WeightedPalette exteriorPalette;
    private final WeightedPalette interiorPalette;
    private final WeightedPalette floorPalette;
    private final WoodPalette wood;
    private final List<Pocket> pockets = new ArrayList<>();
    private final List<HubExit> hubExits = new ArrayList<>();
    private final List<LadderShaft> ladderShafts = new ArrayList<>();
    private final Set<Long> intentionalLava = new HashSet<>();
    private final Set<Long> protectedBlocks = new HashSet<>();
    private final Set<Long> shaftModuleBlocks = new HashSet<>();
    private final Set<Long> shaftWalkableBlocks = new HashSet<>();
    private BlockPos origin = BlockPos.ZERO;
    private int originY;
    private int hubCenterY;
    private int terrainSampleRadius;
    private int[][] terrainGroundY;
    private boolean[][] terrainWet;
    private BuildingSpec forgeBuilding;
    private BlockPos worldCoreAnchor = BlockPos.ZERO;

    public VolcanoGenerator(ServerLevel level, long worldSeed, AshwakeConfig.Settings settings, ResourceLocation biome) {
        this.level = level;
        this.worldSeed = worldSeed;
        this.settings = settings;
        this.biome = biome;
        this.exteriorPalette = new WeightedPalette(List.of(
                new PaletteEntry(Blocks.BASALT, 24),
                new PaletteEntry(Blocks.SMOOTH_BASALT, 16),
                new PaletteEntry(Blocks.BLACKSTONE, 18),
                new PaletteEntry(Blocks.DEEPSLATE_TILES, 18),
                new PaletteEntry(Blocks.DEEPSLATE_BRICKS, 12),
                new PaletteEntry(Blocks.POLISHED_BLACKSTONE_BRICKS, 10),
                new PaletteEntry(Blocks.GILDED_BLACKSTONE, 2)));
        this.interiorPalette = new WeightedPalette(List.of(
                new PaletteEntry(Blocks.DEEPSLATE_TILES, 28),
                new PaletteEntry(Blocks.DEEPSLATE_BRICKS, 20),
                new PaletteEntry(Blocks.BLACKSTONE, 20),
                new PaletteEntry(Blocks.BASALT, 14),
                new PaletteEntry(Blocks.POLISHED_BLACKSTONE_BRICKS, 12),
                new PaletteEntry(Blocks.GILDED_BLACKSTONE, 4),
                new PaletteEntry(Blocks.CUT_COPPER, 2)));
        this.floorPalette = new WeightedPalette(List.of(
                new PaletteEntry(Blocks.DEEPSLATE_TILES, 32),
                new PaletteEntry(Blocks.DEEPSLATE_BRICKS, 24),
                new PaletteEntry(Blocks.POLISHED_BLACKSTONE, 22),
                new PaletteEntry(Blocks.BLACKSTONE, 14),
                new PaletteEntry(Blocks.POLISHED_BLACKSTONE_BRICKS, 6),
                new PaletteEntry(Blocks.GILDED_BLACKSTONE, 1),
                new PaletteEntry(Blocks.CUT_COPPER, 1)));
        this.wood = ((mix(worldSeed, 13, 17, 19) & 1L) == 0L)
                ? new WoodPalette(
                        Blocks.STRIPPED_DARK_OAK_LOG,
                        Blocks.DARK_OAK_PLANKS,
                        Blocks.DARK_OAK_SLAB,
                        Blocks.DARK_OAK_STAIRS,
                        Blocks.DARK_OAK_DOOR,
                        Blocks.DARK_OAK_TRAPDOOR)
                : new WoodPalette(
                        Blocks.STRIPPED_SPRUCE_LOG,
                        Blocks.SPRUCE_PLANKS,
                        Blocks.SPRUCE_SLAB,
                        Blocks.SPRUCE_STAIRS,
                        Blocks.SPRUCE_DOOR,
                        Blocks.SPRUCE_TRAPDOOR);
    }

    public BlockPos generate(BlockPos selectedOrigin) {
        this.origin = selectedOrigin.immutable();
        this.originY = selectedOrigin.getY();
        this.hubCenterY = originY + settings.hubCenterOffset();
        this.ladderShafts.clear();
        this.protectedBlocks.clear();
        this.shaftModuleBlocks.clear();
        this.shaftWalkableBlocks.clear();
        initPockets();
        ensureChunksLoaded();
        snapshotTerrain();

        stabilizeBuildVolume();
        prepareBase();
        buildExteriorShell();
        buildCalderaCrown();
        carveInterior();
        applyFoundationAndSkirt();
        sealAccidentalExteriorLeaks();
        blendVolcanoIntoTerrain();
        skinAndFloorPass();
        buildSettlement();
        addLavaFeatures();
        BlockPos spawn = safetyPass();
        this.worldCoreAnchor = buildWorldCoreInfrastructure(BlockPos.ZERO);
        lightingPass();
        stabilityValidationPass();
        return spawn;
    }

    public BlockPos getWorldCoreAnchor() {
        return worldCoreAnchor == null ? BlockPos.ZERO : worldCoreAnchor;
    }

    public BlockPos ensureWorldCoreInfrastructure(BlockPos selectedOrigin, BlockPos preferredCorePos) {
        this.origin = selectedOrigin.immutable();
        this.originY = selectedOrigin.getY();
        this.hubCenterY = originY + settings.hubCenterOffset();
        this.ladderShafts.clear();
        this.protectedBlocks.clear();
        this.shaftModuleBlocks.clear();
        this.shaftWalkableBlocks.clear();
        ensureChunksLoaded();
        this.worldCoreAnchor = buildWorldCoreInfrastructure(preferredCorePos == null ? BlockPos.ZERO : preferredCorePos);
        return this.worldCoreAnchor;
    }

    public void removeLegacyCoreShadowPedestal(BlockPos selectedOrigin, BlockPos corePos) {
        if (selectedOrigin == null || corePos == null || corePos.equals(BlockPos.ZERO)) {
            return;
        }
        this.origin = selectedOrigin.immutable();
        this.originY = selectedOrigin.getY();
        this.hubCenterY = originY + settings.hubCenterOffset();
        clearCoreShadowPedestal(corePos);
    }

    public void enforceCoreSightline(BlockPos selectedOrigin, BlockPos corePos) {
        if (selectedOrigin == null || corePos == null || corePos.equals(BlockPos.ZERO)) {
            return;
        }
        this.origin = selectedOrigin.immutable();
        this.originY = selectedOrigin.getY();
        this.hubCenterY = originY + settings.hubCenterOffset();
        ensureShaftSightline(corePos);
        rebuildCorePerchPlatforms(corePos);
        validateExistingCoreLadders(corePos);
    }

    private void initPockets() {
        int pocketY = settings.hubCenterOffset() + 1;
        pockets.clear();
        pockets.add(new Pocket("inn", -25, pocketY, 5, 13, 9, 9));
        pockets.add(new Pocket("storage", -20, pocketY, -18, 11, 8, 8));
        pockets.add(new Pocket("story", 25, pocketY, -5, 13, 10, 10));
        pockets.add(new Pocket("forge", 20, pocketY, 18, 11, 9, 8));
    }

    private void ensureChunksLoaded() {
        int cX = origin.getX() >> 4;
        int cZ = origin.getZ() >> 4;
        for (int x = cX - settings.chunkLoadRadius(); x <= cX + settings.chunkLoadRadius(); x++) {
            for (int z = cZ - settings.chunkLoadRadius(); z <= cZ + settings.chunkLoadRadius(); z++) {
                level.getChunk(x, z);
            }
        }
    }

    private void snapshotTerrain() {
        terrainSampleRadius = settings.outerRadius() + TERRAIN_BLEND_RADIUS + 8;
        int size = terrainSampleRadius * 2 + 1;
        terrainGroundY = new int[size][size];
        terrainWet = new boolean[size][size];
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int seaLevel = level.getSeaLevel();

        for (int lx = -terrainSampleRadius; lx <= terrainSampleRadius; lx++) {
            for (int lz = -terrainSampleRadius; lz <= terrainSampleRadius; lz++) {
                int x = wx(lx);
                int z = wz(lz);
                int ix = lx + terrainSampleRadius;
                int iz = lz + terrainSampleRadius;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int floorY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
                floorY = Mth.clamp(floorY, minY, maxY);
                int topY = Mth.clamp(surfaceY, minY, maxY);
                BlockState topState = level.getBlockState(new BlockPos(x, topY, z));
                terrainGroundY[ix][iz] = floorY;
                terrainWet[ix][iz] = topState.getFluidState().is(FluidTags.WATER) || surfaceY <= seaLevel;
            }
        }
    }

    private int sampledGroundY(int lx, int lz) {
        int ix = lx + terrainSampleRadius;
        int iz = lz + terrainSampleRadius;
        if (terrainGroundY != null && ix >= 0 && iz >= 0 && ix < terrainGroundY.length && iz < terrainGroundY[ix].length) {
            return terrainGroundY[ix][iz];
        }
        int x = wx(lx);
        int z = wz(lz);
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        return Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
    }

    private boolean sampledWet(int lx, int lz) {
        int ix = lx + terrainSampleRadius;
        int iz = lz + terrainSampleRadius;
        if (terrainWet != null && ix >= 0 && iz >= 0 && ix < terrainWet.length && iz < terrainWet[ix].length) {
            return terrainWet[ix][iz];
        }
        int x = wx(lx);
        int z = wz(lz);
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        top = Mth.clamp(top, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        return level.getBlockState(new BlockPos(x, top, z)).getFluidState().is(FluidTags.WATER) || top <= level.getSeaLevel();
    }

    private void stabilizeBuildVolume() {
        int radius = settings.outerRadius() + 8;
        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                for (int y = originY - 40; y <= originY + settings.height() + 8; y++) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (!state.getFluidState().isEmpty()) {
                        setBlockWorld(x, y, z, Blocks.BASALT.defaultBlockState());
                    }
                }
            }
        }
    }

    private void prepareBase() {
        int radius = settings.outerRadius() + 4;
        int radiusSq = radius * radius;
        for (int lx = -radius; lx <= radius; lx++) {
            for (int lz = -radius; lz <= radius; lz++) {
                if ((lx * lx) + (lz * lz) > radiusSq) {
                    continue;
                }
                int x = wx(lx);
                int z = wz(lz);
                for (int y = originY - 5; y <= originY + 2; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.getFluidState().isEmpty() || (y <= originY && (state.isAir() || !state.blocksMotion()))) {
                        setBlockWorld(x, y, z, pickExterior(x, y, z));
                    }
                }
            }
        }
    }

    private int computeConeSurfaceY(int lx, int lz) {
        double r = Math.sqrt((double) lx * lx + (double) lz * lz);
        double cone = settings.height() * Math.pow(Math.max(0.0D, 1.0D - (r / settings.outerRadius())), settings.slopeAlpha());
        double n = noise2D((wx(lx) + 37.0D) * 0.08D, (wz(lz) - 19.0D) * 0.08D, 101L);
        return originY + Math.max(0, (int) Math.round(cone + n * settings.noiseAmplitude()));
    }

    private int computeExteriorSurfaceY(int lx, int lz) {
        double r = Math.sqrt((double) lx * lx + (double) lz * lz);
        int surfaceY = computeConeSurfaceY(lx, lz);
        int craterTopY = originY + settings.height() - 6;
        double craterRadius = settings.calderaRadiusTop()
                + (noise2D((wx(lx) + 11.0D) * 0.03D, (wz(lz) - 7.0D) * 0.03D, 139L) * 2.0D);
        if (r < craterRadius && surfaceY > originY + settings.height() - 10) {
            return Math.min(surfaceY, craterTopY);
        }
        return surfaceY;
    }

    private void buildExteriorShell() {
        int outer = settings.outerRadius();
        int craterTopY = originY + settings.height() - 6;
        int craterBottomY = hubCenterY + (settings.hubHeight() / 2);
        for (int lx = -outer; lx <= outer; lx++) {
            for (int lz = -outer; lz <= outer; lz++) {
                double r = Math.sqrt((double) lx * lx + (double) lz * lz);
                if (r > outer) {
                    continue;
                }
                int x = wx(lx);
                int z = wz(lz);
                int surfaceY = computeExteriorSurfaceY(lx, lz);
                double craterRadius = settings.calderaRadiusTop() + (noise2D((x + 11.0D) * 0.03D, (z - 7.0D) * 0.03D, 139L) * 2.0D);
                int shell = r > 28.0D ? 5 : 7;
                for (int y = surfaceY; y >= surfaceY - shell; y--) {
                    setBlockWorld(x, y, z, pickExterior(x, y, z));
                }
                for (int y = craterTopY; y >= craterBottomY; y--) {
                    if (r < craterRadius - 1.0D) {
                        setAirWorld(x, y, z);
                    } else if (r <= craterRadius + 2.0D) {
                        setBlockWorld(x, y, z, pickExterior(x, y, z));
                    }
                }
            }
        }
        carveVents();
    }

    private void buildCalderaCrown() {
        int rimY = originY + settings.height() - 6;
        int capY = rimY + 1;
        int crownRadius = Math.max(16, settings.calderaRadiusTop() + 2);

        buildIrregularCrownRim(rimY, crownRadius);
        buildRuneFrame(capY);
        buildUpperBrokenCrownRing(capY + 14, crownRadius);
        buildLightTeeth(rimY, crownRadius);
        buildHangingRelic(capY);
        addRimCrownLighting(rimY, crownRadius);
        addRimGlowPockets(rimY, crownRadius);
        buildSkylightRing(capY + 22);
    }

    private void buildIrregularCrownRim(int rimY, int crownRadius) {
        int limit = crownRadius + 3;
        for (int lx = -limit; lx <= limit; lx++) {
            for (int lz = -limit; lz <= limit; lz++) {
                double r = Math.sqrt((double) lx * lx + (double) lz * lz);
                double rimNoise = noise2D(wx(lx) * 0.17D, wz(lz) * 0.17D, 901L) * 2.0D;
                double target = crownRadius + rimNoise;
                if (r >= target - 1.5D && r <= target + 1.5D) {
                    setBlockLocal(lx, rimY - 1, lz, pickExterior(wx(lx), rimY - 1, wz(lz)));
                    setBlockLocal(lx, rimY, lz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    setBlockLocal(lx, rimY + 1, lz, Blocks.DEEPSLATE_TILES.defaultBlockState());
                }
            }
        }
    }

    private void buildRuneFrame(int capY) {
        int outerHalf = 8;
        int innerHalf = 5;
        int notchDepth = 2;

        for (int lx = -outerHalf; lx <= outerHalf; lx++) {
            for (int lz = -outerHalf; lz <= outerHalf; lz++) {
                int max = Math.max(Math.abs(lx), Math.abs(lz));
                if (max > outerHalf || max < innerHalf) {
                    continue;
                }
                for (int y = capY; y <= capY + 1; y++) {
                    setBlockLocal(lx, y, lz, pickRuneStone(lx, y, lz));
                }
            }
        }

        // 2x3 rune windows on each inward face.
        carveRuneWindow(capY, Direction.EAST);
        carveRuneWindow(capY, Direction.WEST);
        carveRuneWindow(capY, Direction.NORTH);
        carveRuneWindow(capY, Direction.SOUTH);

        // Corner glyph notches.
        for (int sx : List.of(-1, 1)) {
            for (int sz : List.of(-1, 1)) {
                for (int d = 0; d < notchDepth; d++) {
                    int nx = sx * (outerHalf - d);
                    int nz = sz * (outerHalf - d);
                    for (int y = capY; y <= capY + 1; y++) {
                        setAirLocal(nx, y, nz);
                        setAirLocal(nx - sx, y, nz);
                        setAirLocal(nx, y, nz - sz);
                    }
                }
            }
        }
    }

    private void carveRuneWindow(int capY, Direction face) {
        int innerHalf = 5;
        int minY = capY - 1;
        int maxY = capY + 1;

        if (face == Direction.EAST || face == Direction.WEST) {
            int xBase = face == Direction.EAST ? innerHalf : -innerHalf;
            int xStep = face == Direction.EAST ? 1 : -1;
            for (int dx = 0; dx < 2; dx++) {
                int x = xBase + (xStep * dx);
                for (int z = -1; z <= 0; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        setAirLocal(x, y, z);
                    }
                }
            }
        } else {
            int zBase = face == Direction.SOUTH ? innerHalf : -innerHalf;
            int zStep = face == Direction.SOUTH ? 1 : -1;
            for (int dz = 0; dz < 2; dz++) {
                int z = zBase + (zStep * dz);
                for (int x = -1; x <= 0; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        setAirLocal(x, y, z);
                    }
                }
            }
        }
    }

    private void buildUpperBrokenCrownRing(int crownY, int baseRadius) {
        for (int angleDeg = 0; angleDeg < 360; angleDeg++) {
            if (random01(mix(worldSeed ^ 983L, angleDeg, crownY, baseRadius)) <= 0.35D) {
                continue;
            }

            double angle = Math.toRadians(angleDeg);
            int radius = baseRadius + (int) Math.round(noise2D((angleDeg + 17) * 0.07D, (angleDeg - 33) * 0.07D, 991L) * 2.0D);
            int lx = (int) Math.round(Math.cos(angle) * radius);
            int lz = (int) Math.round(Math.sin(angle) * radius);
            int px = (int) Math.signum(-Math.sin(angle));
            int pz = (int) Math.signum(Math.cos(angle));

            for (int y = crownY; y <= crownY + 1; y++) {
                setBlockLocal(lx, y, lz, Blocks.BLACKSTONE.defaultBlockState());
                setBlockLocal(lx + px, y, lz + pz, Blocks.DEEPSLATE_TILES.defaultBlockState());
            }
        }

        for (int i = 0; i < 6; i++) {
            double angle = ((Math.PI * 2.0D) * i / 6.0D) + (randomSigned(mix(worldSeed ^ 997L, i, crownY, 3)) * 0.18D);
            int radius = baseRadius + (int) Math.round(noise2D((i + 7) * 0.19D, (i - 11) * 0.21D, 1003L) * 2.0D);
            int lx = (int) Math.round(Math.cos(angle) * radius);
            int lz = (int) Math.round(Math.sin(angle) * radius);
            int len = 6 + (int) Math.floorMod(mix(worldSeed ^ 1009L, i, 3, 5), 5);
            for (int d = 1; d <= len; d++) {
                int y = crownY - d;
                BlockState tooth = (d % 3 == 0) ? Blocks.BLACKSTONE_WALL.defaultBlockState() : Blocks.CHAIN.defaultBlockState();
                setBlockLocal(lx, y, lz, tooth);
            }
        }
    }

    private void buildLightTeeth(int rimY, int crownRadius) {
        int radius = Math.max(settings.calderaRadiusTop() + 2, crownRadius - 2);
        int toothCount = 24 + (int) Math.floorMod(mix(worldSeed ^ 1013L, radius, rimY, 1), 5);
        for (int i = 0; i < toothCount; i++) {
            double angle = ((Math.PI * 2.0D) * i / toothCount) + (randomSigned(mix(worldSeed ^ 1019L, i, 2, 4)) * 0.09D);
            int lx = (int) Math.round(Math.cos(angle) * radius);
            int lz = (int) Math.round(Math.sin(angle) * radius);
            int len = 4 + (int) Math.floorMod(mix(worldSeed ^ 1021L, i, 5, 9), 4);
            for (int d = 0; d < len; d++) {
                int y = rimY - d;
                boolean accent = Math.floorMod(mix(worldSeed ^ 1031L, i, d, 7), 7) == 0;
                setBlockLocal(lx, y, lz, accent ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState());
            }
        }
    }

    private void buildHangingRelic(int capY) {
        int relicY = capY - 18;
        int inner = 4;
        int outer = 5;
        for (int lx = -outer - 1; lx <= outer + 1; lx++) {
            for (int lz = -outer - 1; lz <= outer + 1; lz++) {
                double r = Math.sqrt((double) lx * lx + (double) lz * lz);
                if (r >= inner && r <= outer + 0.2D) {
                    setBlockLocal(lx, relicY, lz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    setBlockLocal(lx, relicY + 1, lz, Blocks.DEEPSLATE_TILES.defaultBlockState());
                }
            }
        }

        for (int sx : List.of(-1, 1)) {
            for (int sz : List.of(-1, 1)) {
                int anchorX = sx * 7;
                int anchorZ = sz * 7;
                int targetX = sx * 4;
                int targetZ = sz * 4;
                drawChainLine(anchorX, capY + 2, anchorZ, targetX, relicY + 2, targetZ);
            }
        }

        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int lx = dir.getStepX() * 5;
            int lz = dir.getStepZ() * 5;
            setBlockLocal(lx, relicY + 1, lz, Blocks.LANTERN.defaultBlockState());
            setBlockLocal(lx + dir.getStepX(), relicY + 1, lz + dir.getStepZ(), wood.trapdoor.defaultBlockState());
        }
    }

    private void drawChainLine(int x0, int y0, int z0, int x1, int y1, int z1) {
        int steps = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
        if (steps <= 0) {
            setBlockLocal(x0, y0, z0, Blocks.CHAIN.defaultBlockState());
            return;
        }

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int lx = (int) Math.round(x0 + ((x1 - x0) * t));
            int y = (int) Math.round(y0 + ((y1 - y0) * t));
            int lz = (int) Math.round(z0 + ((z1 - z0) * t));
            setBlockLocal(lx, y, lz, Blocks.CHAIN.defaultBlockState());
        }
    }

    private void buildSkylightRing(int skylightY) {
        for (int lx = -20; lx <= 20; lx++) {
            for (int lz = -20; lz <= 20; lz++) {
                double r = Math.sqrt((double) lx * lx + (double) lz * lz);
                if (r >= 18.0D && r <= 19.0D && ((lx + lz) & 1) == 0) {
                    setBlockLocal(lx, skylightY, lz, Blocks.GLASS_PANE.defaultBlockState());
                }
            }
        }
    }

    private BlockState pickRuneStone(int lx, int y, int lz) {
        long roll = mix(worldSeed ^ 1049L, wx(lx), y, wz(lz));
        return (roll & 3L) == 0L
                ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    }

    private void addRimCrownLighting(int rimY, int crownRadius) {
        int lanternCount = 16;
        int ringRadius = crownRadius - 2;
        for (int i = 0; i < lanternCount; i++) {
            double angle = (Math.PI * 2.0D) * i / lanternCount;
            int lx = (int) Math.round(Math.cos(angle) * ringRadius);
            int lz = (int) Math.round(Math.sin(angle) * ringRadius);
            setBlockLocal(lx, rimY + 1, lz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            placeLantern(lx, rimY + 2, lz);
        }
    }

    private void addRimGlowPockets(int rimY, int crownRadius) {
        int pocketRadius = Math.max(settings.calderaRadiusTop() + 1, crownRadius - 3);
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int barX = dir.getStepX() * pocketRadius;
            int barZ = dir.getStepZ() * pocketRadius;
            int magmaX = barX + dir.getStepX();
            int magmaZ = barZ + dir.getStepZ();
            Direction side = dir.getClockWise();

            setBlockLocal(barX, rimY + 1, barZ, Blocks.IRON_BARS.defaultBlockState());
            setBlockLocal(barX, rimY + 2, barZ, Blocks.IRON_BARS.defaultBlockState());

            setBlockLocal(magmaX, rimY + 1, magmaZ, Blocks.MAGMA_BLOCK.defaultBlockState());
            setBlockLocal(magmaX, rimY + 2, magmaZ, Blocks.MAGMA_BLOCK.defaultBlockState());

            setBlockLocal(magmaX, rimY, magmaZ, Blocks.BASALT.defaultBlockState());
            setBlockLocal(magmaX, rimY + 3, magmaZ, Blocks.BASALT.defaultBlockState());
            setBlockLocal(magmaX + dir.getStepX(), rimY + 1, magmaZ + dir.getStepZ(), Blocks.BASALT.defaultBlockState());
            setBlockLocal(magmaX + side.getStepX(), rimY + 1, magmaZ + side.getStepZ(), Blocks.BASALT.defaultBlockState());
            setBlockLocal(magmaX - side.getStepX(), rimY + 1, magmaZ - side.getStepZ(), Blocks.BASALT.defaultBlockState());
            setBlockLocal(magmaX + side.getStepX(), rimY + 2, magmaZ + side.getStepZ(), Blocks.BASALT.defaultBlockState());
            setBlockLocal(magmaX - side.getStepX(), rimY + 2, magmaZ - side.getStepZ(), Blocks.BASALT.defaultBlockState());
        }
    }

    private void carveVents() {
        int min = Math.min(settings.ventMin(), settings.ventMax());
        int max = Math.max(settings.ventMin(), settings.ventMax());
        if (max <= 0) {
            return;
        }
        int count = min + (int) Math.floorMod(mix(worldSeed ^ 801L, 2, 3, 5), (max - min) + 1);
        for (int i = 0; i < count; i++) {
            double angle = ((Math.PI * 2.0D) * i / Math.max(1, count)) + (random01(mix(worldSeed ^ 811L, i, 0, 0)) * 0.8D);
            int radius = 30 + (int) Math.floor(random01(mix(worldSeed ^ 813L, i, 1, 2)) * 8.0D);
            int lx = (int) Math.round(Math.cos(angle) * radius);
            int lz = (int) Math.round(Math.sin(angle) * radius);
            int baseY = originY + settings.height() / 2 + (int) Math.round(randomSigned(mix(worldSeed ^ 817L, i, 7, 9)) * 2.0D);
            int h = 6 + (int) Math.floor(random01(mix(worldSeed ^ 819L, i, 8, 11)) * 4.0D);
            for (int dy = 0; dy <= h; dy++) {
                for (int ox = 0; ox < 2; ox++) {
                    for (int oz = 0; oz < 2; oz++) {
                        setAirLocal(lx + ox, baseY + dy, lz + oz);
                    }
                }
            }
            setBlockLocal(lx, baseY - 1, lz, Blocks.CAMPFIRE.defaultBlockState());
            setBlockLocal(lx, baseY, lz, Blocks.IRON_BARS.defaultBlockState());
        }
    }

    private void carveInterior() {
        carveHub();
        carveRing();
        carveHubExits();
        carvePocketSpaces();
        carveSecretTunnel();
    }

    private void carveHub() {
        int rx = settings.hubRadius();
        int rz = settings.hubRadius();
        double ry = settings.hubHeight() / 2.0D;
        for (int lx = -rx - 2; lx <= rx + 2; lx++) {
            for (int ly = (int) -ry - 2; ly <= ry + 2; ly++) {
                for (int lz = -rz - 2; lz <= rz + 2; lz++) {
                    double nx = lx / (double) rx;
                    double ny = ly / ry;
                    double nz = lz / (double) rz;
                    double d = (nx * nx) + (ny * ny) + (nz * nz);
                    int x = wx(lx);
                    int y = hubCenterY + ly;
                    int z = wz(lz);
                    double n = noise3D(x * 0.09D, y * 0.09D, z * 0.09D, 211L) * 0.12D;
                    if (d <= 1.0D + n) {
                        setAirWorld(x, y, z);
                    }
                }
            }
        }
    }

    private void carveRing() {
        int ringY = hubCenterY + 1;
        int ring = settings.ringRadius();
        int tube = settings.ringWidth();
        int limit = ring + tube + 4;
        for (int lx = -limit; lx <= limit; lx++) {
            for (int lz = -limit; lz <= limit; lz++) {
                double horizontal = Math.sqrt((double) lx * lx + (double) lz * lz);
                for (int y = ringY - 4; y <= ringY + 4; y++) {
                    double d = Math.pow(horizontal - ring, 2.0D) + Math.pow((y - ringY) / 1.8D, 2.0D);
                    if (d <= tube * tube) {
                        setAirLocal(lx, y, lz);
                    }
                }
                if (Math.abs(horizontal - ring) <= tube + 0.4D) {
                    int floorY = ringY - 2;
                    setBlockLocal(lx, floorY, lz, pickFloor(wx(lx), floorY, wz(lz)));
                    for (int y = floorY + 1; y <= floorY + 5; y++) {
                        setAirLocal(lx, y, lz);
                    }
                }
            }
        }
    }

    private void carveHubExits() {
        hubExits.clear();
        carveSingleHubExit(Direction.NORTH, 5, 5, settings.outerRadius() + 24, false);
        carveSingleHubExit(Direction.WEST, 5, 5, settings.outerRadius() + 24, false);
        carveSingleHubExit(Direction.EAST, 5, 5, settings.outerRadius() + 24, false);
        carveSingleHubExit(Direction.SOUTH, 3, 4, 24, true);
    }

    private void carveSingleHubExit(Direction dir, int width, int height, int maxLen, boolean smallSouth) {
        int gateDistance = HUB_PLAZA_RADIUS + 3;
        int startX = dir.getStepX() * gateDistance;
        int startZ = dir.getStepZ() * gateDistance;
        int floorY = hubCenterY - 1;
        Direction right = dir.getClockWise();
        int outsideRun = 0;
        int endStep = maxLen;

        for (int step = 0; step <= maxLen; step++) {
            int centerX = startX + dir.getStepX() * step;
            int centerZ = startZ + dir.getStepZ() * step;
            carveTunnelCrossSection(centerX, centerZ, floorY, width, height, right);

            if (isOutsideAhead(centerX, centerZ, floorY, width, height, dir)) {
                outsideRun++;
            } else {
                outsideRun = 0;
            }

            if (outsideRun >= 3 && step > 8) {
                endStep = Math.min(maxLen, step + 2);
                for (int extra = step + 1; extra <= endStep; extra++) {
                    int extraX = startX + dir.getStepX() * extra;
                    int extraZ = startZ + dir.getStepZ() * extra;
                    carveTunnelCrossSection(extraX, extraZ, floorY, width, height, right);
                }
                break;
            }
        }

        int exitX = startX + dir.getStepX() * endStep;
        int exitZ = startZ + dir.getStepZ() * endStep;
        reinforceExitRim(exitX, exitZ, floorY, width, height, dir, right);
        hubExits.add(new HubExit(dir, width, height, endStep, exitX, exitZ, smallSouth));

    }

    private void carveTunnelCrossSection(int centerX, int centerZ, int floorY, int width, int height, Direction right) {
        int half = width / 2;
        for (int offset = -half; offset <= half; offset++) {
            int x = centerX + right.getStepX() * offset;
            int z = centerZ + right.getStepZ() * offset;
            for (int dy = 1; dy <= height; dy++) {
                boolean archCorner = Math.abs(offset) == half && dy == height;
                if (!archCorner) {
                    setAirLocal(x, floorY + dy, z);
                }
            }
        }
    }

    private boolean isOutsideAhead(int centerX, int centerZ, int floorY, int width, int height, Direction dir) {
        double radial = Math.sqrt((double) centerX * centerX + (double) centerZ * centerZ);
        if (radial < settings.outerRadius() - 1) {
            return false;
        }

        int probeX = centerX + dir.getStepX();
        int probeZ = centerZ + dir.getStepZ();
        Direction right = dir.getClockWise();
        int half = width / 2;
        int open = 0;
        int total = 0;

        for (int offset = -half; offset <= half; offset++) {
            int x = wx(probeX + right.getStepX() * offset);
            int z = wz(probeZ + right.getStepZ() * offset);
            for (int y = floorY + 1; y <= floorY + Math.min(3, height); y++) {
                total++;
                if (level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                    open++;
                }
            }
        }
        return open >= Math.max(3, (total * 2) / 3);
    }

    private void reinforceExitRim(int exitX, int exitZ, int floorY, int width, int height, Direction dir, Direction right) {
        int half = width / 2;
        for (int offset = -half - 1; offset <= half + 1; offset++) {
            int sideX = exitX + right.getStepX() * offset;
            int sideZ = exitZ + right.getStepZ() * offset;
            for (int dy = 0; dy <= height + 1; dy++) {
                int y = floorY + dy;
                boolean edge = Math.abs(offset) == half + 1 || dy == 0 || dy == height + 1;
                if (edge) {
                    setBlockLocal(sideX, y, sideZ, Blocks.BASALT.defaultBlockState());
                    setBlockLocal(sideX + dir.getStepX(), y, sideZ + dir.getStepZ(), Blocks.BASALT.defaultBlockState());
                }
            }
        }
    }

    private void carvePocketSpaces() {
        for (Pocket p : pockets) {
            int hx = p.sizeX / 2;
            int hy = p.sizeY / 2;
            int hz = p.sizeZ / 2;
            int centerY = originY + p.localY;
            for (int lx = p.localX - hx - 1; lx <= p.localX + hx + 1; lx++) {
                for (int y = centerY - hy - 1; y <= centerY + hy + 1; y++) {
                    for (int lz = p.localZ - hz - 1; lz <= p.localZ + hz + 1; lz++) {
                        double nx = (lx - p.localX) / (hx + 0.5D);
                        double ny = (y - centerY) / (hy + 0.5D);
                        double nz = (lz - p.localZ) / (hz + 0.5D);
                        double d = (nx * nx) + (ny * ny) + (nz * nz);
                        double n = noise3D(wx(lx) * 0.07D, y * 0.07D, wz(lz) * 0.07D, 241L) * 0.08D;
                        if (d <= 1.1D + n) {
                            setAirLocal(lx, y, lz);
                        }
                    }
                }
            }
        }
    }

    private void carveSecretTunnel() {
        if (!settings.enableSecretTunnel()) {
            return;
        }
        int startX = 18;
        int endX = 42;
        int startLocalY = settings.hubCenterOffset() + 1;
        int endLocalY = settings.hubCenterOffset() - 3;
        for (int lx = startX; lx <= endX; lx++) {
            double t = (lx - startX) / (double) (endX - startX);
            int centerY = originY + (int) Math.round(Mth.lerp(t, startLocalY, endLocalY));
            for (int lz = -1; lz <= 1; lz++) {
                for (int dy = 0; dy < 3; dy++) {
                    setAirLocal(lx, centerY + dy, lz);
                }
                setBlockLocal(lx, centerY - 1, lz, pickFloor(wx(lx), centerY - 1, wz(lz)));
            }
        }

        if (!settings.enableRelicRoom()) {
            return;
        }
        int cx = 45;
        int cy = originY + settings.hubCenterOffset() - 5;
        for (int lx = cx - 6; lx <= cx + 6; lx++) {
            for (int y = cy - 3; y <= cy + 3; y++) {
                for (int lz = -6; lz <= 6; lz++) {
                    double nx = (lx - cx) / 6.0D;
                    double ny = (y - cy) / 3.0D;
                    double nz = lz / 6.0D;
                    if ((nx * nx) + (ny * ny) + (nz * nz) <= 1.0D + (noise3D(wx(lx) * 0.08D, y * 0.08D, wz(lz) * 0.08D, 263L) * 0.05D)) {
                        setAirLocal(lx, y, lz);
                    }
                }
            }
        }
    }

    private void applyFoundationAndSkirt() {
        int outer = settings.outerRadius();
        int skirtRadius = outer + 6;
        int seaLevel = level.getSeaLevel();

        for (int lx = -skirtRadius; lx <= skirtRadius; lx++) {
            for (int lz = -skirtRadius; lz <= skirtRadius; lz++) {
                double r = Math.sqrt((double) lx * lx + (double) lz * lz);
                if (r < Math.max(outer - 2.5D, settings.hubRadius() + 6.0D) || r > skirtRadius + 0.5D) {
                    continue;
                }

                int groundY = sampledGroundY(lx, lz);
                int surfaceY = computeSkirtSurfaceY(lx, lz, r, groundY);
                if (surfaceY - groundY > 2) {
                    fillVerticalMass(lx, lz, groundY, surfaceY - 1, 3, true);
                }

                if (sampledWet(lx, lz) || groundY <= seaLevel - 1) {
                    int seaTop = Math.min(surfaceY - 1, seaLevel - 1);
                    for (int y = groundY; y <= seaTop; y++) {
                        BlockState footing = ((mix(worldSeed ^ 3301L, lx, y, lz) & 1L) == 0L)
                                ? Blocks.BASALT.defaultBlockState()
                                : Blocks.SMOOTH_BASALT.defaultBlockState();
                        setBlockLocal(lx, y, lz, footing);
                    }
                }
            }
        }
        addSkirtSupportColumns(outer, skirtRadius);
    }

    private int computeSkirtSurfaceY(int lx, int lz, double r, int groundY) {
        int outer = settings.outerRadius();
        if (r <= outer) {
            return computeExteriorSurfaceY(lx, lz);
        }
        double scale = outer / Math.max(1.0D, r);
        int edgeX = (int) Math.round(lx * scale);
        int edgeZ = (int) Math.round(lz * scale);
        int edgeY = computeExteriorSurfaceY(edgeX, edgeZ) - 1;
        double t = Mth.clamp((r - outer) / 6.0D, 0.0D, 1.0D);
        int slopeTarget = groundY + 2;
        return (int) Math.round(Mth.lerp(t, edgeY, slopeTarget));
    }

    private void fillVerticalMass(int lx, int lz, int fromY, int toY, int skinThickness, boolean protectInterior) {
        int minY = Math.max(fromY, level.getMinBuildHeight());
        int maxY = Math.min(toY, level.getMaxBuildHeight() - 1);
        for (int y = minY; y <= maxY; y++) {
            if (protectInterior && isProtectedInterior(lx, y, lz)) {
                continue;
            }
            int depth = maxY - y;
            BlockState state;
            if (depth < skinThickness) {
                state = pickExterior(wx(lx), y, wz(lz));
            } else if ((depth % 7) == 0) {
                state = Blocks.BLACKSTONE.defaultBlockState();
            } else if ((depth & 1) == 0) {
                state = Blocks.BASALT.defaultBlockState();
            } else {
                state = Blocks.SMOOTH_BASALT.defaultBlockState();
            }
            setBlockLocal(lx, y, lz, state);
        }
    }

    private boolean isProtectedInterior(int lx, int y, int lz) {
        if (y < hubCenterY - 26 || y > hubCenterY + 16) {
            return false;
        }

        double hubNX = lx / (settings.hubRadius() + 2.0D);
        double hubNY = (y - hubCenterY) / (settings.hubHeight() / 2.0D + 2.0D);
        double hubNZ = lz / (settings.hubRadius() + 2.0D);
        if ((hubNX * hubNX) + (hubNY * hubNY) + (hubNZ * hubNZ) <= 1.2D) {
            return true;
        }

        double ringDist = Math.sqrt((double) lx * lx + (double) lz * lz);
        if (Math.abs(ringDist - settings.ringRadius()) <= settings.ringWidth() + 2.5D
                && y >= hubCenterY - 2
                && y <= hubCenterY + 6) {
            return true;
        }

        int floorY = hubCenterY - 1;
        int gateDistance = HUB_PLAZA_RADIUS + 3;
        for (HubExit exit : hubExits) {
            Direction dir = exit.direction();
            Direction right = dir.getClockWise();
            int startX = dir.getStepX() * gateDistance;
            int startZ = dir.getStepZ() * gateDistance;
            int dx = lx - startX;
            int dz = lz - startZ;
            int forward = dx * dir.getStepX() + dz * dir.getStepZ();
            int lateral = Math.abs(dx * right.getStepX() + dz * right.getStepZ());
            if (forward >= -2
                    && forward <= exit.endStep() + 3
                    && lateral <= (exit.width() / 2) + 2
                    && y >= floorY
                    && y <= floorY + exit.height() + 2) {
                return true;
            }
        }

        for (Pocket p : pockets) {
            int centerY = originY + p.localY;
            int hx = p.sizeX / 2 + 2;
            int hy = p.sizeY / 2 + 2;
            int hz = p.sizeZ / 2 + 2;
            if (Math.abs(lx - p.localX) <= hx && Math.abs(y - centerY) <= hy && Math.abs(lz - p.localZ) <= hz) {
                return true;
            }
        }

        if (settings.enableSecretTunnel() && lx >= 16 && lx <= 44 && Math.abs(lz) <= 3 && y >= hubCenterY - 8 && y <= hubCenterY + 5) {
            return true;
        }
        if (settings.enableSecretTunnel() && settings.enableRelicRoom()) {
            int cx = 45;
            int cy = originY + settings.hubCenterOffset() - 5;
            if (Math.abs(lx - cx) <= 8 && Math.abs(lz) <= 8 && Math.abs(y - cy) <= 5) {
                return true;
            }
        }
        return false;
    }

    private void addSkirtSupportColumns(int outer, int skirtRadius) {
        for (int lx = -skirtRadius; lx <= skirtRadius; lx += 4) {
            for (int lz = -skirtRadius; lz <= skirtRadius; lz += 4) {
                double r = Math.sqrt((double) lx * lx + (double) lz * lz);
                if (r < Math.max(outer - 1.0D, settings.hubRadius() + 6.0D) || r > skirtRadius + 0.5D) {
                    continue;
                }
                int groundY = sampledGroundY(lx, lz);
                int surfaceY = computeSkirtSurfaceY(lx, lz, r, groundY);
                if (surfaceY - groundY < 10) {
                    continue;
                }
                if ((mix(worldSeed ^ 3359L, lx, groundY, lz) & 3L) != 0L) {
                    continue;
                }
                buildSupportColumn(lx, lz, groundY, surfaceY - 1);
            }
        }
    }

    private void buildSupportColumn(int cx, int cz, int fromY, int toY) {
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                if ((ox * ox) + (oz * oz) > 4) {
                    continue;
                }
                for (int y = fromY; y <= toY; y++) {
                    int stripe = Math.floorMod(y - fromY, 6);
                    BlockState state;
                    if (stripe == 0) {
                        state = Blocks.BLACKSTONE.defaultBlockState();
                    } else if ((mix(worldSeed ^ 3391L, cx + ox, y, cz + oz) & 1L) == 0L) {
                        state = Blocks.BASALT.defaultBlockState();
                    } else {
                        state = Blocks.SMOOTH_BASALT.defaultBlockState();
                    }
                    setBlockLocal(cx + ox, y, cz + oz, state);
                }
            }
        }
    }

    private void sealAccidentalExteriorLeaks() {
        for (int attempt = 0; attempt < 3; attempt++) {
            int sealed = sealLeakPass();
            if (sealed == 0) {
                break;
            }
        }
    }

    private int sealLeakPass() {
        int radius = settings.outerRadius() + 10;
        int minX = origin.getX() - radius;
        int maxX = origin.getX() + radius;
        int minZ = origin.getZ() - radius;
        int maxZ = origin.getZ() + radius;
        int minY = Math.max(level.getMinBuildHeight(), hubCenterY - 24);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, originY + settings.height() + 8);

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        int plane = sizeY * sizeZ;
        int volume = sizeX * plane;

        boolean[] passable = new boolean[volume];
        boolean[] outsideAir = new boolean[volume];
        boolean[] interiorAir = new boolean[volume];
        int[] queue = new int[volume];

        for (int ix = 0; ix < sizeX; ix++) {
            int x = minX + ix;
            for (int iy = 0; iy < sizeY; iy++) {
                int y = minY + iy;
                for (int iz = 0; iz < sizeZ; iz++) {
                    int z = minZ + iz;
                    int idx = (ix * plane) + (iy * sizeZ) + iz;
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    passable[idx] = state.isAir() || !state.getFluidState().isEmpty();
                }
            }
        }

        int qHead = 0;
        int qTail = 0;
        for (int ix = 0; ix < sizeX; ix++) {
            for (int iy = 0; iy < sizeY; iy++) {
                for (int iz = 0; iz < sizeZ; iz++) {
                    boolean boundary = ix == 0 || ix == sizeX - 1 || iy == 0 || iy == sizeY - 1 || iz == 0 || iz == sizeZ - 1;
                    if (!boundary) {
                        continue;
                    }
                    int idx = (ix * plane) + (iy * sizeZ) + iz;
                    if (passable[idx] && !outsideAir[idx]) {
                        outsideAir[idx] = true;
                        queue[qTail++] = idx;
                    }
                }
            }
        }

        while (qHead < qTail) {
            int idx = queue[qHead++];
            int ix = idx / plane;
            int rem = idx % plane;
            int iy = rem / sizeZ;
            int iz = rem % sizeZ;

            if (ix > 0) {
                int n = idx - plane;
                if (passable[n] && !outsideAir[n]) {
                    outsideAir[n] = true;
                    queue[qTail++] = n;
                }
            }
            if (ix < sizeX - 1) {
                int n = idx + plane;
                if (passable[n] && !outsideAir[n]) {
                    outsideAir[n] = true;
                    queue[qTail++] = n;
                }
            }
            if (iy > 0) {
                int n = idx - sizeZ;
                if (passable[n] && !outsideAir[n]) {
                    outsideAir[n] = true;
                    queue[qTail++] = n;
                }
            }
            if (iy < sizeY - 1) {
                int n = idx + sizeZ;
                if (passable[n] && !outsideAir[n]) {
                    outsideAir[n] = true;
                    queue[qTail++] = n;
                }
            }
            if (iz > 0) {
                int n = idx - 1;
                if (passable[n] && !outsideAir[n]) {
                    outsideAir[n] = true;
                    queue[qTail++] = n;
                }
            }
            if (iz < sizeZ - 1) {
                int n = idx + 1;
                if (passable[n] && !outsideAir[n]) {
                    outsideAir[n] = true;
                    queue[qTail++] = n;
                }
            }
        }

        int anchorIdx = findInteriorAnchorIndex(minX, minY, minZ, sizeX, sizeY, sizeZ, plane, passable);
        if (anchorIdx < 0) {
            return 0;
        }

        qHead = 0;
        qTail = 0;
        interiorAir[anchorIdx] = true;
        queue[qTail++] = anchorIdx;
        while (qHead < qTail) {
            int idx = queue[qHead++];
            int ix = idx / plane;
            int rem = idx % plane;
            int iy = rem / sizeZ;
            int iz = rem % sizeZ;

            if (ix > 0) {
                int nx = ix - 1;
                int n = idx - plane;
                if (passable[n] && !interiorAir[n]) {
                    int wx = minX + nx;
                    int wy = minY + iy;
                    int wz = minZ + iz;
                    if (!isAllowedExteriorOpening(wx - origin.getX(), wy, wz - origin.getZ())) {
                        interiorAir[n] = true;
                        queue[qTail++] = n;
                    }
                }
            }
            if (ix < sizeX - 1) {
                int nx = ix + 1;
                int n = idx + plane;
                if (passable[n] && !interiorAir[n]) {
                    int wx = minX + nx;
                    int wy = minY + iy;
                    int wz = minZ + iz;
                    if (!isAllowedExteriorOpening(wx - origin.getX(), wy, wz - origin.getZ())) {
                        interiorAir[n] = true;
                        queue[qTail++] = n;
                    }
                }
            }
            if (iy > 0) {
                int ny = iy - 1;
                int n = idx - sizeZ;
                if (passable[n] && !interiorAir[n]) {
                    int wx = minX + ix;
                    int wy = minY + ny;
                    int wz = minZ + iz;
                    if (!isAllowedExteriorOpening(wx - origin.getX(), wy, wz - origin.getZ())) {
                        interiorAir[n] = true;
                        queue[qTail++] = n;
                    }
                }
            }
            if (iy < sizeY - 1) {
                int ny = iy + 1;
                int n = idx + sizeZ;
                if (passable[n] && !interiorAir[n]) {
                    int wx = minX + ix;
                    int wy = minY + ny;
                    int wz = minZ + iz;
                    if (!isAllowedExteriorOpening(wx - origin.getX(), wy, wz - origin.getZ())) {
                        interiorAir[n] = true;
                        queue[qTail++] = n;
                    }
                }
            }
            if (iz > 0) {
                int nz = iz - 1;
                int n = idx - 1;
                if (passable[n] && !interiorAir[n]) {
                    int wx = minX + ix;
                    int wy = minY + iy;
                    int wz = minZ + nz;
                    if (!isAllowedExteriorOpening(wx - origin.getX(), wy, wz - origin.getZ())) {
                        interiorAir[n] = true;
                        queue[qTail++] = n;
                    }
                }
            }
            if (iz < sizeZ - 1) {
                int nz = iz + 1;
                int n = idx + 1;
                if (passable[n] && !interiorAir[n]) {
                    int wx = minX + ix;
                    int wy = minY + iy;
                    int wz = minZ + nz;
                    if (!isAllowedExteriorOpening(wx - origin.getX(), wy, wz - origin.getZ())) {
                        interiorAir[n] = true;
                        queue[qTail++] = n;
                    }
                }
            }
        }

        boolean[] leakContacts = new boolean[volume];
        for (int idx = 0; idx < volume; idx++) {
            if (!interiorAir[idx]) {
                continue;
            }
            int ix = idx / plane;
            int rem = idx % plane;
            int iy = rem / sizeZ;
            int iz = rem % sizeZ;
            int wx = minX + ix;
            int y = minY + iy;
            int wz = minZ + iz;
            int lx = wx - origin.getX();
            int lz = wz - origin.getZ();
            if (isAllowedExteriorOpening(lx, y, lz)) {
                continue;
            }

            for (Direction dir : Direction.values()) {
                int nx = ix + dir.getStepX();
                int ny = iy + dir.getStepY();
                int nz = iz + dir.getStepZ();
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sizeX || ny >= sizeY || nz >= sizeZ) {
                    continue;
                }
                int nIdx = (nx * plane) + (ny * sizeZ) + nz;
                if (!outsideAir[nIdx] || interiorAir[nIdx]) {
                    continue;
                }
                int nxWorld = minX + nx;
                int nyWorld = minY + ny;
                int nzWorld = minZ + nz;
                int nlx = nxWorld - origin.getX();
                int nlz = nzWorld - origin.getZ();
                if (isAllowedExteriorOpening(nlx, nyWorld, nlz)) {
                    continue;
                }
                leakContacts[nIdx] = true;
            }
        }

        int sealed = 0;
        for (int idx = 0; idx < volume; idx++) {
            if (!leakContacts[idx]) {
                continue;
            }
            int ix = idx / plane;
            int rem = idx % plane;
            int iy = rem / sizeZ;
            int iz = rem % sizeZ;
            sealed += sealLeakPatch(
                    minX + ix,
                    minY + iy,
                    minZ + iz,
                    passable,
                    outsideAir,
                    interiorAir,
                    minX,
                    minY,
                    minZ,
                    sizeX,
                    sizeY,
                    sizeZ,
                    plane);
        }
        return sealed;
    }

    private int findInteriorAnchorIndex(
            int minX,
            int minY,
            int minZ,
            int sizeX,
            int sizeY,
            int sizeZ,
            int plane,
            boolean[] passable) {
        int anchorX = origin.getX();
        int anchorY = hubCenterY + 1;
        int anchorZ = origin.getZ();

        for (int radius = 0; radius <= 6; radius++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int x = anchorX + dx;
                        int y = anchorY + dy;
                        int z = anchorZ + dz;
                        int ix = x - minX;
                        int iy = y - minY;
                        int iz = z - minZ;
                        if (ix < 0 || iy < 0 || iz < 0 || ix >= sizeX || iy >= sizeY || iz >= sizeZ) {
                            continue;
                        }
                        int idx = (ix * plane) + (iy * sizeZ) + iz;
                        if (!passable[idx]) {
                            continue;
                        }
                        if (isAllowedExteriorOpening(x - origin.getX(), y, z - origin.getZ())) {
                            continue;
                        }
                        return idx;
                    }
                }
            }
        }
        return -1;
    }

    private int sealLeakPatch(
            int x,
            int y,
            int z,
            boolean[] passable,
            boolean[] outsideAir,
            boolean[] interiorAir,
            int minX,
            int minY,
            int minZ,
            int sizeX,
            int sizeY,
            int sizeZ,
            int plane) {
        int changed = 0;
        int patchRadius = 2;

        for (int ox = -patchRadius; ox <= patchRadius; ox++) {
            for (int oy = -patchRadius; oy <= patchRadius; oy++) {
                for (int oz = -patchRadius; oz <= patchRadius; oz++) {
                    if (Math.abs(ox) + Math.abs(oy) + Math.abs(oz) > 4) {
                        continue;
                    }
                    int px = x + ox;
                    int py = y + oy;
                    int pz = z + oz;
                    int ix = px - minX;
                    int iy = py - minY;
                    int iz = pz - minZ;
                    if (ix < 0 || iy < 0 || iz < 0 || ix >= sizeX || iy >= sizeY || iz >= sizeZ) {
                        continue;
                    }
                    int idx = (ix * plane) + (iy * sizeZ) + iz;
                    if (!outsideAir[idx] || interiorAir[idx]) {
                        continue;
                    }
                    int lx = px - origin.getX();
                    int lz = pz - origin.getZ();
                    if (isAllowedExteriorOpening(lx, py, lz) || !inShellBandForSealing(lx, py, lz)) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(px, py, pz);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.isAir() || !state.getFluidState().isEmpty())) {
                        continue;
                    }
                    setBlockWorld(px, py, pz, pickExterior(px, py, pz));
                    passable[idx] = false;
                    outsideAir[idx] = false;
                    changed++;
                }
            }
        }

        int towardX = Integer.compare(origin.getX(), x);
        int towardZ = Integer.compare(origin.getZ(), z);
        for (int depth = 1; depth <= 2; depth++) {
            int rx = x + towardX * depth;
            int rz = z + towardZ * depth;
            for (int ry = y - 1; ry <= y + 1; ry++) {
                int ix = rx - minX;
                int iy = ry - minY;
                int iz = rz - minZ;
                if (ix < 0 || iy < 0 || iz < 0 || ix >= sizeX || iy >= sizeY || iz >= sizeZ) {
                    continue;
                }
                int idx = (ix * plane) + (iy * sizeZ) + iz;
                if (interiorAir[idx]) {
                    continue;
                }
                int lx = rx - origin.getX();
                int lz = rz - origin.getZ();
                if (isAllowedExteriorOpening(lx, ry, lz) || !inShellBandForSealing(lx, ry, lz)) {
                    continue;
                }
                BlockPos pos = new BlockPos(rx, ry, rz);
                BlockState state = level.getBlockState(pos);
                if (!(state.isAir() || !state.getFluidState().isEmpty())) {
                    continue;
                }
                setBlockWorld(rx, ry, rz, pickExterior(rx, ry, rz));
                passable[idx] = false;
                outsideAir[idx] = false;
                changed++;
            }
        }
        return changed;
    }

    private boolean inShellBandForSealing(int lx, int y, int lz) {
        double r = Math.sqrt((double) lx * lx + (double) lz * lz);
        int minR = Math.max(settings.hubRadius() + 4, settings.ringRadius() + 4);
        int maxR = settings.outerRadius() + 8;
        return r >= minR && r <= maxR && y >= hubCenterY - 28 && y <= originY + settings.height() + 10;
    }

    private boolean isAllowedExteriorOpening(int lx, int y, int lz) {
        int floorY = hubCenterY - 1;
        int gateDistance = HUB_PLAZA_RADIUS + 3;
        for (HubExit exit : hubExits) {
            Direction dir = exit.direction();
            Direction right = dir.getClockWise();
            int startX = dir.getStepX() * gateDistance;
            int startZ = dir.getStepZ() * gateDistance;
            int dx = lx - startX;
            int dz = lz - startZ;
            int forward = dx * dir.getStepX() + dz * dir.getStepZ();
            int lateral = Math.abs(dx * right.getStepX() + dz * right.getStepZ());
            int minForward = Math.max(0, exit.endStep() - 10);
            int maxForward = exit.endStep() + 12;
            if (forward >= minForward
                    && forward <= maxForward
                    && lateral <= (exit.width() / 2) + 2
                    && y >= floorY - 1
                    && y <= floorY + exit.height() + 3) {
                return true;
            }
        }

        if (y >= originY + settings.height() - 10) {
            double r = Math.sqrt((double) lx * lx + (double) lz * lz);
            if (r <= settings.calderaRadiusTop() + 3.0D) {
                return true;
            }
        }

        int shaftX = 2;
        int shaftZ = settings.calderaRadiusTop() - 2;
        return Math.abs(lx - shaftX) <= 1
                && Math.abs(lz - shaftZ) <= 1
                && y >= hubCenterY + 1
                && y <= originY + settings.height();
    }

    private void blendVolcanoIntoTerrain() {
        int outer = settings.outerRadius();
        int blendMax = outer + TERRAIN_BLEND_RADIUS;

        for (int lx = -blendMax; lx <= blendMax; lx++) {
            for (int lz = -blendMax; lz <= blendMax; lz++) {
                double r = Math.sqrt((double) lx * lx + (double) lz * lz);
                if (r < outer || r > blendMax + 0.5D) {
                    continue;
                }

                int groundY = sampledGroundY(lx, lz);
                double edgeScale = outer / Math.max(1.0D, r);
                int edgeX = (int) Math.round(lx * edgeScale);
                int edgeZ = (int) Math.round(lz * edgeScale);
                int edgeY = computeExteriorSurfaceY(edgeX, edgeZ);
                int terraceDrop = (int) Math.floor((r - outer) / 4.0D);
                int volcanoY = edgeY - terraceDrop;
                double blendFactor = Mth.clamp(1.0D - ((r - outer) / TERRAIN_BLEND_RADIUS), 0.0D, 1.0D);
                int desiredY = (int) Math.round(Mth.lerp(blendFactor, groundY, volcanoY));

                if (desiredY > groundY) {
                    fillVerticalMass(lx, lz, groundY, desiredY, 2, false);
                }

                if (r >= outer + 4.0D && r <= outer + 12.0D && (mix(worldSeed ^ 3457L, lx, groundY, lz) & 3L) == 0L) {
                    int topY = Math.max(groundY, desiredY);
                    int x = wx(lx);
                    int z = wz(lz);
                    BlockPos top = new BlockPos(x, topY, z);
                    if (!level.getBlockState(top).isAir() && level.getBlockState(top.above()).isAir()) {
                        setBlockWorld(x, topY, z, pickTalusBlock(x, topY, z));
                    }
                }
            }
        }
    }

    private BlockState pickTalusBlock(int x, int y, int z) {
        return switch ((int) Math.floorMod(mix(worldSeed ^ 3527L, x, y, z), 4)) {
            case 0 -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            case 1 -> Blocks.TUFF.defaultBlockState();
            case 2 -> Blocks.COBBLESTONE.defaultBlockState();
            default -> Blocks.GRAVEL.defaultBlockState();
        };
    }

    private void skinAndFloorPass() {
        int radius = settings.outerRadius() + 2;
        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                for (int y = hubCenterY - 18; y <= originY + settings.height(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (exposedToAir(x, y, z)) {
                        setBlockWorld(x, y, z, pickInterior(x, y, z));
                    }
                }
            }
        }

        int floorY = hubCenterY - 1;
        int outer = settings.outerRadius();
        for (int lx = -outer; lx <= outer; lx++) {
            for (int lz = -outer; lz <= outer; lz++) {
                if (!civilizedZone(lx, lz)) {
                    continue;
                }
                int x = wx(lx);
                int z = wz(lz);
                BlockPos floor = new BlockPos(x, floorY, z);
                if (!level.getBlockState(floor).isAir() && level.getBlockState(floor.above()).isAir()) {
                    setBlockWorld(x, floorY, z, pickFloor(x, floorY, z));
                }
            }
        }
    }

    private boolean exposedToAir(int x, int y, int z) {
        for (Direction dir : Direction.values()) {
            BlockPos n1 = new BlockPos(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
            BlockPos n2 = new BlockPos(x + dir.getStepX() * 2, y + dir.getStepY() * 2, z + dir.getStepZ() * 2);
            if (level.getBlockState(n1).isAir() || level.getBlockState(n2).isAir()) {
                return true;
            }
        }
        return false;
    }

    private boolean civilizedZone(int lx, int lz) {
        double dist = Math.sqrt((double) lx * lx + (double) lz * lz);
        if (dist <= HUB_PLAZA_RADIUS + 0.5D) {
            return true;
        }
        if (Math.abs(dist - settings.ringRadius()) <= settings.ringWidth() + 1.5D) {
            return true;
        }

        int gateDistance = HUB_PLAZA_RADIUS + 3;
        for (HubExit exit : hubExits) {
            Direction dir = exit.direction();
            Direction right = dir.getClockWise();
            int startX = dir.getStepX() * gateDistance;
            int startZ = dir.getStepZ() * gateDistance;
            int dx = lx - startX;
            int dz = lz - startZ;
            int forward = dx * dir.getStepX() + dz * dir.getStepZ();
            int lateral = Math.abs(dx * right.getStepX() + dz * right.getStepZ());

            if (forward >= 0 && forward <= exit.endStep() + 8 && lateral <= (exit.width() / 2) + 1) {
                return true;
            }
        }

        for (Pocket p : pockets) {
            if (Math.abs(lx - p.localX) <= p.sizeX / 2 && Math.abs(lz - p.localZ) <= p.sizeZ / 2) {
                return true;
            }
        }
        return settings.enableSecretTunnel() && lx >= 18 && lx <= 42 && Math.abs(lz) <= 1;
    }

    private void buildSettlement() {
        buildPlazaAndBridges();
        buildGateMouths();
        buildHubWallCrest();
        buildExitLandings();
        buildHeatChains();
        buildStalls();
        buildBuildings();
    }

    private void buildPlazaAndBridges() {
        int plazaY = hubCenterY - 1;
        int plazaRadiusSq = HUB_PLAZA_RADIUS * HUB_PLAZA_RADIUS;
        for (int lx = -HUB_PLAZA_RADIUS; lx <= HUB_PLAZA_RADIUS; lx++) {
            for (int lz = -HUB_PLAZA_RADIUS; lz <= HUB_PLAZA_RADIUS; lz++) {
                if ((lx * lx) + (lz * lz) <= plazaRadiusSq) {
                    setBlockLocal(lx, plazaY, lz, pickFloor(wx(lx), plazaY, wz(lz)));
                }
            }
        }

        buildRuneCompassDais(plazaY);
        buildPlazaEdgeBand(plazaY);
        buildBreadcrumbLines(plazaY);
        buildHeatVeins(plazaY);

        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            Direction side = dir.getClockWise();
            for (int d = HUB_PLAZA_RADIUS; d <= settings.ringRadius(); d++) {
                for (int w = -1; w <= 1; w++) {
                    int lx = (dir.getStepX() * d) + (side.getStepX() * w);
                    int lz = (dir.getStepZ() * d) + (side.getStepZ() * w);
                    setBlockLocal(lx, plazaY, lz, pickFloor(wx(lx), plazaY, wz(lz)));
                }
            }
        }
    }

    private void buildRuneCompassDais(int plazaY) {
        int centerRadius = 7;
        int centerSq = centerRadius * centerRadius;
        for (int lx = -centerRadius; lx <= centerRadius; lx++) {
            for (int lz = -centerRadius; lz <= centerRadius; lz++) {
                if ((lx * lx) + (lz * lz) > centerSq) {
                    continue;
                }
                BlockState base = Math.floorMod(mix(worldSeed ^ 4591L, wx(lx), plazaY, wz(lz)), 9L) == 0L
                        ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : Blocks.DEEPSLATE_TILES.defaultBlockState();
                setBlockLocal(lx, plazaY, lz, base);
                setAirLocal(lx, plazaY + 1, lz);
                setAirLocal(lx, plazaY + 2, lz);
            }
        }
    }

    private void buildPlazaEdgeBand(int plazaY) {
        int inner = HUB_PLAZA_RADIUS;
        int outer = HUB_PLAZA_RADIUS + 4;
        int limit = outer + 1;
        int breakA = (int) Math.floorMod(mix(worldSeed ^ 4639L, plazaY, inner, outer), 32L);
        int breakB = (breakA + 9) % 32;
        int breakC = (breakA + 18) % 32;

        for (int lx = -limit; lx <= limit; lx++) {
            for (int lz = -limit; lz <= limit; lz++) {
                double r = Math.sqrt((double) lx * lx + (double) lz * lz);
                if (r < inner - 0.15D || r > outer + 0.6D) {
                    continue;
                }

                BlockState floor = (Math.floorMod(mix(worldSeed ^ 4651L, wx(lx), plazaY, wz(lz)), 7L) == 0L)
                        ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                        : Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                setBlockLocal(lx, plazaY, lz, floor);

                if (r < outer - 0.4D || r > outer + 0.4D || isPlazaGateApproach(lx, lz, inner, outer)) {
                    continue;
                }

                int segment = (int) Math.floor(
                        Math.floorMod((long) Math.floor(Math.toDegrees(Math.atan2(lz, lx)) + 360.0D), 360L) / 11.25D);
                boolean brokenGap = segment == breakA || segment == breakB || segment == breakC;
                if (brokenGap) {
                    continue;
                }

                if ((segment & 1) == 0) {
                    setBlockLocal(lx, plazaY + 1, lz, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
                } else {
                    BlockState railing = ((segment / 3) & 1) == 0
                            ? Blocks.IRON_BARS.defaultBlockState()
                            : Blocks.CHAIN.defaultBlockState();
                    setBlockLocal(lx, plazaY + 1, lz, railing);
                }
            }
        }
    }

    private boolean isPlazaGateApproach(int lx, int lz, int inner, int outer) {
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            Direction right = dir.getClockWise();
            int forward = lx * dir.getStepX() + lz * dir.getStepZ();
            int lateral = Math.abs(lx * right.getStepX() + lz * right.getStepZ());
            if (forward >= inner - 1 && forward <= outer + 2 && lateral <= 2) {
                return true;
            }
        }
        return false;
    }

    private void buildBreadcrumbLines(int plazaY) {
        int gateDistance = HUB_PLAZA_RADIUS + 3;
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            Direction right = dir.getClockWise();
            for (int d = 3; d <= gateDistance - 2; d++) {
                int lx = dir.getStepX() * d;
                int lz = dir.getStepZ() * d;
                BlockState line = (d & 1) == 0
                        ? bottomSlab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB)
                        : bottomSlab(Blocks.DEEPSLATE_BRICK_SLAB);
                setBlockLocal(lx, plazaY, lz, line);
            }

            int tipDistance = gateDistance - 1;
            int tipX = dir.getStepX() * tipDistance;
            int tipZ = dir.getStepZ() * tipDistance;
            BlockState arrow = Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState();
            if (arrow.hasProperty(StairBlock.FACING)) {
                arrow = arrow.setValue(StairBlock.FACING, dir);
            }
            setBlockLocal(tipX, plazaY, tipZ, arrow);

            int wingDistance = gateDistance - 2;
            for (int side : List.of(-1, 1)) {
                int wx = dir.getStepX() * wingDistance + right.getStepX() * side;
                int wz = dir.getStepZ() * wingDistance + right.getStepZ() * side;
                setBlockLocal(wx, plazaY, wz, arrow);
            }
        }
    }

    private void buildHeatVeins(int plazaY) {
        int startDistance = 6;
        int endDistance = settings.ringRadius() + 1;
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            for (int d = startDistance; d <= endDistance; d++) {
                int lx = dir.getStepX() * d;
                int lz = dir.getStepZ() * d;

                BlockState inlay = ((d + plazaY) & 1) == 0
                        ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : Blocks.DEEPSLATE_TILES.defaultBlockState();
                if (Math.floorMod(mix(worldSeed ^ 6043L, d, plazaY, dir.ordinal()), 7L) == 0L) {
                    inlay = Blocks.GILDED_BLACKSTONE.defaultBlockState();
                }
                setBlockLocal(lx, plazaY, lz, inlay);

                if ((d - startDistance) % 3 != 0) {
                    continue;
                }

                setBlockLocal(lx, plazaY, lz, Blocks.IRON_BARS.defaultBlockState());
                placeSealedRitualPocket(lx, plazaY, lz);
                setAirLocal(lx, plazaY + 1, lz);
            }
        }
    }

    private void buildGateMouths() {
        int gateDistance = HUB_PLAZA_RADIUS + 3;
        buildSingleGateMouth(Direction.NORTH, gateDistance);
        buildSingleGateMouth(Direction.SOUTH, gateDistance);
        buildSingleGateMouth(Direction.WEST, gateDistance);
        buildSingleGateMouth(Direction.EAST, gateDistance);
    }

    private void buildHubWallCrest() {
        int wallDistance = Math.max(settings.hubRadius() - 2, settings.ringRadius() + 3);
        int centerZ = -wallDistance;
        int baseY = hubCenterY + 2;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy < 5; dy++) {
                int y = baseY + dy;
                int lx = dx;
                int lz = centerZ;
                boolean carve = (Math.abs(dx) == 2 && dy == 4)
                        || (Math.abs(dx) == 2 && dy <= 1)
                        || (Math.abs(dx) == 1 && dy == 0)
                        || (dx == 0 && dy == 4);
                if (carve) {
                    setAirLocal(lx, y, lz);
                } else {
                    BlockState skull = ((dx + dy) & 1) == 0
                            ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                            : Blocks.DEEPSLATE_TILES.defaultBlockState();
                    setBlockLocal(lx, y, lz, skull);
                }
            }
        }

        placeSealedWallVent(-1, baseY + 2, centerZ, Direction.NORTH);
        placeSealedWallVent(1, baseY + 2, centerZ, Direction.NORTH);
    }

    private void buildSingleGateMouth(Direction dir, int gateDistance) {
        int centerX = dir.getStepX() * gateDistance;
        int centerZ = dir.getStepZ() * gateDistance;
        int baseY = hubCenterY - 1;
        Direction right = dir.getClockWise();
        int openingHalf = 2;
        int frameHalf = 4;

        for (int depth = 0; depth < 2; depth++) {
            int depthX = centerX + dir.getStepX() * depth;
            int depthZ = centerZ + dir.getStepZ() * depth;
            for (int w = -frameHalf; w <= frameHalf; w++) {
                int lx = depthX + right.getStepX() * w;
                int lz = depthZ + right.getStepZ() * w;
                for (int y = baseY; y <= baseY + 7; y++) {
                    boolean opening = Math.abs(w) <= openingHalf && y >= baseY + 1 && y <= baseY + 5;
                    if (opening) {
                        setAirLocal(lx, y, lz);
                    } else {
                        setBlockLocal(lx, y, lz, ((w + y + depth) & 1) == 0
                                ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                                : Blocks.DEEPSLATE_TILES.defaultBlockState());
                    }
                }
            }
            // Crown notch
            setAirLocal(depthX, baseY + 7, depthZ);
        }
    }

    private void buildExitLandings() {
        for (HubExit exit : hubExits) {
            buildLanding(exit);
        }
    }

    private void buildLanding(HubExit exit) {
        Direction dir = exit.direction();
        Direction right = dir.getClockWise();
        int floorY = hubCenterY - 1;
        int centerX = exit.endX() + dir.getStepX() * 2;
        int centerZ = exit.endZ() + dir.getStepZ() * 2;

        int width = exit.smallSouth() ? 5 : 7;
        int depth = exit.smallSouth() ? 4 : 5;
        if (dir == Direction.NORTH) {
            width = 9;
            depth = 6;
        }

        int half = width / 2;
        for (int f = 0; f < depth; f++) {
            for (int w = -half; w <= half; w++) {
                int lx = centerX + dir.getStepX() * f + right.getStepX() * w;
                int lz = centerZ + dir.getStepZ() * f + right.getStepZ() * w;
                setBlockLocal(lx, floorY, lz, pickFloor(wx(lx), floorY, wz(lz)));
                for (int y = floorY + 1; y <= floorY + 3; y++) {
                    setAirLocal(lx, y, lz);
                }
            }
        }

        for (int f = 0; f < depth; f++) {
            for (int side : List.of(-half, half)) {
                int lx = centerX + dir.getStepX() * f + right.getStepX() * side;
                int lz = centerZ + dir.getStepZ() * f + right.getStepZ() * side;
                setBlockLocal(lx, floorY + 1, lz, Blocks.BLACKSTONE_WALL.defaultBlockState());
            }
        }

        int frontX = centerX + dir.getStepX() * (depth - 1);
        int frontZ = centerZ + dir.getStepZ() * (depth - 1);
        setBlockLocal(frontX + right.getStepX() * (half - 1), floorY + 1, frontZ, Blocks.BLACKSTONE_WALL.defaultBlockState());
        setBlockLocal(frontX - right.getStepX() * (half - 1), floorY + 1, frontZ, Blocks.BLACKSTONE_WALL.defaultBlockState());

        for (int p = 1; p <= 12; p++) {
            int lx = frontX + dir.getStepX() * p;
            int lz = frontZ + dir.getStepZ() * p;
            setBlockLocal(lx, floorY, lz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            setAirLocal(lx, floorY + 1, lz);
        }

        if (dir == Direction.WEST) {
            setBlockLocal(frontX, floorY + 1, frontZ, Blocks.BASALT.defaultBlockState());
            setBlockLocal(frontX, floorY + 2, frontZ, Blocks.BASALT.defaultBlockState());
            setBlockLocal(frontX, floorY + 3, frontZ, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        } else if (dir == Direction.EAST) {
            int archDepth = 2;
            for (int d = 0; d <= archDepth; d++) {
                int ax = frontX + dir.getStepX() * d;
                int az = frontZ + dir.getStepZ() * d;
                for (int w = -2; w <= 2; w++) {
                    int lx = ax + right.getStepX() * w;
                    int lz = az + right.getStepZ() * w;
                    setBlockLocal(lx, floorY + 1, lz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    if (Math.abs(w) == 2) {
                        setBlockLocal(lx, floorY + 2, lz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    }
                }
                setAirLocal(ax, floorY + 2, az);
            }
        }
    }

    private void buildHeatChains() {
        int floorY = hubCenterY - 1;
        int count = 8;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D) * i / count;
            int lx = (int) Math.round(Math.cos(angle) * 11.0D);
            int lz = (int) Math.round(Math.sin(angle) * 11.0D);
            int startY = findRuneChainAnchorY(lx, lz, floorY + 10, floorY + 24);
            int endY = floorY + 6 + (int) Math.floorMod(mix(worldSeed ^ 1213L, i, floorY, 11), 5L);
            if (endY >= startY - 1) {
                endY = Math.max(floorY + 6, startY - 3);
            }
            for (int y = startY - 1; y >= endY; y--) {
                setBlockLocal(lx, y, lz, Blocks.CHAIN.defaultBlockState());
            }
        }
    }

    private void buildStalls() {
        int count = settings.stallCount();
        if (count <= 0) {
            return;
        }
        int baseY = hubCenterY - 1;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D) * i / count;
            int centerX = (int) Math.round(Math.cos(angle) * settings.ringRadius());
            int centerZ = (int) Math.round(Math.sin(angle) * settings.ringRadius());
            Direction face = directionToCenter(centerX, centerZ);

            for (int rx = -2; rx <= 2; rx++) {
                for (int rz = 0; rz <= 3; rz++) {
                    LocalXZ p = stallOffset(centerX, centerZ, rx, rz, face);
                    setBlockLocal(p.x, baseY, p.z, pickFloor(wx(p.x), baseY, wz(p.z)));
                }
            }
            for (int rx : List.of(-2, 2)) {
                for (int rz : List.of(0, 3)) {
                    LocalXZ p = stallOffset(centerX, centerZ, rx, rz, face);
                    for (int y = baseY + 1; y <= baseY + 3; y++) {
                        setBlockLocal(p.x, y, p.z, wood.strippedLog.defaultBlockState());
                    }
                }
            }
            for (int rx = -3; rx <= 3; rx++) {
                for (int rz = -1; rz <= 4; rz++) {
                    LocalXZ p = stallOffset(centerX, centerZ, rx, rz, face);
                    setBlockLocal(p.x, baseY + 4, p.z, (Math.abs(rx) == 3 || rz == -1 || rz == 4) ? bottomSlab(wood.slab) : wood.planks.defaultBlockState());
                }
            }
            for (int rx = -1; rx <= 1; rx++) {
                LocalXZ p = stallOffset(centerX, centerZ, rx, 0, face);
                BlockState stairs = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState();
                if (stairs.hasProperty(StairBlock.FACING)) {
                    stairs = stairs.setValue(StairBlock.FACING, face);
                }
                setBlockLocal(p.x, baseY + 1, p.z, stairs);
            }
        }
    }

    private void buildBuildings() {
        List<BuildingSpec> buildings = List.of(
                new BuildingSpec("inn", -25, 5, 9, 9),
                new BuildingSpec("storage", -20, -18, 9, 7),
                new BuildingSpec("story", 25, -5, 9, 9),
                new BuildingSpec("forge", 20, 18, 9, 7));
        for (BuildingSpec b : buildings) {
            buildBuilding(b);
        }
    }

    private void buildBuilding(BuildingSpec b) {
        int baseY = hubCenterY - 1;
        int hw = b.width / 2;
        int hd = b.depth / 2;
        for (int lx = b.x - hw; lx <= b.x + hw; lx++) {
            for (int lz = b.z - hd; lz <= b.z + hd; lz++) {
                setBlockLocal(lx, baseY, lz, pickFloor(wx(lx), baseY, wz(lz)));
                boolean edge = Math.abs(lx - b.x) == hw || Math.abs(lz - b.z) == hd;
                if (edge) {
                    for (int y = baseY + 1; y <= baseY + 4; y++) {
                        setBlockLocal(lx, y, lz, wood.planks.defaultBlockState());
                    }
                } else {
                    for (int y = baseY + 1; y <= baseY + 4; y++) {
                        setAirLocal(lx, y, lz);
                    }
                }
            }
        }
        for (int lx = b.x - hw - 1; lx <= b.x + hw + 1; lx++) {
            for (int lz = b.z - hd - 1; lz <= b.z + hd + 1; lz++) {
                boolean edge = Math.abs(lx - b.x) == hw + 1 || Math.abs(lz - b.z) == hd + 1;
                setBlockLocal(lx, baseY + 5, lz, edge ? bottomSlab(wood.slab) : wood.planks.defaultBlockState());
            }
        }

        Direction doorFacing = directionToCenter(b.x, b.z);
        int edgeDist = doorFacing.getAxis() == Direction.Axis.X ? hw : hd;
        int doorX = b.x + doorFacing.getStepX() * edgeDist;
        int doorZ = b.z + doorFacing.getStepZ() * edgeDist;
        setAirLocal(doorX, baseY + 1, doorZ);
        setAirLocal(doorX, baseY + 2, doorZ);
        placeDoor(doorX, baseY + 1, doorZ, doorFacing);

        if ("storage".equals(b.name)) {
            setBlockLocal(b.x, baseY + 1, b.z, Blocks.CRAFTING_TABLE.defaultBlockState());
            setBlockLocal(b.x + 1, baseY + 1, b.z, Blocks.ANVIL.defaultBlockState());
        } else if ("story".equals(b.name)) {
            setBlockLocal(b.x, baseY + 1, b.z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            setBlockLocal(b.x, baseY + 2, b.z, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
        } else if ("forge".equals(b.name)) {
            setBlockLocal(b.x - 1, baseY + 1, b.z, Blocks.BLAST_FURNACE.defaultBlockState());
            setBlockLocal(b.x, baseY + 1, b.z, Blocks.SMOKER.defaultBlockState());
            setBlockLocal(b.x + 1, baseY + 1, b.z, Blocks.ANVIL.defaultBlockState());
            forgeBuilding = b;
        }
    }

    private void lightingPass() {
        int plazaY = hubCenterY - 1;
        cleanupHubLanternSpam(plazaY);
        buildCentralCoreReceiver(plazaY);
        placeLanternConstellation(plazaY);
        lightGateSignatures(plazaY);
        placeWallAlcoveSconces(plazaY);
        lightStallClusters(plazaY);
        lightTunnels(plazaY);
        validateCriticalLighting();
    }

    private void cleanupHubLanternSpam(int plazaY) {
        int radius = settings.ringRadius() + 18;
        int radiusSq = radius * radius;
        for (int lx = -radius; lx <= radius; lx++) {
            for (int lz = -radius; lz <= radius; lz++) {
                if ((lx * lx) + (lz * lz) > radiusSq) {
                    continue;
                }
                for (int y = plazaY; y <= plazaY + 6; y++) {
                    BlockPos pos = new BlockPos(wx(lx), y, wz(lz));
                    if (level.getBlockState(pos).is(Blocks.LANTERN)) {
                        setAirLocal(lx, y, lz);
                    }
                }
            }
        }
    }

    private void buildCentralCoreReceiver(int plazaY) {
        for (int lx = -2; lx <= 2; lx++) {
            for (int lz = -2; lz <= 2; lz++) {
                if ((lx * lx) + (lz * lz) <= 4) {
                    setAirLocal(lx, plazaY + 1, lz);
                    setAirLocal(lx, plazaY + 2, lz);
                }
            }
        }
    }

    private void placeLanternConstellation(int plazaY) {
        int centerY = plazaY + 10 + (int) Math.floorMod(mix(worldSeed ^ 7319L, 0, plazaY, 0), 2L);
        int cardinalBaseY = centerY + 1;

        List<ConstellationLamp> lamps = List.of(
                new ConstellationLamp(0, 0, centerY, 3),
                new ConstellationLamp(8, 0, cardinalBaseY + (int) Math.floorMod(mix(worldSeed ^ 7321L, 1, plazaY, 0), 2L), 3),
                new ConstellationLamp(-8, 0, cardinalBaseY + (int) Math.floorMod(mix(worldSeed ^ 7323L, 2, plazaY, 0), 2L), 3));

        for (ConstellationLamp lamp : lamps) {
            int anchorY = findRuneChainAnchorY(lamp.lx(), lamp.lz(), lamp.lanternY() + 2, lamp.lanternY() + 10);
            if (anchorY <= lamp.lanternY()) {
                anchorY = lamp.lanternY() + lamp.chainLen();
            }
            buildLanternAnchorPlateLocal(lamp.lx(), anchorY, lamp.lz());
            for (int y = lamp.lanternY() + 1; y < anchorY; y++) {
                setBlockLocal(lamp.lx(), y, lamp.lz(), Blocks.CHAIN.defaultBlockState());
            }
            placeHangingLanternLocal(lamp.lx(), lamp.lanternY(), lamp.lz());
        }
    }

    private void lightGateSignatures(int plazaY) {
        int gateDistance = HUB_PLAZA_RADIUS + 3;
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int gx = dir.getStepX() * gateDistance;
            int gz = dir.getStepZ() * gateDistance;
            Direction right = dir.getClockWise();

            if (dir == Direction.NORTH) {
                for (int side : List.of(-1, 1)) {
                    int px = gx + right.getStepX() * 3 * side;
                    int pz = gz + right.getStepZ() * 3 * side;
                    setBlockLocal(px, plazaY + 1, pz, Blocks.BASALT.defaultBlockState());
                    setBlockLocal(px, plazaY + 2, pz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                    setBlockLocal(px, plazaY + 3, pz, Blocks.CANDLE.defaultBlockState());
                }
                continue;
            }

            if (dir == Direction.EAST) {
                setBlockLocal(gx, plazaY + 6, gz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                setBlockLocal(gx, plazaY + 5, gz, Blocks.CHAIN.defaultBlockState());
                placeHangingLanternLocal(gx, plazaY + 4, gz);
                continue;
            }

            if (dir == Direction.WEST) {
                for (int side : List.of(-1, 1)) {
                    int px = gx + right.getStepX() * 3 * side;
                    int pz = gz + right.getStepZ() * 3 * side;
                    setBlockLocal(px, plazaY + 1, pz, Blocks.BASALT.defaultBlockState());
                    setBlockLocal(px, plazaY + 2, pz, Blocks.BASALT.defaultBlockState());
                    setBlockLocal(px, plazaY + 3, pz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                }
                continue;
            }

            int ventX = gx + right.getStepX() * 4;
            int ventZ = gz + right.getStepZ() * 4;
            setBlockLocal(ventX, plazaY, ventZ, Blocks.IRON_BARS.defaultBlockState());
            setBlockLocal(ventX, plazaY - 1, ventZ, Blocks.MAGMA_BLOCK.defaultBlockState());
            for (Direction seal : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
                int sx = ventX + seal.getStepX();
                int sz = ventZ + seal.getStepZ();
                BlockPos side = new BlockPos(wx(sx), plazaY - 1, wz(sz));
                if (!level.getBlockState(side).blocksMotion()) {
                    setBlockLocal(sx, plazaY - 1, sz, Blocks.BASALT.defaultBlockState());
                }
            }
        }
    }

    private void placeWallAlcoveSconces(int plazaY) {
        int radius = Math.max(settings.hubRadius() - 1, settings.ringRadius() + 2);
        for (int i = 0; i < 6; i++) {
            double angle = ((Math.PI * 2.0D) * i / 6.0D) + (Math.PI / 6.0D);
            int sx = (int) Math.round(Math.cos(angle) * radius);
            int sz = (int) Math.round(Math.sin(angle) * radius);
            int outX = Integer.compare(sx, 0);
            int outZ = Integer.compare(sz, 0);
            if (Math.abs(sx) > Math.abs(sz)) {
                outZ = 0;
            } else {
                outX = 0;
            }

            int frontX = sx;
            int frontZ = sz;
            int y = plazaY + 2;
            Direction outward = outX > 0
                    ? Direction.EAST
                    : outX < 0 ? Direction.WEST : outZ > 0 ? Direction.SOUTH : Direction.NORTH;

            placeSealedWallVent(frontX, y, frontZ, outward);
            setBlockLocal(frontX, y + 1, frontZ, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            setBlockLocal(frontX, y - 1, frontZ, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
    }

    private void lightStallClusters(int plazaY) {
        int r = settings.ringRadius();
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            Direction right = dir.getClockWise();
            int lx = dir.getStepX() * (r - 1) + right.getStepX() * 2;
            int lz = dir.getStepZ() * (r - 1) + right.getStepZ() * 2;
            setBlockLocal(lx, plazaY + 1, lz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            setBlockLocal(lx, plazaY + 2, lz, Blocks.IRON_BARS.defaultBlockState());
            placeSealedRitualPocket(lx, plazaY + 2, lz);
        }
    }

    private void lightTunnels(int plazaY) {
        int gateDistance = HUB_PLAZA_RADIUS + 3;
        for (HubExit exit : hubExits) {
            Direction dir = exit.direction();
            Direction right = dir.getClockWise();
            int startX = dir.getStepX() * gateDistance;
            int startZ = dir.getStepZ() * gateDistance;
            int half = exit.width() / 2;

            for (int step = 2; step <= exit.endStep(); step += 7) {
                int cx = startX + dir.getStepX() * step;
                int cz = startZ + dir.getStepZ() * step;
                int side = ((step / 7) & 1) == 0 ? -1 : 1;
                placeTunnelRecessedLight(cx, cz, plazaY + 2, right, side, half);
            }

            for (int step = 7; step <= exit.endStep(); step += 14) {
                int cx = startX + dir.getStepX() * step;
                int cz = startZ + dir.getStepZ() * step;
                int side = ((step / 14) & 1) == 0 ? 1 : -1;
                placeTunnelRecessedLight(cx, cz, plazaY + 3, right, side, half);
            }
        }
    }

    private void placeTunnelRecessedLight(int cx, int cz, int y, Direction right, int side, int half) {
        int faceX = cx + right.getStepX() * (half + 1) * side;
        int faceZ = cz + right.getStepZ() * (half + 1) * side;
        Direction outward = side > 0 ? right : right.getOpposite();
        placeSealedWallVent(faceX, y, faceZ, outward);
        if (level.getBlockState(new BlockPos(wx(faceX), y - 1, wz(faceZ))).isAir()) {
            setBlockLocal(faceX, y - 1, faceZ, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
    }

    private void validateCriticalLighting() {
        int floorY = hubCenterY - 1;
        int plazaRadiusSq = HUB_PLAZA_RADIUS * HUB_PLAZA_RADIUS;
        for (int lx = -HUB_PLAZA_RADIUS; lx <= HUB_PLAZA_RADIUS; lx += 4) {
            for (int lz = -HUB_PLAZA_RADIUS; lz <= HUB_PLAZA_RADIUS; lz += 4) {
                if ((lx * lx) + (lz * lz) <= plazaRadiusSq) {
                    ensureMinLight(lx, floorY, lz, 9);
                }
            }
        }
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0D) * i / 12.0D;
            int lx = (int) Math.round(Math.cos(angle) * settings.ringRadius());
            int lz = (int) Math.round(Math.sin(angle) * settings.ringRadius());
            ensureMinLight(lx, floorY, lz, 8);
        }
    }

    private void ensureMinLight(int lx, int floorY, int lz, int min) {
        BlockPos sample = new BlockPos(wx(lx), floorY + 1, wz(lz));
        if (level.getBrightness(LightLayer.BLOCK, sample) < min) {
            if (!placeNearbyHiddenAlcoveLight(lx, floorY + 2, lz)) {
                placeLantern(lx, floorY + 3, lz);
            }
        }
    }

    private boolean placeNearbyHiddenAlcoveLight(int lx, int y, int lz) {
        for (int radius = 4; radius <= 8; radius++) {
            for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
                int fx = lx + dir.getStepX() * radius;
                int fz = lz + dir.getStepZ() * radius;
                BlockPos backPos = new BlockPos(wx(fx + dir.getStepX()), y, wz(fz + dir.getStepZ()));
                if (!level.getBlockState(backPos).blocksMotion()) {
                    continue;
                }
                placeSealedWallVent(fx, y, fz, dir);
                return true;
            }
        }
        return false;
    }

    private void addLavaFeatures() {
        addCalderaGlow();
        createLavaWindow(0, hubCenterY, 3, Direction.SOUTH, 5);
        if (forgeBuilding != null) {
            Direction back = directionToCenter(forgeBuilding.x, forgeBuilding.z).getOpposite();
            createLavaWindow(
                    forgeBuilding.x + back.getStepX() * 2,
                    hubCenterY,
                    forgeBuilding.z + back.getStepZ() * 2,
                    back,
                    3);
        }
        int ring = settings.ringRadius();
        for (LocalXZ p : List.of(new LocalXZ(ring, 0), new LocalXZ(-ring, 0), new LocalXZ(0, ring), new LocalXZ(0, -ring))) {
            for (int ox = 0; ox < 2; ox++) {
                for (int oz = 0; oz < 2; oz++) {
                    setBlockLocal(p.x + ox, hubCenterY - 2, p.z + oz, Blocks.MAGMA_BLOCK.defaultBlockState());
                    setBlockLocal(p.x + ox, hubCenterY - 1, p.z + oz, Blocks.IRON_BARS.defaultBlockState());
                }
            }
        }
    }

    private void addCalderaGlow() {
        int y = originY + settings.height() - 10;
        int radius = Math.max(4, settings.calderaRadiusTop() - 8);
        for (int lx = -radius; lx <= radius; lx++) {
            for (int lz = -radius; lz <= radius; lz++) {
                double r = Math.sqrt((double) lx * lx + (double) lz * lz);
                setBlockLocal(lx, y - 1, lz, Blocks.BASALT.defaultBlockState());
                if (r <= radius - 1) {
                    setLavaLocal(lx, y, lz);
                } else {
                    setBlockLocal(lx, y, lz, Blocks.BASALT.defaultBlockState());
                }
            }
        }
    }

    private void createLavaWindow(int centerX, int centerY, int centerZ, Direction facing, int length) {
        Direction right = facing.getClockWise();
        int half = length / 2;
        for (int i = -half; i <= half; i++) {
            int barX = centerX + right.getStepX() * i;
            int barZ = centerZ + right.getStepZ() * i;
            int y = centerY + 1;
            setBlockLocal(barX, y, barZ, Blocks.IRON_BARS.defaultBlockState());
            int lavaX = barX + facing.getStepX();
            int lavaZ = barZ + facing.getStepZ();
            setBlockLocal(lavaX, y - 1, lavaZ, Blocks.BASALT.defaultBlockState());
            setBlockLocal(lavaX, y + 1, lavaZ, Blocks.BASALT.defaultBlockState());
            setBlockLocal(lavaX + right.getStepX(), y, lavaZ + right.getStepZ(), Blocks.BASALT.defaultBlockState());
            setBlockLocal(lavaX - right.getStepX(), y, lavaZ - right.getStepZ(), Blocks.BASALT.defaultBlockState());
            setBlockLocal(lavaX + facing.getStepX(), y, lavaZ + facing.getStepZ(), Blocks.BASALT.defaultBlockState());
            setLavaLocal(lavaX, y, lavaZ);
        }
    }

    private BlockPos buildWorldCoreInfrastructure(BlockPos preferredCorePos) {
        BlockPos corePos = resolveWorldCoreAnchor(preferredCorePos);
        if (!hasCoreClearance(corePos, 3, 28)) {
            carveCorePocket(corePos, 4);
        }

        carveSightlineChimney(corePos.getY());
        if (!hasCoreClearance(corePos, 2, 8)) {
            carveCorePocket(corePos, 4);
        }

        buildCoreCage(corePos);
        buildCoreSetpieceV3(corePos);
        ensureShaftSightline(corePos);
        return corePos;
    }

    private BlockPos resolveWorldCoreAnchor(BlockPos preferredCorePos) {
        int hubFloorY = hubCenterY - 1;
        int rimY = originY + settings.height() - 6;
        int shaftBottomY = hubFloorY + 2;
        int shaftTopY = rimY - 6;
        int shaftHeight = Math.max(12, shaftTopY - shaftBottomY);

        int computedY = shaftBottomY + (int) Math.round(shaftHeight * 0.62D);
        int minY = hubFloorY + 8;
        int maxY = rimY - 10;
        computedY = Mth.clamp(computedY, minY, maxY);

        int preferredY = computedY;
        if (preferredCorePos != null && !preferredCorePos.equals(BlockPos.ZERO)) {
            preferredY = Mth.clamp(preferredCorePos.getY(), minY, maxY);
        }

        BlockPos preferred = findNearestValidCorePos(preferredY, minY, maxY);
        if (preferred != null) {
            return preferred;
        }
        if (preferredY != computedY) {
            BlockPos fallbackComputed = findNearestValidCorePos(computedY, minY, maxY);
            if (fallbackComputed != null) {
                return fallbackComputed;
            }
        }

        BlockPos fallback = new BlockPos(origin.getX(), computedY, origin.getZ());
        carveCorePocket(fallback, 4);
        return fallback;
    }

    private BlockPos findNearestValidCorePos(int baseY, int minY, int maxY) {
        for (int dy = 0; dy <= 10; dy++) {
            int upY = baseY + dy;
            if (upY >= minY && upY <= maxY) {
                BlockPos up = new BlockPos(origin.getX(), upY, origin.getZ());
                if (isCoreCenterAir(up) && hasCoreClearance(up, 3, 28)) {
                    return up;
                }
            }
            if (dy == 0) {
                continue;
            }
            int downY = baseY - dy;
            if (downY >= minY && downY <= maxY) {
                BlockPos down = new BlockPos(origin.getX(), downY, origin.getZ());
                if (isCoreCenterAir(down) && hasCoreClearance(down, 3, 28)) {
                    return down;
                }
            }
        }
        return null;
    }

    private boolean isCoreCenterAir(BlockPos center) {
        return level.getBlockState(center).isAir();
    }

    private boolean hasCoreClearance(BlockPos center, int radius, int maxSolidBlocks) {
        if (!isCoreCenterAir(center)) {
            return false;
        }
        int solids = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(p);
                    if (!state.isAir() && state.blocksMotion()) {
                        solids++;
                        if (solids > maxSolidBlocks) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private void carveCorePocket(BlockPos center, int radius) {
        int shellRadius = radius + 1;
        for (int dx = -shellRadius; dx <= shellRadius; dx++) {
            for (int dy = -shellRadius; dy <= shellRadius; dy++) {
                for (int dz = -shellRadius; dz <= shellRadius; dz++) {
                    int x = center.getX() + dx;
                    int y = center.getY() + dy;
                    int z = center.getZ() + dz;
                    double dist = Math.sqrt((double) (dx * dx) + (double) (dy * dy) + (double) (dz * dz));
                    if (dist <= radius + 0.25D) {
                        setAirWorld(x, y, z);
                    } else if (dist <= shellRadius + 0.15D) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || !state.getFluidState().isEmpty() || !state.blocksMotion()) {
                            setBlockWorld(x, y, z, pickInterior(x, y, z));
                        }
                    }
                }
            }
        }
    }

    private void carveSightlineChimney(int orbY) {
        int fromY = (hubCenterY - 1) + 6;
        int toY = orbY - 2;
        if (toY < fromY) {
            return;
        }

        int radius = 3;
        int radiusSq = radius * radius;
        for (int y = fromY; y <= toY; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int d2 = (dx * dx) + (dz * dz);
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    if (d2 <= radiusSq) {
                        setAirWorld(x, y, z);
                    } else if (d2 == radiusSq + 1) {
                        BlockPos edge = new BlockPos(x, y, z);
                        if (level.getBlockState(edge).isAir()) {
                            setBlockWorld(x, y, z, pickInterior(x, y, z));
                        }
                    }
                }
            }
        }
    }

    private void ensureShaftSightline(BlockPos corePos) {
        int cx = corePos.getX() - origin.getX();
        int cz = corePos.getZ() - origin.getZ();
        int hubFloorY = hubCenterY - 1;
        int orbY = corePos.getY();
        int protectedYMin = orbY - 3;
        int protectedYMax = orbY + 5;

        removeMidShaftPlatformVolume(cx, cz, hubFloorY + 6, orbY - 4, protectedYMin, protectedYMax, 6);
        carveProtectedSightlineChimney(cx, cz, hubFloorY + 6, orbY - 2, protectedYMin, protectedYMax, 4);
        validateShaftSightlineRay(corePos, hubFloorY + 6, orbY - 2, protectedYMin, protectedYMax);
        reskinShaftWallsAfterSightline(cx, cz, hubFloorY + 6, orbY + 8, 5, Math.max(settings.calderaRadiusTop() + 2, 10));
        sealShaftFluids(cx, cz, hubFloorY + 4, orbY + 8, Math.max(settings.calderaRadiusTop() + 4, 12));
    }

    private void removeMidShaftPlatformVolume(
            int cx,
            int cz,
            int fromY,
            int toY,
            int protectedYMin,
            int protectedYMax,
            int radius) {
        if (toY < fromY) {
            return;
        }
        int radiusSq = radius * radius;
        for (int y = fromY; y <= toY; y++) {
            if (y >= protectedYMin && y <= protectedYMax) {
                continue;
            }
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if ((dx * dx) + (dz * dz) > radiusSq) {
                        continue;
                    }
                    setAirLocal(cx + dx, y, cz + dz);
                }
            }
        }
    }

    private void carveProtectedSightlineChimney(
            int cx,
            int cz,
            int fromY,
            int toY,
            int protectedYMin,
            int protectedYMax,
            int radius) {
        if (toY < fromY) {
            return;
        }
        int radiusSq = radius * radius;
        for (int y = fromY; y <= toY; y++) {
            if (y >= protectedYMin && y <= protectedYMax) {
                continue;
            }
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if ((dx * dx) + (dz * dz) > radiusSq) {
                        continue;
                    }
                    setAirLocal(cx + dx, y, cz + dz);
                }
            }
        }
    }

    private void validateShaftSightlineRay(
            BlockPos corePos,
            int minY,
            int maxY,
            int protectedYMin,
            int protectedYMax) {
        if (maxY < minY) {
            return;
        }

        double fromX = origin.getX() + 0.5D;
        double fromY = (hubCenterY - 1) + 1.7D;
        double fromZ = origin.getZ() + 0.5D;
        double toX = corePos.getX() + 0.5D;
        double toY = corePos.getY();
        double toZ = corePos.getZ() + 0.5D;

        int samples = 12;
        for (int i = 0; i < samples; i++) {
            double t = i / (double) (samples - 1);
            double sx = Mth.lerp(t, fromX, toX);
            double sy = Mth.lerp(t, fromY, toY);
            double sz = Mth.lerp(t, fromZ, toZ);
            int blockY = Mth.floor(sy + 0.5D);
            if (blockY < minY || blockY > maxY || (blockY >= protectedYMin && blockY <= protectedYMax)) {
                continue;
            }
            int baseX = Mth.floor(sx);
            int baseZ = Mth.floor(sz);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = baseX + dx;
                    int z = baseZ + dz;
                    BlockPos pos = new BlockPos(x, blockY, z);
                    BlockState state = level.getBlockState(pos);
                    if (isSightlinePassable(state)) {
                        continue;
                    }
                    setAirWorld(x, blockY, z);
                }
            }
        }
    }

    private boolean isSightlinePassable(BlockState state) {
        return state.isAir()
                || !state.canOcclude()
                || state.is(Blocks.CHAIN)
                || state.is(Blocks.IRON_BARS)
                || state.is(Blocks.GLASS_PANE)
                || state.is(Blocks.LANTERN)
                || state.is(Blocks.CANDLE);
    }

    private void reskinShaftWallsAfterSightline(
            int cx,
            int cz,
            int minY,
            int maxY,
            int innerRadius,
            int outerRadius) {
        if (maxY < minY) {
            return;
        }
        int innerSq = innerRadius * innerRadius;
        int outerSq = outerRadius * outerRadius;
        for (int lx = cx - outerRadius; lx <= cx + outerRadius; lx++) {
            for (int lz = cz - outerRadius; lz <= cz + outerRadius; lz++) {
                int dx = lx - cx;
                int dz = lz - cz;
                int d2 = (dx * dx) + (dz * dz);
                if (d2 <= innerSq || d2 > outerSq) {
                    continue;
                }
                int x = wx(lx);
                int z = wz(lz);
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.blocksMotion()) {
                        continue;
                    }
                    if (exposedToAir(x, y, z)) {
                        setBlockWorld(x, y, z, pickInterior(x, y, z));
                    }
                }
            }
        }
    }

    private void sealShaftFluids(int cx, int cz, int minY, int maxY, int radius) {
        int radiusSq = radius * radius;
        for (int lx = cx - radius; lx <= cx + radius; lx++) {
            for (int lz = cz - radius; lz <= cz + radius; lz++) {
                int dx = lx - cx;
                int dz = lz - cz;
                if ((dx * dx) + (dz * dz) > radiusSq) {
                    continue;
                }
                int x = wx(lx);
                int z = wz(lz);
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getFluidState().is(FluidTags.WATER)) {
                        setBlockWorld(x, y, z, Blocks.BASALT.defaultBlockState());
                    } else if (state.getFluidState().is(FluidTags.LAVA) && !intentionalLava.contains(BlockPos.asLong(x, y, z))) {
                        setBlockWorld(x, y, z, Blocks.BASALT.defaultBlockState());
                    }
                }
            }
        }
    }

    private void buildCoreCage(BlockPos corePos) {
        int cx = corePos.getX() - origin.getX();
        int cz = corePos.getZ() - origin.getZ();
        int ringY = corePos.getY();
        int rimY = originY + settings.height() - 6;

        buildCoreRing(cx, ringY, cz);
        buildCoreChainBraces(cx, corePos.getY(), cz, ringY, rimY);
        buildCoreRibs(cx, ringY, cz);
        buildCoreHalo(cx, corePos.getY(), cz, ringY);
        addCoreRingAccentLights(cx, ringY, cz);
    }

    private void buildCoreSetpieceV3(BlockPos corePos) {
        int cx = corePos.getX() - origin.getX();
        int cz = corePos.getZ() - origin.getZ();
        int orbY = corePos.getY();
        int rimY = originY + settings.height() - 6;
        int hubFloorY = hubCenterY - 1;

        applyCoreShaftContrastBanding(cx, cz, hubFloorY, rimY);
        buildCoreRuneCompassFloor(cx, hubFloorY, cz);
        buildCoreStabilizerPylons(cx, cz, orbY, rimY, hubFloorY);
        buildCoreUpperHalo(cx, cz, orbY, rimY);
        buildCoreGlowPockets(cx, cz, hubFloorY);
        buildCorePerchPlatformsAndSpiral(cx, cz, orbY, hubFloorY);
        buildShaftLightTeeth(cx, cz, rimY);
    }

    private void buildCoreRuneCompassFloor(int cx, int floorY, int cz) {
        int outerRingMin = 6;
        int outerRingMax = 7;
        int sigilRingMin = 4;
        int sigilRingMax = 5;
        int coreDiscRadius = 3;
        int flattenRadius = 8;
        int flattenSq = flattenRadius * flattenRadius;

        for (int dx = -flattenRadius; dx <= flattenRadius; dx++) {
            for (int dz = -flattenRadius; dz <= flattenRadius; dz++) {
                int d2 = (dx * dx) + (dz * dz);
                if (d2 > flattenSq) {
                    continue;
                }

                int lx = cx + dx;
                int lz = cz + dz;
                double r = Math.sqrt(d2);
                BlockState state = pickRitualFoundationBlock(lx, floorY, lz);

                if (r >= outerRingMin - 0.05D && r <= outerRingMax + 0.15D) {
                    state = pickRitualOuterRingBlock(lx, floorY, lz);
                } else if (r >= sigilRingMin - 0.05D && r <= sigilRingMax + 0.15D) {
                    state = pickRitualMiddleRingBlock(lx, floorY, lz);
                } else if (r <= coreDiscRadius + 0.15D) {
                    state = pickInnerDiscRune(cx, floorY, cz, lx, lz, coreDiscRadius);
                }

                setBlockLocal(lx, floorY, lz, state);
                setAirLocal(lx, floorY + 1, lz);
                setAirLocal(lx, floorY + 2, lz);
                setAirLocal(lx, floorY + 3, lz);
            }
        }

        addRuneGlyphNotches(cx, floorY, cz, 6);
        addRitualOuterNotches(cx, floorY, cz, 6);
        buildRuneEnergyVents(cx, floorY, cz, sigilRingMax);
        addRitualCrossPattern(cx, floorY, cz);
        buildRitualPedestalAndCandles(cx, floorY, cz);
        placeRuneDirectionalMarkers(cx, floorY, cz, outerRingMax + 2);
    }

    private BlockState pickInnerDiscRune(int cx, int floorY, int cz, int lx, int lz, int discRadius) {
        int dx = lx - cx;
        int dz = lz - cz;
        int d2 = (dx * dx) + (dz * dz);
        if (d2 == 0) {
            return Blocks.DEEPSLATE_TILES.defaultBlockState();
        }
        if (d2 > discRadius * discRadius) {
            return pickRitualFoundationBlock(lx, floorY, lz);
        }
        long roll = mix(worldSeed ^ 4511L, wx(lx), floorY, wz(lz));
        if (Math.floorMod(roll, 10L) < 7L) {
            return Blocks.DEEPSLATE_TILES.defaultBlockState();
        }
        if (Math.floorMod(roll, 13L) == 0L) {
            return Blocks.GILDED_BLACKSTONE.defaultBlockState();
        }
        return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    }

    private BlockState pickRitualFoundationBlock(int lx, int floorY, int lz) {
        long roll = mix(worldSeed ^ 4471L, wx(lx), floorY, wz(lz));
        return Math.floorMod(roll, 9L) == 0L
                ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                : Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    private BlockState pickRitualOuterRingBlock(int lx, int floorY, int lz) {
        long roll = mix(worldSeed ^ 4481L, wx(lx), floorY, wz(lz));
        return Math.floorMod(roll, 10L) < 8L
                ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                : Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    private BlockState pickRitualMiddleRingBlock(int lx, int floorY, int lz) {
        long roll = mix(worldSeed ^ 4487L, wx(lx), floorY, wz(lz));
        return Math.floorMod(roll, 9L) == 0L
                ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                : Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    private void addRuneGlyphNotches(int cx, int floorY, int cz, int glyphRadius) {
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2.0D) * i / 8.0D;
            int gx = cx + (int) Math.round(Math.cos(angle) * glyphRadius);
            int gz = cz + (int) Math.round(Math.sin(angle) * glyphRadius);
            BlockState accent = Math.floorMod(mix(worldSeed ^ 4537L, i, floorY, glyphRadius), 5L) == 0L
                    ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
                    : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            setBlockLocal(gx, floorY, gz, accent);
            setAirLocal(gx, floorY + 1, gz);

            int stepX = Integer.signum(gx - cx);
            int stepZ = Integer.signum(gz - cz);
            int tx = gx + stepX;
            int tz = gz + stepZ;
            Block tooth = Math.floorMod(mix(worldSeed ^ 4549L, i, floorY, 0), 2L) == 0L
                    ? Blocks.DEEPSLATE_BRICK_SLAB
                    : Blocks.POLISHED_BLACKSTONE_SLAB;
            setBlockLocal(tx, floorY, tz, bottomSlab(tooth));
            setAirLocal(tx, floorY + 1, tz);
        }
    }

    private void addRitualOuterNotches(int cx, int floorY, int cz, int notchRadius) {
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int nx = cx + dir.getStepX() * notchRadius;
            int nz = cz + dir.getStepZ() * notchRadius;
            BlockState inset = Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState();
            if (inset.hasProperty(StairBlock.FACING)) {
                inset = inset.setValue(StairBlock.FACING, dir.getOpposite());
            }
            if (inset.hasProperty(StairBlock.HALF)) {
                inset = inset.setValue(StairBlock.HALF, Half.TOP);
            }
            setBlockLocal(nx, floorY, nz, inset);
            setAirLocal(nx, floorY + 1, nz);
        }
    }

    private void addRitualCrossPattern(int cx, int floorY, int cz) {
        for (int step = -3; step <= 3; step++) {
            setBlockLocal(cx + step, floorY, cz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            setBlockLocal(cx, floorY, cz + step, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }

        for (int sx : List.of(-2, 2)) {
            for (int sz : List.of(-2, 2)) {
                BlockState accent = Math.floorMod(mix(worldSeed ^ 4559L, sx, floorY, sz), 2L) == 0L
                        ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
                        : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                setBlockLocal(cx + sx, floorY, cz + sz, accent);
            }
        }
    }

    private void buildRitualPedestalAndCandles(int cx, int floorY, int cz) {
        BlockState pedestal = Math.floorMod(mix(worldSeed ^ 4567L, cx, floorY, cz), 2L) == 0L
                ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        setBlockLocal(cx, floorY + 1, cz, pedestal);
        setAirLocal(cx, floorY + 2, cz);

        for (int dx : List.of(-1, 1)) {
            for (int dz : List.of(-1, 1)) {
                int lx = cx + dx;
                int lz = cz + dz;
                setBlockLocal(lx, floorY, lz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                setBlockLocal(lx, floorY + 1, lz, Blocks.CANDLE.defaultBlockState());
                setAirLocal(lx, floorY + 2, lz);
            }
        }
    }

    private void buildRuneEnergyVents(int cx, int floorY, int cz, int ventRadius) {
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            Direction right = dir.getClockWise();
            int centerX = cx + dir.getStepX() * ventRadius;
            int centerZ = cz + dir.getStepZ() * ventRadius;

            for (int side = -1; side <= 0; side++) {
                int vx = centerX + right.getStepX() * side;
                int vz = centerZ + right.getStepZ() * side;
                setBlockLocal(vx, floorY, vz, Blocks.IRON_BARS.defaultBlockState());
                setAirLocal(vx, floorY + 1, vz);
                placeSealedRitualPocket(vx, floorY, vz);
            }
        }
    }

    private void placeSealedRitualPocket(int lx, int floorY, int lz) {
        setBlockLocal(lx, floorY - 1, lz, Blocks.MAGMA_BLOCK.defaultBlockState());
        setBlockLocal(lx, floorY - 2, lz, Blocks.BASALT.defaultBlockState());

        for (Direction seal : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int sx = lx + seal.getStepX();
            int sz = lz + seal.getStepZ();
            BlockPos sidePos = new BlockPos(wx(sx), floorY - 1, wz(sz));
            if (!level.getBlockState(sidePos).blocksMotion()) {
                setBlockLocal(sx, floorY - 1, sz, Blocks.BASALT.defaultBlockState());
            }
            BlockPos floorPos = new BlockPos(wx(sx), floorY - 2, wz(sz));
            if (!level.getBlockState(floorPos).blocksMotion()) {
                setBlockLocal(sx, floorY - 2, sz, Blocks.BASALT.defaultBlockState());
            }
        }
    }

    private void placeSealedWallVent(int lx, int y, int lz, Direction outward) {
        int magmaX = lx + outward.getStepX();
        int magmaZ = lz + outward.getStepZ();

        setBlockLocal(lx, y, lz, Blocks.IRON_BARS.defaultBlockState());
        setBlockLocal(magmaX, y, magmaZ, Blocks.MAGMA_BLOCK.defaultBlockState());

        for (Direction seal : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            if (seal == outward.getOpposite()) {
                continue;
            }
            int sx = magmaX + seal.getStepX();
            int sz = magmaZ + seal.getStepZ();
            BlockPos sidePos = new BlockPos(wx(sx), y, wz(sz));
            if (!level.getBlockState(sidePos).blocksMotion()) {
                setBlockLocal(sx, y, sz, Blocks.BASALT.defaultBlockState());
            }
        }

        BlockPos below = new BlockPos(wx(magmaX), y - 1, wz(magmaZ));
        if (!level.getBlockState(below).blocksMotion()) {
            setBlockLocal(magmaX, y - 1, magmaZ, Blocks.BASALT.defaultBlockState());
        }
        BlockPos above = new BlockPos(wx(magmaX), y + 1, wz(magmaZ));
        if (!level.getBlockState(above).blocksMotion()) {
            setBlockLocal(magmaX, y + 1, magmaZ, Blocks.BASALT.defaultBlockState());
        }
    }

    private void placeRuneDirectionalMarkers(int cx, int floorY, int cz, int markerRadius) {
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int mx = cx + dir.getStepX() * markerRadius;
            int mz = cz + dir.getStepZ() * markerRadius;

            BlockPos below = new BlockPos(wx(mx), floorY - 1, wz(mz));
            if (!level.getBlockState(below).blocksMotion()) {
                setBlockLocal(mx, floorY - 1, mz, Blocks.DEEPSLATE_TILES.defaultBlockState());
            }

            BlockState arrow = Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState();
            if (arrow.hasProperty(StairBlock.FACING)) {
                arrow = arrow.setValue(StairBlock.FACING, dir);
            }
            setBlockLocal(mx, floorY, mz, arrow);
            setAirLocal(mx, floorY + 1, mz);
        }
    }

    private int findRuneChainAnchorY(int lx, int lz, int startY, int maxY) {
        int minWorldY = level.getMinBuildHeight() + 2;
        int topWorldY = level.getMaxBuildHeight() - 2;
        int clampedStart = Mth.clamp(startY, minWorldY, topWorldY);
        int clampedMax = Mth.clamp(Math.max(startY, maxY), clampedStart, topWorldY);

        for (int y = clampedStart; y <= clampedMax; y++) {
            if (level.getBlockState(new BlockPos(wx(lx), y, wz(lz))).blocksMotion()) {
                return y;
            }
        }

        int fallbackAnchor = Math.min(clampedStart + 1, topWorldY);
        if (level.getBlockState(new BlockPos(wx(lx), fallbackAnchor, wz(lz))).isAir()) {
            setBlockLocal(lx, fallbackAnchor, lz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        }
        return fallbackAnchor;
    }

    private void applyCoreShaftContrastBanding(int cx, int cz, int hubFloorY, int rimY) {
        int minY = hubFloorY + 4;
        int maxY = rimY - 5;
        if (maxY <= minY) {
            return;
        }
        int transitionY = minY + (int) Math.round((maxY - minY) * 0.55D);
        int innerR = Math.max(settings.calderaRadiusTop() - 2, 8);
        int outerR = Math.max(settings.calderaRadiusTop() + 5, 14);
        int outerSq = outerR * outerR;
        int innerSq = innerR * innerR;

        for (int lx = cx - outerR; lx <= cx + outerR; lx++) {
            for (int lz = cz - outerR; lz <= cz + outerR; lz++) {
                int dx = lx - cx;
                int dz = lz - cz;
                int d2 = (dx * dx) + (dz * dz);
                if (d2 < innerSq || d2 > outerSq) {
                    continue;
                }
                int x = wx(lx);
                int z = wz(lz);
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.blocksMotion() || !exposedToAir(x, y, z)) {
                        continue;
                    }
                    BlockState band = y <= transitionY
                            ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                            : Blocks.SMOOTH_BASALT.defaultBlockState();
                    setBlockWorld(x, y, z, band);
                }
            }
        }
    }

    private void buildCoreStabilizerPylons(int cx, int cz, int orbY, int rimY, int hubFloorY) {
        int bottomY = hubFloorY + 4;
        int topY = Math.min(orbY + 10, rimY - 8);
        if (topY - bottomY < 8) {
            return;
        }

        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int pylonR = detectCoreWallRadius(cx, cz, dir, bottomY, topY);
            buildSingleCorePylon(cx, cz, dir, pylonR, bottomY, topY, orbY);
        }
    }

    private int detectCoreWallRadius(int cx, int cz, Direction dir, int bottomY, int topY) {
        int[] sampleYs = new int[] {bottomY + 2, bottomY + ((topY - bottomY) / 2), topY - 1};
        int sum = 0;
        int count = 0;
        for (int sampleY : sampleYs) {
            int clampedY = Mth.clamp(sampleY, level.getMinBuildHeight() + 2, level.getMaxBuildHeight() - 3);
            int hit = -1;
            for (int r = 9; r <= 22; r++) {
                int lx = cx + dir.getStepX() * r;
                int lz = cz + dir.getStepZ() * r;
                BlockPos pos = new BlockPos(wx(lx), clampedY, wz(lz));
                if (level.getBlockState(pos).blocksMotion()) {
                    hit = r;
                    break;
                }
            }
            if (hit > 0) {
                sum += hit;
                count++;
            }
        }
        int wall = count == 0 ? 17 : Math.round(sum / (float) count);
        return Mth.clamp(wall - 1, 13, 16);
    }

    private void buildSingleCorePylon(int cx, int cz, Direction dir, int radius, int bottomY, int topY, int orbY) {
        int centerX = cx + dir.getStepX() * radius;
        int centerZ = cz + dir.getStepZ() * radius;
        Direction right = dir.getClockWise();

        for (int y = bottomY; y <= topY; y++) {
            for (int w = -1; w <= 1; w++) {
                for (int depth = 0; depth <= 1; depth++) {
                    int lx = centerX + right.getStepX() * w + dir.getStepX() * depth;
                    int lz = centerZ + right.getStepZ() * w + dir.getStepZ() * depth;
                    setBlockLocal(lx, y, lz, pickCorePylonBlock(lx, y, lz));
                }
            }
            setBlockLocal(centerX + dir.getStepX() * 2, y, centerZ + dir.getStepZ() * 2, Blocks.BASALT.defaultBlockState());
        }

        int midY = bottomY + ((topY - bottomY) / 2);
        setAirLocal(centerX, midY, centerZ);
        setAirLocal(centerX, midY + 1, centerZ);
        setBlockLocal(centerX + dir.getStepX(), midY, centerZ + dir.getStepZ(), Blocks.LANTERN.defaultBlockState());

        int targetX = cx + dir.getStepX() * 6;
        int targetZ = cz + dir.getStepZ() * 6;
        drawChainLine(centerX, topY, centerZ, targetX, orbY + 1, targetZ);
    }

    private BlockState pickCorePylonBlock(int lx, int y, int lz) {
        long roll = mix(worldSeed ^ 4337L, wx(lx), y, wz(lz));
        if (Math.floorMod(roll, 13L) == 0L) {
            return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        }
        return (roll & 1L) == 0L
                ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                : Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    private void buildCoreUpperHalo(int cx, int cz, int orbY, int rimY) {
        int haloY = Math.min(orbY + 14, rimY - 8);
        if (haloY <= orbY + 5) {
            return;
        }

        int outer = 11;
        int inner = 9;
        int limit = outer + 1;
        for (int lx = cx - limit; lx <= cx + limit; lx++) {
            for (int lz = cz - limit; lz <= cz + limit; lz++) {
                double r = Math.sqrt((double) (lx - cx) * (lx - cx) + (double) (lz - cz) * (lz - cz));
                if (r < inner - 0.25D || r > outer + 0.25D) {
                    continue;
                }

                double angle = Math.toDegrees(Math.atan2(lz - cz, lx - cx));
                int segment = (int) Math.floor(Math.floorMod((long) Math.floor(angle + 360.0D), 360L) / 10.0D);
                int keepRoll = (int) Math.floorMod(mix(worldSeed ^ 4373L, segment, haloY, 0), 100L);
                if (keepRoll < 35) {
                    continue;
                }

                for (int y = haloY; y <= haloY + 1; y++) {
                    setBlockLocal(lx, y, lz, pickCoreUpperHaloBlock(lx, y, lz));
                }
            }
        }

        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2.0D) * i / 8.0D;
            int lx = cx + (int) Math.round(Math.cos(angle) * outer);
            int lz = cz + (int) Math.round(Math.sin(angle) * outer);
            int len = 4 + (int) Math.floorMod(mix(worldSeed ^ 4399L, i, haloY, 1), 4);
            for (int d = 1; d <= len; d++) {
                int y = haloY - d;
                setBlockLocal(lx, y, lz, (d % 3 == 0) ? Blocks.BLACKSTONE_WALL.defaultBlockState() : Blocks.BASALT.defaultBlockState());
            }
        }

        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int lx = cx + dir.getStepX() * 10;
            int lz = cz + dir.getStepZ() * 10;
            if (level.getBlockState(new BlockPos(wx(lx), haloY, wz(lz))).isAir()) {
                setBlockLocal(lx, haloY, lz, pickCoreUpperHaloBlock(lx, haloY, lz));
            }
            placeHangingLanternLocal(lx, haloY - 1, lz);
        }
    }

    private BlockState pickCoreUpperHaloBlock(int lx, int y, int lz) {
        return ((mix(worldSeed ^ 4421L, wx(lx), y, wz(lz)) & 1L) == 0L)
                ? Blocks.SMOOTH_BASALT.defaultBlockState()
                : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    }

    private void buildCorePerchPlatformsAndSpiral(int cx, int cz, int orbY, int hubFloorY) {
        shaftModuleBlocks.clear();
        shaftWalkableBlocks.clear();

        int upperY = resolveUpperDistrictY(hubFloorY, orbY);
        if (upperY <= hubFloorY + 6) {
            return;
        }

        int sampleBottom = hubFloorY + 4;
        int sampleTop = Math.min(level.getMaxBuildHeight() - 4, orbY + 8);
        int minWallRadius = Integer.MAX_VALUE;
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            minWallRadius = Math.min(minWallRadius, detectCoreWallRadius(cx, cz, dir, sampleBottom, sampleTop));
        }
        if (minWallRadius == Integer.MAX_VALUE) {
            minWallRadius = 13;
        }

        int ringOuterRadius = Mth.clamp(minWallRadius - 1, 10, 14);
        int perchRadius = Math.max(8, ringOuterRadius - 2);

        carveUpperRingBand(cx, cz, upperY, ringOuterRadius);
        buildUpperRingBalcony(cx, cz, upperY, ringOuterRadius);
        buildUpperRingShortBridges(cx, cz, upperY, ringOuterRadius);

        List<PerchAnchor> anchors = new ArrayList<>();
        for (Direction wallDir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            anchors.add(buildSingleCorePerchPlatform(cx, cz, wallDir, upperY, perchRadius));
        }
        connectPerchesToUpperRing(cx, cz, upperY, ringOuterRadius, anchors);

        buildUpperUtilityPockets(cx, cz, upperY, ringOuterRadius);
        buildUpperShaftViewpoint(cx, cz, upperY, ringOuterRadius);
        placeUpperRingRecessedLights(cx, cz, upperY, ringOuterRadius);

        List<ServiceStairNode> stairNodes = buildCoreServiceStair(cx, cz, hubFloorY, upperY, ringOuterRadius);
        CoreLadderAccess ladderAccess = buildCoreLadderAccess(cx, cz, hubFloorY, upperY, ringOuterRadius);
        enforcePrimaryUpperAccessRoute(cx, cz, upperY, ringOuterRadius);
        restoreUpperUtilityPocketAccess(cx, cz, upperY, ringOuterRadius);
        cleanupShaftWalkwayBand(cx, cz, upperY, ringOuterRadius);
        validateCoreServiceStair(stairNodes);
        validateCoreLadderAccess(ladderAccess);
        validateShaftWalkabilityAndSafety();
        enforcePrimaryUpperAccessRoute(cx, cz, upperY, ringOuterRadius);
        protectCoreUpperDistrict(cx, cz, hubFloorY, upperY, ringOuterRadius, stairNodes, ladderAccess);
    }

    private int resolveUpperDistrictY(int hubFloorY, int orbY) {
        int maxAllowed = orbY - 8;
        int minTarget = hubFloorY + 8;
        if (maxAllowed < minTarget) {
            return maxAllowed;
        }
        return Mth.clamp(hubFloorY + 16, minTarget, maxAllowed);
    }

    private void carveUpperRingBand(int cx, int cz, int upperY, int ringOuterRadius) {
        int ringInnerRadius = ringOuterRadius - 2;
        int limit = ringOuterRadius + 3;

        for (int lx = cx - limit; lx <= cx + limit; lx++) {
            for (int lz = cz - limit; lz <= cz + limit; lz++) {
                double r = Math.sqrt((double) (lx - cx) * (lx - cx) + (double) (lz - cz) * (lz - cz));
                if (r < ringInnerRadius - 1.2D || r > ringOuterRadius + 1.2D) {
                    continue;
                }
                for (int y = upperY + 1; y <= upperY + 5; y++) {
                    setAirLocal(lx, y, lz);
                }
            }
        }
    }

    private void buildUpperRingBalcony(int cx, int cz, int upperY, int ringOuterRadius) {
        int ringInnerRadius = ringOuterRadius - 2;
        int limit = ringOuterRadius + 2;

        for (int lx = cx - limit; lx <= cx + limit; lx++) {
            for (int lz = cz - limit; lz <= cz + limit; lz++) {
                double r = Math.sqrt((double) (lx - cx) * (lx - cx) + (double) (lz - cz) * (lz - cz));
                if (r < ringInnerRadius - 0.35D || r > ringOuterRadius + 0.35D) {
                    continue;
                }

                setBlockLocal(lx, upperY, lz, pickServiceFloorBlock(lx, upperY, lz));
                markShaftWalkableLocal(lx, upperY, lz);
                clearThreeBlockHeadroom(lx, upperY, lz);
                reinforceFloorSupport(lx, upperY - 1, lz, 3);

                Direction toCenter = directionToCenter(lx - cx, lz - cz);
                if (r <= ringInnerRadius + 0.25D) {
                    setBlockLocal(
                            lx + toCenter.getStepX(),
                            upperY + 1,
                            lz + toCenter.getStepZ(),
                            Blocks.BLACKSTONE_WALL.defaultBlockState());
                    markShaftModuleLocal(lx + toCenter.getStepX(), upperY + 1, lz + toCenter.getStepZ());
                }

                if (r >= ringOuterRadius - 0.2D) {
                    Direction toWall = toCenter.getOpposite();
                    int wallX = lx + toWall.getStepX();
                    int wallZ = lz + toWall.getStepZ();
                    BlockPos wallPos = new BlockPos(wx(wallX), upperY, wz(wallZ));
                    if (!level.getBlockState(wallPos).blocksMotion()) {
                        setBlockLocal(wallX, upperY, wallZ, pickInterior(wx(wallX), upperY, wz(wallZ)));
                        markShaftModuleLocal(wallX, upperY, wallZ);
                    }
                }
            }
        }
    }

    private void buildUpperRingShortBridges(int cx, int cz, int upperY, int ringOuterRadius) {
        int bridgeX = ringOuterRadius - 2;
        int bridgeZ = ringOuterRadius - 4;
        buildSingleUpperRingBridge(cx + bridgeX, cz + bridgeZ, upperY, Direction.WEST);
        buildSingleUpperRingBridge(cx - bridgeX, cz - bridgeZ, upperY, Direction.EAST);
    }

    private void buildSingleUpperRingBridge(int startX, int startZ, int upperY, Direction towardCenter) {
        Direction right = towardCenter.getClockWise();
        for (int d = 0; d < 3; d++) {
            for (int w = -1; w <= 1; w++) {
                int lx = startX + towardCenter.getStepX() * d + right.getStepX() * w;
                int lz = startZ + towardCenter.getStepZ() * d + right.getStepZ() * w;
                setBlockLocal(lx, upperY, lz, pickServiceFloorBlock(lx, upperY, lz));
                markShaftWalkableLocal(lx, upperY, lz);
                clearThreeBlockHeadroom(lx, upperY, lz);
                reinforceFloorSupport(lx, upperY - 1, lz, 3);
            }
        }
    }

    private void connectPerchesToUpperRing(
            int cx,
            int cz,
            int upperY,
            int ringOuterRadius,
            List<PerchAnchor> anchors) {
        int ringInnerRadius = ringOuterRadius - 2;
        for (PerchAnchor anchor : anchors) {
            Direction toCenter = directionToCenter(anchor.x() - cx, anchor.z() - cz);
            for (int step = 0; step <= 8; step++) {
                int lx = anchor.x() + toCenter.getStepX() * step;
                int lz = anchor.z() + toCenter.getStepZ() * step;
                setBlockLocal(lx, upperY, lz, pickServiceFloorBlock(lx, upperY, lz));
                markShaftWalkableLocal(lx, upperY, lz);
                clearThreeBlockHeadroom(lx, upperY, lz);
                reinforceFloorSupport(lx, upperY - 1, lz, 3);

                Direction side = toCenter.getClockWise();
                setBlockLocal(lx + side.getStepX(), upperY + 1, lz + side.getStepZ(), Blocks.BLACKSTONE_WALL.defaultBlockState());
                setBlockLocal(lx - side.getStepX(), upperY + 1, lz - side.getStepZ(), Blocks.BLACKSTONE_WALL.defaultBlockState());
                markShaftModuleLocal(lx + side.getStepX(), upperY + 1, lz + side.getStepZ());
                markShaftModuleLocal(lx - side.getStepX(), upperY + 1, lz - side.getStepZ());

                double r = Math.sqrt((double) (lx - cx) * (lx - cx) + (double) (lz - cz) * (lz - cz));
                if (r >= ringInnerRadius - 0.45D && r <= ringOuterRadius + 0.45D) {
                    break;
                }
            }
        }
    }

    private List<Integer> resolveCorePerchLevels(int hubFloorY, int orbY) {
        int maxAllowedY = orbY - 8;
        List<Integer> levels = new ArrayList<>();
        for (int candidate : List.of(hubFloorY + 8, hubFloorY + 16, hubFloorY + 24)) {
            if (candidate <= maxAllowedY) {
                levels.add(candidate);
            }
        }
        return levels;
    }

    private PerchAnchor buildSingleCorePerchPlatform(int cx, int cz, Direction wallDir, int perchY, int perchRadius) {
        Direction right = wallDir.getClockWise();
        Direction inward = wallDir.getOpposite();

        int centerX = cx + wallDir.getStepX() * perchRadius;
        int centerZ = cz + wallDir.getStepZ() * perchRadius;

        carveCorePerchPocket(centerX, centerZ, wallDir, perchY);

        for (int w = -3; w <= 3; w++) {
            for (int d = -2; d <= 2; d++) {
                int lx = centerX + right.getStepX() * w + inward.getStepX() * d;
                int lz = centerZ + right.getStepZ() * w + inward.getStepZ() * d;
                boolean edge = Math.abs(w) == 3 || Math.abs(d) == 2;
                boolean entryGap = d == -2 && (w == -1 || w == 0);

                BlockState floor = edge
                        ? bottomSlab(Blocks.POLISHED_BLACKSTONE_SLAB)
                        : pickPerchFloorBlock(lx, perchY, lz, w, d);
                setBlockLocal(lx, perchY, lz, floor);
                markShaftWalkableLocal(lx, perchY, lz);
                clearThreeBlockHeadroom(lx, perchY, lz);
                reinforceFloorSupport(lx, perchY - 1, lz, 3);

                if (edge && !entryGap) {
                    setBlockLocal(lx, perchY + 1, lz, Blocks.BLACKSTONE_WALL.defaultBlockState());
                    markShaftModuleLocal(lx, perchY + 1, lz);
                }
            }
        }

        for (int cornerW : List.of(-3, 3)) {
            for (int cornerD : List.of(-2, 2)) {
                int lx = centerX + right.getStepX() * cornerW + inward.getStepX() * cornerD;
                int lz = centerZ + right.getStepZ() * cornerW + inward.getStepZ() * cornerD;
                setBlockLocal(lx, perchY + 2, lz, Blocks.BASALT.defaultBlockState());
                markShaftModuleLocal(lx, perchY + 2, lz);
            }
        }

        int throatDepth = buildPerchEntryThroat(centerX, centerZ, wallDir, perchY);
        placePerchPocketLantern(centerX, centerZ, wallDir, perchY);

        int anchorX = centerX + right.getStepX() * -1 + inward.getStepX() * throatDepth;
        int anchorZ = centerZ + right.getStepZ() * -1 + inward.getStepZ() * throatDepth;
        return new PerchAnchor(anchorX, perchY, anchorZ);
    }

    private void carveCorePerchPocket(int centerX, int centerZ, Direction wallDir, int perchY) {
        Direction right = wallDir.getClockWise();
        Direction inward = wallDir.getOpposite();

        for (int w = -4; w <= 4; w++) {
            for (int d = -5; d <= 3; d++) {
                int lx = centerX + right.getStepX() * w + inward.getStepX() * d;
                int lz = centerZ + right.getStepZ() * w + inward.getStepZ() * d;
                for (int y = perchY; y <= perchY + 5; y++) {
                    boolean shell = Math.abs(w) == 4 || d == -5 || d == 3 || y == perchY + 5;
                    if (shell) {
                        int worldX = wx(lx);
                        int worldZ = wz(lz);
                        BlockPos pos = new BlockPos(worldX, y, worldZ);
                        BlockState state = level.getBlockState(pos);
                        if (!state.blocksMotion() || exposedToAir(worldX, y, worldZ)) {
                            setBlockLocal(lx, y, lz, pickInterior(worldX, y, worldZ));
                        }
                    } else {
                        setAirLocal(lx, y, lz);
                    }
                }
            }
        }
    }

    private BlockState pickPerchFloorBlock(int lx, int y, int lz, int widthOffset, int depthOffset) {
        if (Math.abs(widthOffset) <= 1 && Math.abs(depthOffset) <= 1) {
            return Blocks.CHISELED_DEEPSLATE.defaultBlockState();
        }
        long roll = mix(worldSeed ^ 5881L, wx(lx), y, wz(lz));
        return Math.floorMod(roll, 5L) == 0L
                ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                : Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    private BlockState pickServiceFloorBlock(int lx, int y, int lz) {
        long roll = mix(worldSeed ^ 5897L, wx(lx), y, wz(lz));
        return Math.floorMod(roll, 11L) == 0L
                ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                : Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    private int buildPerchEntryThroat(int centerX, int centerZ, Direction wallDir, int perchY) {
        Direction right = wallDir.getClockWise();
        Direction inward = wallDir.getOpposite();
        int endDepth = -4;

        for (int d = -3; d >= endDepth; d--) {
            for (int w = -1; w <= 0; w++) {
                int lx = centerX + right.getStepX() * w + inward.getStepX() * d;
                int lz = centerZ + right.getStepZ() * w + inward.getStepZ() * d;
                setBlockLocal(lx, perchY, lz, pickServiceFloorBlock(lx, perchY, lz));
                markShaftWalkableLocal(lx, perchY, lz);
                clearThreeBlockHeadroom(lx, perchY, lz);
                reinforceFloorSupport(lx, perchY - 1, lz, 3);
            }
            for (int wallW : List.of(-2, 1)) {
                int wx = centerX + right.getStepX() * wallW + inward.getStepX() * d;
                int wz = centerZ + right.getStepZ() * wallW + inward.getStepZ() * d;
                setBlockLocal(wx, perchY + 1, wz, Blocks.BLACKSTONE_WALL.defaultBlockState());
                setBlockLocal(wx, perchY + 2, wz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                markShaftModuleLocal(wx, perchY + 1, wz);
                markShaftModuleLocal(wx, perchY + 2, wz);
            }
        }

        for (int w = -2; w <= 1; w++) {
            int lx = centerX + right.getStepX() * w + inward.getStepX() * endDepth;
            int lz = centerZ + right.getStepZ() * w + inward.getStepZ() * endDepth;
            setBlockLocal(lx, perchY + 3, lz, Blocks.BASALT.defaultBlockState());
            markShaftModuleLocal(lx, perchY + 3, lz);
        }

        return endDepth;
    }

    private void placePerchPocketLantern(int centerX, int centerZ, Direction wallDir, int perchY) {
        Direction right = wallDir.getClockWise();
        Direction inward = wallDir.getOpposite();
        int lx = centerX + right.getStepX() * 2 + inward.getStepX() * -1;
        int lz = centerZ + right.getStepZ() * 2 + inward.getStepZ() * -1;
        setBlockLocal(lx, perchY + 1, lz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        markShaftModuleLocal(lx, perchY + 1, lz);
        placeLantern(lx, perchY + 2, lz);
    }

    private void buildUpperUtilityPockets(int cx, int cz, int upperY, int ringOuterRadius) {
        buildUpperUtilityPocket(cx, cz, upperY, ringOuterRadius, Direction.EAST, false);
        buildUpperUtilityPocket(cx, cz, upperY, ringOuterRadius, Direction.WEST, true);
    }

    private void restoreUpperUtilityPocketAccess(int cx, int cz, int upperY, int ringOuterRadius) {
        restoreSingleUpperUtilityPocketAccess(cx, cz, upperY, ringOuterRadius, Direction.EAST);
        restoreSingleUpperUtilityPocketAccess(cx, cz, upperY, ringOuterRadius, Direction.WEST);
    }

    private void enforcePrimaryUpperAccessRoute(int cx, int cz, int upperY, int ringOuterRadius) {
        int laneA = cx - ringOuterRadius + 1;
        int laneB = laneA + 2;
        int stairXMax = laneB + 1;
        int stairXMin = laneA;
        int ringDoorX = cx - ringOuterRadius;
        int ringDoorZ = cz;

        // Dedicated 3-wide corridor: stair top connector -> ring door.
        enforceUpperAccessVolume(stairXMin, stairXMax, ringDoorZ - 1, ringDoorZ + 1, upperY);
        enforceUpperAccessVolume(Math.min(stairXMin, ringDoorX), Math.max(stairXMax, ringDoorX), ringDoorZ - 1, ringDoorZ + 1, upperY);

        // Door pocket (5 wide, 5 tall, 4 deep) so the entrance can never pinch.
        int pocketMinX = ringDoorX - 3;
        int pocketMaxX = ringDoorX + 1;
        int pocketMinZ = ringDoorZ - 2;
        int pocketMaxZ = ringDoorZ + 2;
        enforceUpperAccessVolume(pocketMinX, pocketMaxX, pocketMinZ, pocketMaxZ, upperY);

        markProtectedAabbLocal(Math.min(stairXMin, pocketMinX), upperY, pocketMinZ, Math.max(stairXMax, pocketMaxX), upperY + 5, pocketMaxZ);
    }

    private void enforceUpperAccessVolume(int minX, int maxX, int minZ, int maxZ, int floorY) {
        for (int lx = minX; lx <= maxX; lx++) {
            for (int lz = minZ; lz <= maxZ; lz++) {
                setBlockLocalForced(lx, floorY, lz, pickServiceFloorBlock(lx, floorY, lz));
                markShaftWalkableLocal(lx, floorY, lz);
                reinforceFloorSupport(lx, floorY - 1, lz, 3);
                for (int y = floorY + 1; y <= floorY + 5; y++) {
                    setAirLocalForced(lx, y, lz);
                }
            }
        }
    }

    private void restoreSingleUpperUtilityPocketAccess(
            int cx,
            int cz,
            int upperY,
            int ringOuterRadius,
            Direction dir) {
        Direction right = dir.getClockWise();
        int baseX = cx + dir.getStepX() * ringOuterRadius;
        int baseZ = cz + dir.getStepZ() * ringOuterRadius;

        for (int d = -2; d <= 1; d++) {
            for (int w = -1; w <= 0; w++) {
                int lx = baseX + dir.getStepX() * d + right.getStepX() * w;
                int lz = baseZ + dir.getStepZ() * d + right.getStepZ() * w;
                setBlockLocal(lx, upperY, lz, pickServiceFloorBlock(lx, upperY, lz));
                markShaftWalkableLocal(lx, upperY, lz);
                clearTwoBlockHeadroomLocalPreservingFixtures(lx, upperY, lz);
                reinforceFloorSupport(lx, upperY - 1, lz, 3);
            }
        }
    }

    private void buildUpperUtilityPocket(
            int cx,
            int cz,
            int upperY,
            int ringOuterRadius,
            Direction dir,
            boolean shrine) {
        Direction right = dir.getClockWise();
        int halfWidth = 4;
        int depth = 7;
        int roomHeight = 5;

        int baseX = cx + dir.getStepX() * ringOuterRadius;
        int baseZ = cz + dir.getStepZ() * ringOuterRadius;

        for (int d = 0; d <= depth; d++) {
            for (int w = -halfWidth; w <= halfWidth; w++) {
                int lx = baseX + dir.getStepX() * d + right.getStepX() * w;
                int lz = baseZ + dir.getStepZ() * d + right.getStepZ() * w;

                for (int y = upperY; y <= upperY + roomHeight; y++) {
                    boolean doorCut = d == 0 && (w == -1 || w == 0) && y <= upperY + 2;
                    boolean shell = Math.abs(w) == halfWidth || d == depth || y == upperY + roomHeight || (d == 0 && !doorCut);
                    if (y == upperY) {
                        setBlockLocal(lx, y, lz, pickServiceFloorBlock(lx, y, lz));
                        markShaftWalkableLocal(lx, y, lz);
                    } else if (shell) {
                        setBlockLocal(lx, y, lz, pickInterior(wx(lx), y, wz(lz)));
                        markShaftModuleLocal(lx, y, lz);
                    } else {
                        setAirLocal(lx, y, lz);
                    }
                }
            }
        }

        for (int c = 1; c <= 2; c++) {
            for (int w = -1; w <= 0; w++) {
                int lx = baseX - dir.getStepX() * c + right.getStepX() * w;
                int lz = baseZ - dir.getStepZ() * c + right.getStepZ() * w;
                setBlockLocal(lx, upperY, lz, pickServiceFloorBlock(lx, upperY, lz));
                markShaftWalkableLocal(lx, upperY, lz);
                clearThreeBlockHeadroom(lx, upperY, lz);
                reinforceFloorSupport(lx, upperY - 1, lz, 3);
            }
        }

        int lampX = baseX + dir.getStepX() * (depth - 1) + right.getStepX() * 3;
        int lampZ = baseZ + dir.getStepZ() * (depth - 1) + right.getStepZ() * 3;
        setBlockLocal(lampX, upperY + 3, lampZ, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        markShaftModuleLocal(lampX, upperY + 3, lampZ);
        placeLantern(lampX, upperY + 2, lampZ);

        if (shrine) {
            decorateShrinePocket(baseX, baseZ, upperY, dir, right, depth);
        } else {
            decorateStoragePocket(baseX, baseZ, upperY, dir, right, depth);
        }
    }

    private void decorateStoragePocket(int baseX, int baseZ, int upperY, Direction dir, Direction right, int depth) {
        for (int w : List.of(-3, 3)) {
            int bx = baseX + dir.getStepX() * (depth - 1) + right.getStepX() * w;
            int bz = baseZ + dir.getStepZ() * (depth - 1) + right.getStepZ() * w;
            setBlockLocal(bx, upperY + 1, bz, Blocks.BARREL.defaultBlockState());
            markShaftModuleLocal(bx, upperY + 1, bz);
        }

        int anvilX = baseX + dir.getStepX() * (depth - 2);
        int anvilZ = baseZ + dir.getStepZ() * (depth - 2);
        setBlockLocal(anvilX, upperY + 1, anvilZ, Blocks.ANVIL.defaultBlockState());
        markShaftModuleLocal(anvilX, upperY + 1, anvilZ);
    }

    private void decorateShrinePocket(int baseX, int baseZ, int upperY, Direction dir, Direction right, int depth) {
        int altarDepth = depth - 2;
        for (int w = -1; w <= 1; w++) {
            int lx = baseX + dir.getStepX() * altarDepth + right.getStepX() * w;
            int lz = baseZ + dir.getStepZ() * altarDepth + right.getStepZ() * w;
            setBlockLocal(lx, upperY + 1, lz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            markShaftModuleLocal(lx, upperY + 1, lz);
        }

        int centerX = baseX + dir.getStepX() * altarDepth;
        int centerZ = baseZ + dir.getStepZ() * altarDepth;
        setBlockLocal(centerX, upperY + 2, centerZ, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
        markShaftModuleLocal(centerX, upperY + 2, centerZ);

        for (int w : List.of(-1, 1)) {
            int cx = baseX + dir.getStepX() * (altarDepth - 1) + right.getStepX() * w;
            int cz = baseZ + dir.getStepZ() * (altarDepth - 1) + right.getStepZ() * w;
            setBlockLocal(cx, upperY + 1, cz, Blocks.CANDLE.defaultBlockState());
            markShaftModuleLocal(cx, upperY + 1, cz);
        }

        int runeX = baseX + dir.getStepX() * depth;
        int runeZ = baseZ + dir.getStepZ() * depth;
        setBlockLocal(runeX, upperY + 2, runeZ, Blocks.GILDED_BLACKSTONE.defaultBlockState());
        markShaftModuleLocal(runeX, upperY + 2, runeZ);

        int ventX = baseX + dir.getStepX() * (depth - 1) + right.getStepX() * -3;
        int ventZ = baseZ + dir.getStepZ() * (depth - 1) + right.getStepZ() * -3;
        setBlockLocal(ventX, upperY, ventZ, Blocks.IRON_BARS.defaultBlockState());
        setBlockLocal(ventX, upperY - 1, ventZ, Blocks.MAGMA_BLOCK.defaultBlockState());
        markShaftModuleLocal(ventX, upperY, ventZ);
        markShaftModuleLocal(ventX, upperY - 1, ventZ);
        for (Direction seal : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int sx = ventX + seal.getStepX();
            int sz = ventZ + seal.getStepZ();
            if (!level.getBlockState(new BlockPos(wx(sx), upperY - 1, wz(sz))).blocksMotion()) {
                setBlockLocal(sx, upperY - 1, sz, Blocks.BASALT.defaultBlockState());
                markShaftModuleLocal(sx, upperY - 1, sz);
            }
        }
    }

    private void buildUpperShaftViewpoint(int cx, int cz, int upperY, int ringOuterRadius) {
        Direction wallDir = Direction.SOUTH;
        Direction right = wallDir.getClockWise();
        Direction inward = wallDir.getOpposite();

        int centerX = cx + wallDir.getStepX() * ringOuterRadius;
        int centerZ = cz + wallDir.getStepZ() * ringOuterRadius;

        for (int w = -2; w <= 2; w++) {
            for (int d = 0; d <= 2; d++) {
                int lx = centerX + right.getStepX() * w + inward.getStepX() * d;
                int lz = centerZ + right.getStepZ() * w + inward.getStepZ() * d;
                setBlockLocal(lx, upperY, lz, pickServiceFloorBlock(lx, upperY, lz));
                markShaftWalkableLocal(lx, upperY, lz);
                clearThreeBlockHeadroom(lx, upperY, lz);
                reinforceFloorSupport(lx, upperY - 1, lz, 3);

                boolean sideRail = Math.abs(w) == 2;
                boolean frontRail = d == 2;
                if (sideRail || frontRail) {
                    setBlockLocal(lx, upperY + 1, lz, Blocks.BLACKSTONE_WALL.defaultBlockState());
                    markShaftModuleLocal(lx, upperY + 1, lz);
                }
            }
        }

        for (int w : List.of(-2, 2)) {
            int lx = centerX + right.getStepX() * w + inward.getStepX() * 2;
            int lz = centerZ + right.getStepZ() * w + inward.getStepZ() * 2;
            for (int y = upperY + 3; y <= upperY + 5; y++) {
                setBlockLocal(lx, y, lz, Blocks.CHAIN.defaultBlockState());
                markShaftModuleLocal(lx, y, lz);
            }
        }

        int lanternX = centerX + inward.getStepX() * 2;
        int lanternZ = centerZ + inward.getStepZ() * 2;
        setBlockLocal(lanternX, upperY + 3, lanternZ, Blocks.CHAIN.defaultBlockState());
        markShaftModuleLocal(lanternX, upperY + 3, lanternZ);
        placeLantern(lanternX, upperY + 2, lanternZ);
    }

    private void placeUpperRingRecessedLights(int cx, int cz, int upperY, int ringOuterRadius) {
        int count = Math.max(8, (int) Math.round((Math.PI * 2.0D * ringOuterRadius) / 12.0D));
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D) * i / count;
            int lightX = cx + (int) Math.round(Math.cos(angle) * (ringOuterRadius + 1));
            int lightZ = cz + (int) Math.round(Math.sin(angle) * (ringOuterRadius + 1));
            int floorX = cx + (int) Math.round(Math.cos(angle) * (ringOuterRadius - 1));
            int floorZ = cz + (int) Math.round(Math.sin(angle) * (ringOuterRadius - 1));

            setBlockLocal(lightX, upperY + 3, lightZ, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            markShaftModuleLocal(lightX, upperY + 3, lightZ);
            placeLantern(lightX, upperY + 2, lightZ);
            setBlockLocal(floorX, upperY, floorZ, pickServiceFloorBlock(floorX, upperY, floorZ));
            markShaftWalkableLocal(floorX, upperY, floorZ);
        }
    }

    private List<ServiceStairNode> buildCoreServiceStair(int cx, int cz, int hubFloorY, int upperY, int ringOuterRadius) {
        int startY = hubFloorY + 1;
        if (upperY <= startY + 2) {
            return List.of();
        }

        int laneA = cx - ringOuterRadius + 1;
        int laneB = laneA + 2;
        int startZ = cz + 4;

        List<ServiceStairNode> nodes = planCoreServiceStairNodes(startY, upperY, startZ, laneA, laneB);
        if (nodes.isEmpty()) {
            return nodes;
        }

        int minZ = startZ;
        int maxZ = startZ;
        for (ServiceStairNode node : nodes) {
            minZ = Math.min(minZ, node.z());
            maxZ = Math.max(maxZ, node.z());
        }
        minZ = Math.min(minZ, cz);
        maxZ = Math.max(maxZ, cz);

        carveCoreServiceStairTube(laneA, laneB, minZ, maxZ, startY, upperY);
        buildServiceStairConnector(laneA, laneB + 1, startY, startZ, cz);
        for (ServiceStairNode node : nodes) {
            placeServiceStairNode(node, laneA, laneB);
        }

        int topZ = nodes.get(nodes.size() - 1).z();
        buildServiceStairConnector(laneA, laneB + 1, upperY, topZ, cz);
        placeServiceStairLighting(nodes, laneA, startY, upperY);
        return nodes;
    }

    private List<ServiceStairNode> planCoreServiceStairNodes(int startY, int endY, int startZ, int laneA, int laneB) {
        List<ServiceStairNode> nodes = new ArrayList<>();
        int y = startY;
        int z = startZ;
        int laneStart = laneA;
        Direction travel = Direction.NORTH;
        int flightLen = 6;

        nodes.add(new ServiceStairNode(laneA, laneB + 1, y, z, true));
        while (y < endY) {
            for (int step = 0; step < flightLen && y < endY; step++) {
                y++;
                z += travel.getStepZ();
                nodes.add(new ServiceStairNode(laneStart, laneStart + 1, y, z, false));
            }
            if (y >= endY) {
                break;
            }

            for (int landing = 0; landing < 2; landing++) {
                z += travel.getStepZ();
                nodes.add(new ServiceStairNode(laneA, laneB + 1, y, z, true));
            }

            laneStart = laneStart == laneA ? laneB : laneA;
            travel = travel.getOpposite();
            nodes.add(new ServiceStairNode(laneA, laneB + 1, y, z, true));
        }

        return nodes;
    }

    private void carveCoreServiceStairTube(int laneA, int laneB, int minZ, int maxZ, int startY, int topY) {
        for (int lx = laneA - 1; lx <= laneB + 2; lx++) {
            for (int lz = minZ - 1; lz <= maxZ + 1; lz++) {
                for (int y = startY; y <= topY + 5; y++) {
                    setAirLocal(lx, y, lz);
                }
            }
        }

        for (int lz = minZ - 1; lz <= maxZ + 1; lz++) {
            for (int y = startY; y <= topY + 4; y++) {
                setBlockLocal(laneA - 1, y, lz, pickInterior(wx(laneA - 1), y, wz(lz)));
            }
        }
    }

    private void placeServiceStairNode(ServiceStairNode node, int laneA, int laneB) {
        for (int lx = node.xMin(); lx <= node.xMax(); lx++) {
            setBlockLocal(lx, node.y(), node.z(), pickServiceFloorBlock(lx, node.y(), node.z()));
            markShaftWalkableLocal(lx, node.y(), node.z());
            clearThreeBlockHeadroom(lx, node.y(), node.z());
            reinforceFloorSupport(lx, node.y() - 1, node.z(), 3);
        }

        int railX = node.xMax() + 1;
        setBlockLocal(railX, node.y() + 1, node.z(), Blocks.BLACKSTONE_WALL.defaultBlockState());
        markShaftModuleLocal(railX, node.y() + 1, node.z());
        if (node.landing()) {
            setBlockLocal(laneA - 1, node.y() + 1, node.z(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            setBlockLocal(railX, node.y() + 2, node.z(), Blocks.BASALT.defaultBlockState());
            markShaftModuleLocal(laneA - 1, node.y() + 1, node.z());
            markShaftModuleLocal(railX, node.y() + 2, node.z());
        }
    }

    private void buildServiceStairConnector(int xMin, int xMax, int y, int zFrom, int zTo) {
        int step = Integer.compare(zTo, zFrom);
        int z = zFrom;
        while (true) {
            for (int lx = xMin; lx <= xMax; lx++) {
                setBlockLocal(lx, y, z, pickServiceFloorBlock(lx, y, z));
                markShaftWalkableLocal(lx, y, z);
                clearThreeBlockHeadroom(lx, y, z);
                reinforceFloorSupport(lx, y - 1, z, 3);
            }
            setBlockLocal(xMax + 1, y + 1, z, Blocks.BLACKSTONE_WALL.defaultBlockState());
            markShaftModuleLocal(xMax + 1, y + 1, z);
            if (z == zTo || step == 0) {
                break;
            }
            z += step;
        }
    }

    private void placeServiceStairLighting(List<ServiceStairNode> nodes, int laneA, int startY, int endY) {
        for (int y = startY + 8; y <= endY; y += 8) {
            ServiceStairNode closest = null;
            int best = Integer.MAX_VALUE;
            for (ServiceStairNode node : nodes) {
                int dist = Math.abs(node.y() - y);
                if (dist < best) {
                    best = dist;
                    closest = node;
                }
            }
            if (closest == null) {
                continue;
            }
            int lanternX = laneA - 1;
            int lanternZ = closest.z();
            setBlockLocal(lanternX, closest.y() + 2, lanternZ, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            placeLantern(lanternX, closest.y() + 1, lanternZ);
        }
    }

    private CoreLadderAccess buildCoreLadderAccess(int cx, int cz, int hubFloorY, int upperY, int ringOuterRadius) {
        int ladderBaseY = hubFloorY + 1;
        int ladderTopY = upperY + 1;
        if (ladderTopY <= ladderBaseY + 2) {
            return new CoreLadderAccess(0, 0, 0, 0, Direction.NORTH, List.of());
        }

        int ladderX = cx + ringOuterRadius - 1;
        int ladderZ = cz + 1;
        Direction facing = Direction.WEST;
        int backingX = ladderX + facing.getOpposite().getStepX();
        int backingZ = ladderZ + facing.getOpposite().getStepZ();
        int playerX = ladderX + facing.getStepX();
        int playerZ = ladderZ + facing.getStepZ();

        for (int y = ladderBaseY; y <= ladderTopY + 2; y++) {
            setAirLocalForced(ladderX, y, ladderZ);
            setAirLocalForced(playerX, y, playerZ);
            setAirLocalForced(playerX, y + 1, playerZ);
            setAirLocalForced(ladderX, y, ladderZ + 1);
            setAirLocalForced(playerX, y, playerZ + 1);
            setAirLocalForced(playerX, y + 1, playerZ + 1);
            setBlockLocalForced(backingX, y, backingZ, pickPermanentLadderBacking(ladderX, y, ladderZ));
            setBlockLocalForced(backingX, y, backingZ + 1, pickPermanentLadderBacking(ladderX, y, ladderZ + 1));
            markShaftModuleLocal(backingX, y, backingZ);
            markShaftModuleLocal(backingX, y, backingZ + 1);
        }

        placeLadderShaftWithBacking(ladderX, ladderZ, ladderBaseY, ladderTopY, facing, true);
        for (int y = ladderBaseY; y <= ladderTopY; y++) {
            markShaftModuleLocal(ladderX, y, ladderZ);
        }

        List<Integer> landingYs = new ArrayList<>();
        int nextLanding = ladderBaseY + 8;
        while (nextLanding < ladderTopY) {
            landingYs.add(nextLanding);
            nextLanding += 8;
        }
        if (landingYs.isEmpty() || landingYs.get(landingYs.size() - 1) != upperY) {
            landingYs.add(upperY);
        }

        for (int landingY : landingYs) {
            buildCoreLadderLanding(ladderX, ladderZ, landingY, ringOuterRadius, cx, cz);
        }

        return new CoreLadderAccess(ladderX, ladderZ, ladderBaseY, ladderTopY, facing, landingYs);
    }

    private void buildCoreLadderLanding(
            int ladderX,
            int ladderZ,
            int landingY,
            int ringOuterRadius,
            int cx,
            int cz) {
        int minX = ladderX - 2;
        int maxX = ladderX - 1;
        for (int lx = minX; lx <= maxX; lx++) {
            for (int lz = ladderZ - 1; lz <= ladderZ + 1; lz++) {
                setBlockLocal(lx, landingY, lz, pickServiceFloorBlock(lx, landingY, lz));
                markShaftWalkableLocal(lx, landingY, lz);
                clearThreeBlockHeadroom(lx, landingY, lz);
                reinforceFloorSupport(lx, landingY - 1, lz, 3);
            }
        }

        for (int lz = ladderZ - 1; lz <= ladderZ + 1; lz++) {
            setBlockLocal(ladderX - 3, landingY + 1, lz, Blocks.BLACKSTONE_WALL.defaultBlockState());
            markShaftModuleLocal(ladderX - 3, landingY + 1, lz);
        }

        int targetX = cx + ringOuterRadius - 2;
        int zStep = Integer.compare(cz, ladderZ);
        int xStep = Integer.compare(targetX, minX);
        int x = minX;
        int z = ladderZ;
        while (x != targetX || z != cz) {
            setBlockLocal(x, landingY, z, pickServiceFloorBlock(x, landingY, z));
            markShaftWalkableLocal(x, landingY, z);
            clearThreeBlockHeadroom(x, landingY, z);
            reinforceFloorSupport(x, landingY - 1, z, 3);
            setBlockLocal(x - 1, landingY + 1, z, Blocks.BLACKSTONE_WALL.defaultBlockState());
            markShaftModuleLocal(x - 1, landingY + 1, z);
            if (x != targetX) {
                x += xStep;
            } else if (z != cz) {
                z += zStep;
            }
        }
        setBlockLocal(targetX, landingY, cz, pickServiceFloorBlock(targetX, landingY, cz));
        markShaftWalkableLocal(targetX, landingY, cz);
    }

    private void validateCoreLadderAccess(CoreLadderAccess ladder) {
        if (ladder.yMax() <= ladder.yMin()) {
            return;
        }
        for (int y = ladder.yMin(); y <= ladder.yMax(); y++) {
            ensureLadderSegmentLocal(ladder.ladderX(), y, ladder.ladderZ(), ladder.facing());
            ensureLadderTubeClearanceLocal(ladder.ladderX(), y, ladder.ladderZ(), ladder.facing());
        }
    }

    private void validateCoreServiceStair(List<ServiceStairNode> nodes) {
        if (nodes.isEmpty()) {
            return;
        }

        ServiceStairNode prev = null;
        for (ServiceStairNode node : nodes) {
            for (int lx = node.xMin(); lx <= node.xMax(); lx++) {
                BlockPos floorPos = new BlockPos(wx(lx), node.y(), wz(node.z()));
                if (!level.getBlockState(floorPos).blocksMotion()) {
                    setBlockLocalForced(lx, node.y(), node.z(), pickServiceFloorBlock(lx, node.y(), node.z()));
                }
                markShaftWalkableLocal(lx, node.y(), node.z());
                setAirLocalForced(lx, node.y() + 1, node.z());
                setAirLocalForced(lx, node.y() + 2, node.z());
                BlockPos comfortPos = new BlockPos(wx(lx), node.y() + 3, wz(node.z()));
                if (level.getBlockState(comfortPos).blocksMotion()) {
                    setAirLocalForced(lx, node.y() + 3, node.z());
                }
            }

            if (prev != null) {
                int dy = node.y() - prev.y();
                if (dy > 1) {
                    int fillZ = prev.z() + Integer.signum(node.z() - prev.z());
                    int fillMinX = Math.min(prev.xMin(), node.xMin());
                    int fillMaxX = Math.max(prev.xMax(), node.xMax());
                    for (int fillY = prev.y() + 1; fillY < node.y(); fillY++) {
                        for (int lx = fillMinX; lx <= fillMaxX; lx++) {
                            setBlockLocalForced(lx, fillY, fillZ, pickServiceFloorBlock(lx, fillY, fillZ));
                            markShaftWalkableLocal(lx, fillY, fillZ);
                            setAirLocalForced(lx, fillY + 1, fillZ);
                            setAirLocalForced(lx, fillY + 2, fillZ);
                        }
                    }
                }
            }
            prev = node;
        }
    }

    private void protectCoreUpperDistrict(
            int cx,
            int cz,
            int hubFloorY,
            int upperY,
            int ringOuterRadius,
            List<ServiceStairNode> stairNodes,
            CoreLadderAccess ladderAccess) {
        int ringPad = ringOuterRadius + 10;
        markProtectedAabbLocal(cx - ringPad, upperY - 1, cz - ringPad, cx + ringPad, upperY + 6, cz + ringPad);

        if (!stairNodes.isEmpty()) {
            int laneA = cx - ringOuterRadius + 1;
            int laneB = laneA + 2;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (ServiceStairNode node : stairNodes) {
                minZ = Math.min(minZ, node.z());
                maxZ = Math.max(maxZ, node.z());
            }
            minZ = Math.min(minZ, cz);
            maxZ = Math.max(maxZ, cz + 4);
            markProtectedAabbLocal(laneA - 1, hubFloorY, minZ - 1, laneB + 2, upperY + 5, maxZ + 1);
        }

        if (ladderAccess.yMax() > ladderAccess.yMin()) {
            int lx = ladderAccess.ladderX();
            int lz = ladderAccess.ladderZ();
            int lyMin = ladderAccess.yMin();
            int lyMax = ladderAccess.yMax() + 3;
            markProtectedAabbLocal(lx - 3, lyMin, lz - 2, lx + 2, lyMax, lz + 3);
        }
    }

    private void cleanupShaftWalkwayBand(int cx, int cz, int upperY, int ringOuterRadius) {
        int bandYMin = upperY - 2;
        int bandYMax = upperY + 4;
        int bandRMin = Math.max(4, ringOuterRadius - 4);
        int bandRMax = ringOuterRadius + 3;
        int limit = bandRMax + 1;
        int bandRMinSq = bandRMin * bandRMin;
        int bandRMaxSq = bandRMax * bandRMax;

        for (int lx = cx - limit; lx <= cx + limit; lx++) {
            for (int lz = cz - limit; lz <= cz + limit; lz++) {
                int dx = lx - cx;
                int dz = lz - cz;
                int d2 = (dx * dx) + (dz * dz);
                if (d2 < bandRMinSq || d2 > bandRMaxSq) {
                    continue;
                }
                for (int y = bandYMin; y <= bandYMax; y++) {
                    long key = BlockPos.asLong(wx(lx), y, wz(lz));
                    if (shaftModuleBlocks.contains(key)) {
                        continue;
                    }

                    BlockPos pos = new BlockPos(wx(lx), y, wz(lz));
                    BlockState state = level.getBlockState(pos);
                    if (!state.blocksMotion() || state.is(Blocks.BEDROCK)) {
                        continue;
                    }

                    if (isProtectedNearWalkable(lx, y, lz)) {
                        continue;
                    }

                    if (isInteriorBandProtrusion(cx, cz, lx, y, lz)) {
                        setAirLocalForced(lx, y, lz);
                    }
                }
            }
        }
    }

    private boolean isProtectedNearWalkable(int lx, int y, int lz) {
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                for (int oy = -1; oy <= 1; oy++) {
                    long neighbor = BlockPos.asLong(wx(lx + ox), y + oy, wz(lz + oz));
                    if (shaftWalkableBlocks.contains(neighbor)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isInteriorBandProtrusion(int cx, int cz, int lx, int y, int lz) {
        Direction inward = directionToCenter(lx - cx, lz - cz);
        int inX = lx + inward.getStepX();
        int inZ = lz + inward.getStepZ();
        BlockPos inwardPos = new BlockPos(wx(inX), y, wz(inZ));
        if (!level.getBlockState(inwardPos).isAir()) {
            return false;
        }
        BlockPos above = new BlockPos(wx(lx), y + 1, wz(lz));
        return level.getBlockState(above).isAir();
    }

    private void validateShaftWalkabilityAndSafety() {
        List<Long> nodes = new ArrayList<>(shaftWalkableBlocks);
        for (long key : nodes) {
            int x = BlockPos.getX(key);
            int y = BlockPos.getY(key);
            int z = BlockPos.getZ(key);
            int lx = x - origin.getX();
            int lz = z - origin.getZ();

            BlockPos floorPos = new BlockPos(x, y, z);
            if (!level.getBlockState(floorPos).blocksMotion()) {
                setBlockWorldForced(x, y, z, pickServiceFloorBlock(lx, y, lz));
                shaftModuleBlocks.add(key);
            }

            clearWalkableHeadroomWorldPreservingFixtures(x, y, z);
            clearWalkableNeighborhoodHeadroomWorldPreservingFixtures(x, y, z);

            ensureWalkableContinuity(x, y, z);
            ensureWalkableEdgeRailings(x, y, z);
        }
    }

    private void ensureWalkableContinuity(int x, int y, int z) {
        if (hasWalkableNeighborWithinStep(x, y, z)) {
            return;
        }

        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            for (int step = 1; step <= 2; step++) {
                int nx = x + dir.getStepX() * step;
                int nz = z + dir.getStepZ() * step;
                int targetY = findWalkableYAt(nx, nz, y);
                if (targetY == Integer.MIN_VALUE || Math.abs(targetY - y) > 1) {
                    continue;
                }

                for (int s = 1; s <= step; s++) {
                    int bx = x + dir.getStepX() * s;
                    int bz = z + dir.getStepZ() * s;
                    int by = (s == step) ? targetY : y;
                    int lx = bx - origin.getX();
                    int lz = bz - origin.getZ();
                    setBlockWorldForced(bx, by, bz, pickServiceFloorBlock(lx, by, lz));
                    markShaftWalkableLocal(lx, by, lz);
                    clearWalkableHeadroomWorldPreservingFixtures(bx, by, bz);
                }
                return;
            }
        }
    }

    private boolean hasWalkableNeighborWithinStep(int x, int y, int z) {
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int nx = x + dir.getStepX();
            int nz = z + dir.getStepZ();
            if (findWalkableYAt(nx, nz, y) != Integer.MIN_VALUE) {
                return true;
            }
        }
        return false;
    }

    private int findWalkableYAt(int x, int z, int nearY) {
        for (int y = nearY - 1; y <= nearY + 1; y++) {
            if (shaftWalkableBlocks.contains(BlockPos.asLong(x, y, z))) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private void ensureWalkableEdgeRailings(int x, int y, int z) {
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int nx = x + dir.getStepX();
            int nz = z + dir.getStepZ();
            long neighborFloor = BlockPos.asLong(nx, y, nz);
            if (shaftWalkableBlocks.contains(neighborFloor)) {
                continue;
            }
            if (!isDropDeeperThanTwo(nx, y, nz)) {
                continue;
            }

            int lx = nx - origin.getX();
            int lz = nz - origin.getZ();
            if (level.getBlockState(new BlockPos(nx, y + 1, nz)).isAir()) {
                setBlockLocal(lx, y + 1, lz, Blocks.BLACKSTONE_WALL.defaultBlockState());
                markShaftModuleLocal(lx, y + 1, lz);
            }
        }
    }

    private boolean isDropDeeperThanTwo(int x, int y, int z) {
        if (y <= level.getMinBuildHeight() + 2) {
            return false;
        }
        BlockPos below1 = new BlockPos(x, y - 1, z);
        BlockPos below2 = new BlockPos(x, y - 2, z);
        return !level.getBlockState(below1).blocksMotion() && !level.getBlockState(below2).blocksMotion();
    }

    private void clearTwoBlockHeadroomLocalPreservingFixtures(int lx, int floorY, int lz) {
        int x = wx(lx);
        int z = wz(lz);
        clearHeadroomCellWorldPreservingFixtures(x, floorY + 1, z, true);
        clearHeadroomCellWorldPreservingFixtures(x, floorY + 2, z, true);
    }

    private void clearWalkableHeadroomWorldPreservingFixtures(int x, int floorY, int z) {
        clearHeadroomCellWorldPreservingFixtures(x, floorY + 1, z, true);
        clearHeadroomCellWorldPreservingFixtures(x, floorY + 2, z, true);
        clearHeadroomCellWorldPreservingFixtures(x, floorY + 3, z, false);
    }

    private void clearWalkableNeighborhoodHeadroomWorldPreservingFixtures(int x, int floorY, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = x + dx;
                int nz = z + dz;
                clearHeadroomCellWorldPreservingFixtures(nx, floorY + 1, nz, true);
                clearHeadroomCellWorldPreservingFixtures(nx, floorY + 2, nz, true);
                clearHeadroomCellWorldPreservingFixtures(nx, floorY + 3, nz, true);
            }
        }
    }

    private void clearHeadroomCellWorldPreservingFixtures(int x, int y, int z, boolean clearNonSolid) {
        long key = BlockPos.asLong(x, y, z);
        if (protectedBlocks.contains(key)) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        if (!clearNonSolid && !state.blocksMotion()) {
            return;
        }
        if (isCriticalTraversalFixture(pos, state)) {
            return;
        }
        setAirWorldForced(x, y, z);
    }

    private boolean isCriticalTraversalFixture(BlockPos pos, BlockState state) {
        if (state.is(Blocks.LADDER) || state.is(Blocks.LANTERN) || state.is(Blocks.CHAIN)) {
            return true;
        }

        BlockState below = level.getBlockState(pos.below());
        if (below.is(Blocks.LANTERN) || below.is(Blocks.CHAIN)) {
            return true;
        }

        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            if (level.getBlockState(pos.relative(dir)).is(Blocks.LADDER)) {
                return true;
            }
        }
        return false;
    }

    private void markShaftModuleLocal(int lx, int y, int lz) {
        shaftModuleBlocks.add(BlockPos.asLong(wx(lx), y, wz(lz)));
    }

    private void markShaftWalkableLocal(int lx, int y, int lz) {
        long key = BlockPos.asLong(wx(lx), y, wz(lz));
        shaftWalkableBlocks.add(key);
        shaftModuleBlocks.add(key);
    }

    private Map<Integer, List<LocalXZ>> buildSpiralServiceWalkway(
            int cx,
            int cz,
            int radius,
            int baseY,
            int topY) {
        Map<Integer, List<LocalXZ>> nodesByY = new HashMap<>();
        if (topY < baseY) {
            return nodesByY;
        }

        int sideLength = Math.max(8, radius * 2);
        for (int loop = 0; ; loop++) {
            int loopBaseY = baseY + (loop * 16);
            if (loopBaseY > topY) {
                break;
            }
            for (int side = 0; side < 4; side++) {
                for (int step = 0; step < sideLength; step++) {
                    int y = loopBaseY + (side * 4) + ((step * 4) / sideLength);
                    if (y > topY) {
                        continue;
                    }
                    LocalXZ node = spiralNodeAt(cx, cz, radius, side, step);
                    laySpiralWalkwayNode(cx, cz, node.x(), y, node.z());
                    nodesByY.computeIfAbsent(y, unused -> new ArrayList<>()).add(node);
                }
            }
        }

        return nodesByY;
    }

    private LocalXZ spiralNodeAt(int cx, int cz, int radius, int side, int step) {
        return switch (side) {
            case 0 -> new LocalXZ(cx - radius, cz + radius - step);
            case 1 -> new LocalXZ(cx - radius + step, cz - radius);
            case 2 -> new LocalXZ(cx + radius, cz - radius + step);
            default -> new LocalXZ(cx + radius - step, cz + radius);
        };
    }

    private void laySpiralWalkwayNode(int cx, int cz, int lx, int y, int lz) {
        Direction inward = directionToCenter(lx - cx, lz - cz);

        int wallLaneX = lx;
        int wallLaneZ = lz;
        int innerLaneX = lx + inward.getStepX();
        int innerLaneZ = lz + inward.getStepZ();

        setBlockLocal(wallLaneX, y, wallLaneZ, pickServiceFloorBlock(wallLaneX, y, wallLaneZ));
        setBlockLocal(innerLaneX, y, innerLaneZ, pickServiceFloorBlock(innerLaneX, y, innerLaneZ));
        clearThreeBlockHeadroom(wallLaneX, y, wallLaneZ);
        clearThreeBlockHeadroom(innerLaneX, y, innerLaneZ);
        reinforceFloorSupport(wallLaneX, y - 1, wallLaneZ, 3);
        reinforceFloorSupport(innerLaneX, y - 1, innerLaneZ, 3);

        int wallSupportX = wallLaneX - inward.getStepX();
        int wallSupportZ = wallLaneZ - inward.getStepZ();
        if (!level.getBlockState(new BlockPos(wx(wallSupportX), y, wz(wallSupportZ))).blocksMotion()) {
            setBlockLocal(wallSupportX, y, wallSupportZ, pickInterior(wx(wallSupportX), y, wz(wallSupportZ)));
        }

        int railX = innerLaneX + inward.getStepX();
        int railZ = innerLaneZ + inward.getStepZ();
        setBlockLocal(railX, y + 1, railZ, Blocks.BLACKSTONE_WALL.defaultBlockState());
    }

    private void connectHubToServiceWalkway(int cx, int cz, int hubFloorY, int serviceBaseY, int serviceRadius) {
        int startX = cx - 4;
        int startY = hubFloorY + 1;
        int startZ = cz;

        int endX = cx - serviceRadius + 1;
        int endY = serviceBaseY + 2;
        int endZ = cz;

        buildGuardedConnector(startX, startY, startZ, endX, endY, endZ);
    }

    private void connectPerchToServiceWalkway(PerchAnchor anchor, Map<Integer, List<LocalXZ>> serviceNodes) {
        LocalXYZ target = findNearestServiceNode(anchor, serviceNodes);
        if (target == null) {
            return;
        }
        buildGuardedConnector(anchor.x(), anchor.y(), anchor.z(), target.x(), target.y(), target.z());
    }

    private LocalXYZ findNearestServiceNode(PerchAnchor anchor, Map<Integer, List<LocalXZ>> serviceNodes) {
        LocalXYZ best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int y = anchor.y() - 2; y <= anchor.y() + 2; y++) {
            List<LocalXZ> nodes = serviceNodes.get(y);
            if (nodes == null || nodes.isEmpty()) {
                continue;
            }
            for (LocalXZ node : nodes) {
                int dx = node.x() - anchor.x();
                int dz = node.z() - anchor.z();
                int dy = y - anchor.y();
                int dist = (dx * dx) + (dz * dz) + (dy * dy * 8);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = new LocalXYZ(node.x(), y, node.z());
                }
            }
        }

        return best;
    }

    private void buildGuardedConnector(int startX, int startY, int startZ, int endX, int endY, int endZ) {
        int dx = endX - startX;
        int dz = endZ - startZ;
        int totalSteps = Math.abs(dx) + Math.abs(dz);
        if (totalSteps == 0) {
            placeGuardedConnectorNode(startX, startY, startZ, Direction.NORTH);
            return;
        }

        int x = startX;
        int z = startZ;
        int stepIndex = 0;
        Direction travel = Math.abs(dx) >= Math.abs(dz)
                ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);

        placeGuardedConnectorNode(x, interpolateConnectorY(startY, endY, stepIndex, totalSteps), z, travel);

        int xStep = Integer.signum(dx);
        while (x != endX) {
            x += xStep;
            stepIndex++;
            travel = xStep >= 0 ? Direction.EAST : Direction.WEST;
            placeGuardedConnectorNode(x, interpolateConnectorY(startY, endY, stepIndex, totalSteps), z, travel);
        }

        int zStep = Integer.signum(dz);
        while (z != endZ) {
            z += zStep;
            stepIndex++;
            travel = zStep >= 0 ? Direction.SOUTH : Direction.NORTH;
            placeGuardedConnectorNode(x, interpolateConnectorY(startY, endY, stepIndex, totalSteps), z, travel);
        }
    }

    private int interpolateConnectorY(int startY, int endY, int stepIndex, int totalSteps) {
        return startY + ((endY - startY) * stepIndex) / Math.max(1, totalSteps);
    }

    private void placeGuardedConnectorNode(int lx, int y, int lz, Direction travel) {
        setBlockLocal(lx, y, lz, pickServiceFloorBlock(lx, y, lz));
        clearThreeBlockHeadroom(lx, y, lz);
        reinforceFloorSupport(lx, y - 1, lz, 3);

        Direction right = travel.getClockWise();
        setBlockLocal(lx + right.getStepX(), y + 1, lz + right.getStepZ(), Blocks.BLACKSTONE_WALL.defaultBlockState());
        setBlockLocal(lx - right.getStepX(), y + 1, lz - right.getStepZ(), Blocks.BLACKSTONE_WALL.defaultBlockState());
    }

    private void clearThreeBlockHeadroom(int lx, int floorY, int lz) {
        setAirLocal(lx, floorY + 1, lz);
        setAirLocal(lx, floorY + 2, lz);
        setAirLocal(lx, floorY + 3, lz);
    }

    private void reinforceFloorSupport(int lx, int startY, int lz, int depth) {
        int minY = level.getMinBuildHeight();
        for (int y = startY; y >= minY && y > startY - depth; y--) {
            BlockPos pos = new BlockPos(wx(lx), y, wz(lz));
            if (level.getBlockState(pos).blocksMotion()) {
                break;
            }
            setBlockLocal(lx, y, lz, Blocks.BASALT.defaultBlockState());
        }
    }

    private void rebuildCorePerchPlatforms(BlockPos corePos) {
        int cx = corePos.getX() - origin.getX();
        int cz = corePos.getZ() - origin.getZ();
        int hubFloorY = hubCenterY - 1;
        buildCorePerchPlatformsAndSpiral(cx, cz, corePos.getY(), hubFloorY);
    }

    private void buildShaftLightTeeth(int cx, int cz, int rimY) {
        int startY = rimY - 2;
        int radius = settings.calderaRadiusTop() + 1;
        int count = 24;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D) * i / count;
            int lx = cx + (int) Math.round(Math.cos(angle) * radius);
            int lz = cz + (int) Math.round(Math.sin(angle) * radius);
            int len = 6 + (int) Math.floorMod(mix(worldSeed ^ 4463L, i, startY, 5), 5);
            for (int d = 0; d < len; d++) {
                int y = startY - d;
                BlockState state = (Math.floorMod(mix(worldSeed ^ 4483L, i, d, 7), 9) == 0)
                        ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                        : Blocks.BASALT.defaultBlockState();
                setBlockLocal(lx, y, lz, state);
            }
        }
    }

    private void buildCoreGlowPockets(int cx, int cz, int hubFloorY) {
        int y = hubFloorY + 7;
        for (LocalXZ offset : List.of(new LocalXZ(11, -11), new LocalXZ(-11, 11))) {
            int gx = cx + offset.x;
            int gz = cz + offset.z;
            int tx = Integer.compare(cx, gx);
            int tz = Integer.compare(cz, gz);

            setBlockLocal(gx, y, gz, Blocks.MAGMA_BLOCK.defaultBlockState());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        int lx = gx + dx;
                        int ly = y + dy;
                        int lz = gz + dz;
                        boolean frontWindow = dx == tx && dz == tz && dy >= 0;
                        if (frontWindow) {
                            setBlockLocal(lx, ly, lz, Blocks.IRON_BARS.defaultBlockState());
                        } else if (!level.getBlockState(new BlockPos(wx(lx), ly, wz(lz))).blocksMotion()) {
                            setBlockLocal(lx, ly, lz, Blocks.BASALT.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    private void buildCoreRing(int cx, int ringY, int cz) {
        int outer = 6;
        int inner = 4;
        for (int lx = cx - outer; lx <= cx + outer; lx++) {
            for (int lz = cz - outer; lz <= cz + outer; lz++) {
                double r = Math.sqrt((double) (lx - cx) * (lx - cx) + (double) (lz - cz) * (lz - cz));
                if (r < inner - 0.1D || r > outer + 0.2D) {
                    continue;
                }
                for (int y = ringY; y <= ringY + 1; y++) {
                    setBlockLocal(lx, y, lz, pickCoreRingBlock(lx, y, lz));
                }
            }
        }

        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int lx = cx + dir.getStepX() * outer;
            int lz = cz + dir.getStepZ() * outer;
            setBlockLocal(lx, ringY, lz, Blocks.GILDED_BLACKSTONE.defaultBlockState());
            setBlockLocal(lx, ringY + 1, lz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
    }

    private BlockState pickCoreRingBlock(int lx, int y, int lz) {
        long roll = mix(worldSeed ^ 4051L, wx(lx), y, wz(lz));
        if (Math.floorMod(roll, 11L) == 0L) {
            return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        }
        return (roll & 1L) == 0L
                ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                : Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    private void buildCoreChainBraces(int cx, int coreY, int cz, int ringY, int rimY) {
        int anchorY = Math.min(coreY + 18, rimY - 4);
        if (anchorY <= ringY + 2) {
            anchorY = ringY + 3;
        }

        for (int sx : List.of(-1, 1)) {
            for (int sz : List.of(-1, 1)) {
                int anchorX = cx + sx * 5;
                int anchorZ = cz + sz * 5;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int lx = anchorX + dx;
                        int lz = anchorZ + dz;
                        setBlockLocal(lx, anchorY, lz, ((dx + dz) & 1) == 0
                                ? Blocks.SMOOTH_BASALT.defaultBlockState()
                                : Blocks.BASALT.defaultBlockState());
                        setBlockLocal(lx, anchorY - 1, lz, Blocks.BASALT.defaultBlockState());
                    }
                }
                int ringX = cx + sx * 4;
                int ringZ = cz + sz * 4;
                drawChainLine(anchorX, anchorY - 1, anchorZ, ringX, ringY + 1, ringZ);
            }
        }
    }

    private void buildCoreRibs(int cx, int ringY, int cz) {
        int ribCount = 8;
        for (int i = 0; i < ribCount; i++) {
            double angle = (Math.PI * 2.0D) * i / ribCount;
            int lx = cx + (int) Math.round(Math.cos(angle) * 6.0D);
            int lz = cz + (int) Math.round(Math.sin(angle) * 6.0D);
            int len = 6 + (int) Math.floorMod(mix(worldSeed ^ 4129L, i, ringY, 17), 5);
            boolean thick = Math.floorMod(mix(worldSeed ^ 4139L, i, ringY, 3), 4) == 0;
            int tangentX = (int) Math.round(Math.cos(angle + (Math.PI / 2.0D)));
            int tangentZ = (int) Math.round(Math.sin(angle + (Math.PI / 2.0D)));

            for (int d = 1; d <= len; d++) {
                int y = ringY - d;
                setBlockLocal(lx, y, lz, pickCoreRibBlock(lx, y, lz));
                if (thick && (d % 2 == 0)) {
                    setBlockLocal(lx + tangentX, y, lz + tangentZ, pickCoreRibBlock(lx + tangentX, y, lz + tangentZ));
                }
            }
        }
    }

    private BlockState pickCoreRibBlock(int lx, int y, int lz) {
        int roll = (int) Math.floorMod(mix(worldSeed ^ 4177L, wx(lx), y, wz(lz)), 4);
        return switch (roll) {
            case 0 -> Blocks.BASALT.defaultBlockState();
            case 1 -> Blocks.BLACKSTONE_WALL.defaultBlockState();
            case 2 -> Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            default -> Blocks.DEEPSLATE_TILE_WALL.defaultBlockState();
        };
    }

    private void buildCoreHalo(int cx, int coreY, int cz, int ringY) {
        int haloY = coreY + 3;
        int radius = 3 + (int) Math.floorMod(mix(worldSeed ^ 4211L, cx, coreY, cz), 2);
        int limit = radius + 1;

        for (int lx = cx - limit; lx <= cx + limit; lx++) {
            for (int lz = cz - limit; lz <= cz + limit; lz++) {
                double r = Math.sqrt((double) (lx - cx) * (lx - cx) + (double) (lz - cz) * (lz - cz));
                if (r >= radius - 0.65D && r <= radius + 0.5D) {
                    BlockState state = (Math.floorMod(mix(worldSeed ^ 4229L, lx, haloY, lz), 5) == 0)
                            ? Blocks.CHAIN.defaultBlockState()
                            : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                    setBlockLocal(lx, haloY, lz, state);
                }
            }
        }

        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int hx = cx + dir.getStepX() * radius;
            int hz = cz + dir.getStepZ() * radius;
            for (int y = ringY + 2; y < haloY; y++) {
                setBlockLocal(hx, y, hz, Blocks.CHAIN.defaultBlockState());
            }
        }
    }

    private void buildCoreShadowPedestal(int cx, int coreY, int cz) {
        int pedestalY = Math.max(hubCenterY + 4, coreY - 10);
        if (pedestalY >= coreY - 3) {
            return;
        }

        int supportCount = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos below = new BlockPos(wx(cx + dx), pedestalY - 1, wz(cz + dz));
                if (level.getBlockState(below).blocksMotion()) {
                    supportCount++;
                }
            }
        }
        if (supportCount < 6) {
            return;
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlockLocal(cx + dx, pedestalY, cz + dz, Blocks.DEEPSLATE_TILES.defaultBlockState());
            }
        }
        for (int dx : List.of(-1, 1)) {
            for (int dz : List.of(-1, 1)) {
                int lx = cx + dx;
                int lz = cz + dz;
                BlockPos above = new BlockPos(wx(lx), pedestalY + 1, wz(lz));
                if (level.getBlockState(above).isAir()) {
                    setBlockLocal(lx, pedestalY + 1, lz, Blocks.CANDLE.defaultBlockState());
                }
            }
        }
    }

    private void clearCoreShadowPedestal(BlockPos corePos) {
        int cx = corePos.getX() - origin.getX();
        int cz = corePos.getZ() - origin.getZ();
        int coreY = corePos.getY();
        int pedestalY = Math.max(hubCenterY + 4, coreY - 10);
        if (pedestalY >= coreY - 3) {
            return;
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = pedestalY; y <= pedestalY + 2; y++) {
                    setAirLocal(cx + dx, y, cz + dz);
                }
            }
        }
    }

    private void addCoreRingAccentLights(int cx, int ringY, int cz) {
        for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            int lx = cx + dir.getStepX() * 5;
            int lz = cz + dir.getStepZ() * 5;
            setBlockLocal(lx, ringY + 1, lz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            placeSealedWallVent(lx, ringY, lz, dir);
            if (level.getBlockState(new BlockPos(wx(lx), ringY - 1, wz(lz))).isAir()) {
                setBlockLocal(lx, ringY - 1, lz, wood.trapdoor.defaultBlockState());
            }
        }
    }

    private void placeHangingLanternLocal(int lx, int y, int lz) {
        int x = wx(lx);
        int z = wz(lz);
        int chainStartY = y + 1;
        int maxProbeY = Math.min(level.getMaxBuildHeight() - 2, chainStartY + 8);
        int anchorY = -1;

        for (int probeY = chainStartY + 1; probeY <= maxProbeY; probeY++) {
            BlockPos probe = new BlockPos(x, probeY, z);
            if (level.getBlockState(probe).blocksMotion()) {
                anchorY = probeY;
                break;
            }
        }
        if (anchorY < 0) {
            anchorY = Math.min(chainStartY + 3, level.getMaxBuildHeight() - 2);
            buildLanternAnchorPlateWorld(x, anchorY, z);
        }

        for (int chainY = chainStartY; chainY < anchorY; chainY++) {
            setBlockWorld(x, chainY, z, Blocks.CHAIN.defaultBlockState());
        }
        setHangingLanternWorld(x, y, z);
    }

    private void setHangingLanternWorld(int x, int y, int z) {
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        if (lantern.hasProperty(LanternBlock.HANGING)) {
            lantern = lantern.setValue(LanternBlock.HANGING, true);
        }
        setBlockWorld(x, y, z, lantern);
    }

    private void setStandingLanternWorld(int x, int y, int z) {
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        if (lantern.hasProperty(LanternBlock.HANGING)) {
            lantern = lantern.setValue(LanternBlock.HANGING, false);
        }
        setBlockWorld(x, y, z, lantern);
    }

    private void buildLanternAnchorPlateLocal(int lx, int y, int lz) {
        buildLanternAnchorPlateWorld(wx(lx), y, wz(lz));
    }

    private void buildLanternAnchorPlateWorld(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockState plate = ((dx + dz) & 1) == 0
                        ? Blocks.SMOOTH_BASALT.defaultBlockState()
                        : Blocks.BASALT.defaultBlockState();
                setBlockWorld(x + dx, y, z + dz, plate);
                protectedBlocks.add(BlockPos.asLong(x + dx, y, z + dz));
                shaftModuleBlocks.add(BlockPos.asLong(x + dx, y, z + dz));
            }
        }
    }

    private BlockPos safetyPass() {
        sealUnwantedFluids();
        BlockPos spawn = new BlockPos(origin.getX(), hubCenterY + 2, origin.getZ() - 6);
        buildSpawnPlatform(spawn);
        buildMaintenanceRoute();
        clearHazards(spawn);
        return spawn;
    }

    private void sealUnwantedFluids() {
        int radius = settings.outerRadius() + 4;
        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                for (int y = hubCenterY - 18; y <= originY + settings.height(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getFluidState().is(FluidTags.WATER)) {
                        setBlockWorld(x, y, z, Blocks.BASALT.defaultBlockState());
                    } else if (state.getFluidState().is(FluidTags.LAVA) && !intentionalLava.contains(BlockPos.asLong(x, y, z))) {
                        setBlockWorld(x, y, z, Blocks.BASALT.defaultBlockState());
                    }
                }
            }
        }
    }

    private void buildSpawnPlatform(BlockPos spawn) {
        int half = settings.platformSize() / 2;
        int floorY = spawn.getY() - 1;
        int cx = spawn.getX() - origin.getX();
        int cz = spawn.getZ() - origin.getZ();
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int lx = cx + dx;
                int lz = cz + dz;
                setBlockLocal(lx, floorY, lz, pickFloor(wx(lx), floorY, wz(lz)));
                for (int y = spawn.getY(); y <= spawn.getY() + 3; y++) {
                    setAirLocal(lx, y, lz);
                }
            }
        }
        for (int lz = cz; lz <= 0; lz++) {
            for (int w = -1; w <= 1; w++) {
                setBlockLocal(cx + w, hubCenterY - 1, lz, pickFloor(wx(cx + w), hubCenterY - 1, wz(lz)));
            }
        }
    }

    private void buildMaintenanceRoute() {
        int rimY = originY + settings.height() - 5;
        int bottomY = hubCenterY + 2;
        int shaftX = 2;
        int shaftZ = settings.calderaRadiusTop() - 2;

        for (int y = bottomY; y <= rimY; y++) {
            setAirLocal(shaftX, y, shaftZ);
            setAirLocal(shaftX, y, shaftZ - 1);
            setBlockLocal(shaftX + 1, y, shaftZ - 1, Blocks.DEEPSLATE_TILES.defaultBlockState());
        }
        placeLadderShaftWithBacking(shaftX, shaftZ, bottomY, rimY, Direction.WEST, true);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlockLocal(shaftX + dx, rimY, shaftZ + dz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                setBlockLocal(shaftX + dx, bottomY - 1, shaftZ + dz, pickFloor(wx(shaftX + dx), bottomY - 1, wz(shaftZ + dz)));
            }
        }
    }

    private void placeLadderShaftWithBacking(
            int ladderX,
            int ladderZ,
            int ladderYMin,
            int ladderYMax,
            Direction facing,
            boolean protectRegion) {
        if (ladderYMax < ladderYMin) {
            return;
        }

        BlockState ladderState = Blocks.LADDER.defaultBlockState();
        if (ladderState.hasProperty(LadderBlock.FACING)) {
            ladderState = ladderState.setValue(LadderBlock.FACING, facing);
        }

        Direction backingDir = facing.getOpposite();
        int backingX = ladderX + backingDir.getStepX();
        int backingZ = ladderZ + backingDir.getStepZ();

        for (int y = ladderYMin; y <= ladderYMax; y++) {
            setBlockLocalForced(backingX, y, backingZ, pickPermanentLadderBacking(ladderX, y, ladderZ));
            setAirLocalForced(ladderX, y, ladderZ);
            setBlockLocalForced(ladderX, y, ladderZ, ladderState);
        }

        ladderShafts.add(new LadderShaft(ladderX, ladderZ, ladderYMin, ladderYMax, facing));
        if (protectRegion) {
            protectLadderRegion(ladderX, ladderZ, ladderYMin, ladderYMax, backingX, backingZ);
        }
    }

    private BlockState pickPermanentLadderBacking(int ladderX, int y, int ladderZ) {
        return ((mix(worldSeed ^ 7747L, wx(ladderX), y, wz(ladderZ)) & 1L) == 0L)
                ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                : Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    private void protectLadderRegion(int ladderX, int ladderZ, int ladderYMin, int ladderYMax, int backingX, int backingZ) {
        int minX = Math.min(ladderX, backingX) - 1;
        int maxX = Math.max(ladderX, backingX) + 1;
        int minZ = Math.min(ladderZ, backingZ) - 1;
        int maxZ = Math.max(ladderZ, backingZ) + 1;
        int minY = ladderYMin;
        int maxY = ladderYMax + 2;
        markProtectedAabbLocal(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void markProtectedAabbLocal(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int lx = minX; lx <= maxX; lx++) {
            for (int lz = minZ; lz <= maxZ; lz++) {
                for (int y = minY; y <= maxY; y++) {
                    protectedBlocks.add(BlockPos.asLong(wx(lx), y, wz(lz)));
                }
            }
        }
    }

    private void clearHazards(BlockPos spawn) {
        for (int x = spawn.getX() - 6; x <= spawn.getX() + 6; x++) {
            for (int z = spawn.getZ() - 6; z <= spawn.getZ() + 6; z++) {
                for (int y = spawn.getY() - 1; y <= spawn.getY() + 3; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    boolean dangerous = state.getFluidState().is(FluidTags.LAVA)
                            || state.is(Blocks.MAGMA_BLOCK)
                            || state.is(Blocks.CAMPFIRE)
                            || state.is(Blocks.FIRE);
                    if (!dangerous) {
                        continue;
                    }
                    if (y <= spawn.getY() - 1) {
                        setBlockWorld(x, y, z, pickFloor(x, y, z));
                    } else {
                        setAirWorld(x, y, z);
                    }
                }
            }
        }
    }

    private void stabilityValidationPass() {
        sealUnwantedFluids();
        if (worldCoreAnchor != null && !worldCoreAnchor.equals(BlockPos.ZERO)) {
            rebuildCorePerchPlatforms(worldCoreAnchor);
        }
        validateRegisteredLadderShafts();
        validateLanternSupports();
    }

    private void validateRegisteredLadderShafts() {
        for (LadderShaft shaft : ladderShafts) {
            for (int y = shaft.yMin(); y <= shaft.yMax(); y++) {
                ensureLadderSegmentLocal(shaft.ladderX(), y, shaft.ladderZ(), shaft.facing());
                ensureLadderTubeClearanceLocal(shaft.ladderX(), y, shaft.ladderZ(), shaft.facing());
            }
            Direction backingDir = shaft.facing().getOpposite();
            int backingX = shaft.ladderX() + backingDir.getStepX();
            int backingZ = shaft.ladderZ() + backingDir.getStepZ();
            protectLadderRegion(shaft.ladderX(), shaft.ladderZ(), shaft.yMin(), shaft.yMax(), backingX, backingZ);
        }
    }

    private void validateExistingCoreLadders(BlockPos corePos) {
        int cx = corePos.getX() - origin.getX();
        int cz = corePos.getZ() - origin.getZ();
        int radius = Math.max(settings.calderaRadiusTop() + 8, 16);
        int minY = hubCenterY + 1;
        int maxY = Math.min(level.getMaxBuildHeight() - 2, corePos.getY() + 12);
        int radiusSq = radius * radius;

        for (int lx = cx - radius; lx <= cx + radius; lx++) {
            for (int lz = cz - radius; lz <= cz + radius; lz++) {
                int dx = lx - cx;
                int dz = lz - cz;
                if ((dx * dx) + (dz * dz) > radiusSq) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    BlockPos ladderPos = new BlockPos(wx(lx), y, wz(lz));
                    BlockState state = level.getBlockState(ladderPos);
                    if (!state.is(Blocks.LADDER) || !state.hasProperty(LadderBlock.FACING)) {
                        continue;
                    }
                    Direction facing = state.getValue(LadderBlock.FACING);
                    ensureLadderSegmentWorld(ladderPos, facing);
                    ensureLadderTubeClearanceWorld(ladderPos, facing);
                }
            }
        }
    }

    private void ensureLadderSegmentLocal(int ladderX, int y, int ladderZ, Direction facing) {
        ensureLadderSegmentWorld(new BlockPos(wx(ladderX), y, wz(ladderZ)), facing);
    }

    private void ensureLadderTubeClearanceLocal(int ladderX, int y, int ladderZ, Direction facing) {
        ensureLadderTubeClearanceWorld(new BlockPos(wx(ladderX), y, wz(ladderZ)), facing);
    }

    private void ensureLadderSegmentWorld(BlockPos ladderPos, Direction facing) {
        BlockPos backingPos = ladderPos.relative(facing.getOpposite());
        BlockState backingState = level.getBlockState(backingPos);
        if (!backingState.blocksMotion()) {
            setBlockWorldForced(
                    backingPos.getX(),
                    backingPos.getY(),
                    backingPos.getZ(),
                    pickPermanentLadderBacking(
                            backingPos.getX() - origin.getX(),
                            backingPos.getY(),
                            backingPos.getZ() - origin.getZ()));
        }

        BlockState ladderState = Blocks.LADDER.defaultBlockState();
        if (ladderState.hasProperty(LadderBlock.FACING)) {
            ladderState = ladderState.setValue(LadderBlock.FACING, facing);
        }
        setAirWorldForced(ladderPos.getX(), ladderPos.getY(), ladderPos.getZ());
        setBlockWorldForced(ladderPos.getX(), ladderPos.getY(), ladderPos.getZ(), ladderState);
    }

    private void ensureLadderTubeClearanceWorld(BlockPos ladderPos, Direction facing) {
        int x = ladderPos.getX();
        int y = ladderPos.getY();
        int z = ladderPos.getZ();
        BlockPos player = ladderPos.relative(facing);
        BlockPos side = player.relative(facing.getClockWise());

        setAirWorldForced(x, y, z);
        setAirWorldForced(player.getX(), player.getY(), player.getZ());
        setAirWorldForced(player.getX(), player.getY() + 1, player.getZ());
        setAirWorldForced(side.getX(), side.getY(), side.getZ());
        setAirWorldForced(side.getX(), side.getY() + 1, side.getZ());
    }

    private void validateLanternSupports() {
        int radius = settings.outerRadius() + 10;
        int minX = origin.getX() - radius;
        int maxX = origin.getX() + radius;
        int minZ = origin.getZ() - radius;
        int maxZ = origin.getZ() + radius;
        int minY = Math.max(level.getMinBuildHeight() + 1, hubCenterY - 4);
        int maxY = Math.min(level.getMaxBuildHeight() - 3, originY + settings.height() + 24);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(Blocks.LANTERN)) {
                        continue;
                    }
                    boolean hanging = state.hasProperty(LanternBlock.HANGING) && state.getValue(LanternBlock.HANGING);
                    if (hanging) {
                        stabilizeHangingLantern(pos);
                    } else {
                        stabilizeStandingLantern(pos);
                    }
                }
            }
        }

        clearDroppedLanternItems(radius, minY, maxY);
    }

    private void stabilizeHangingLantern(BlockPos lanternPos) {
        int x = lanternPos.getX();
        int y = lanternPos.getY();
        int z = lanternPos.getZ();

        int chainStartY = y + 1;
        int maxProbeY = Math.min(level.getMaxBuildHeight() - 2, chainStartY + 8);
        int anchorY = -1;
        for (int probeY = chainStartY + 1; probeY <= maxProbeY; probeY++) {
            BlockPos probe = new BlockPos(x, probeY, z);
            BlockState probeState = level.getBlockState(probe);
            if (probeState.blocksMotion()) {
                anchorY = probeY;
                break;
            }
        }
        if (anchorY < 0) {
            anchorY = Math.min(chainStartY + 3, level.getMaxBuildHeight() - 2);
        }

        buildLanternAnchorPlateWorld(x, anchorY, z);
        for (int chainY = chainStartY; chainY < anchorY; chainY++) {
            setBlockWorld(x, chainY, z, Blocks.CHAIN.defaultBlockState());
        }
        setHangingLanternWorld(x, y, z);
    }

    private void stabilizeStandingLantern(BlockPos lanternPos) {
        BlockPos below = lanternPos.below();
        if (!level.getBlockState(below).blocksMotion()) {
            setBlockWorld(below.getX(), below.getY(), below.getZ(), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        setStandingLanternWorld(lanternPos.getX(), lanternPos.getY(), lanternPos.getZ());
    }

    private void clearDroppedLanternItems(int radius, int minY, int maxY) {
        AABB area = new AABB(
                origin.getX() - radius,
                minY,
                origin.getZ() - radius,
                origin.getX() + radius + 1,
                maxY + 1,
                origin.getZ() + radius + 1);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area);
        for (ItemEntity drop : drops) {
            drop.discard();
        }
    }

    private void placeLantern(int lx, int y, int lz) {
        int x = wx(lx);
        int z = wz(lz);
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.getBlockState(pos).isAir()) {
            return;
        }
        if (level.getBlockState(pos.below()).blocksMotion()) {
            setStandingLanternWorld(x, y, z);
            return;
        }
        if (level.getBlockState(pos.above()).blocksMotion()) {
            setBlockWorld(x, y + 1, z, Blocks.CHAIN.defaultBlockState());
            setHangingLanternWorld(x, y, z);
            return;
        }
        buildLanternAnchorPlateWorld(x, Math.min(level.getMaxBuildHeight() - 2, y + 3), z);
        setBlockWorld(x, y + 1, z, Blocks.CHAIN.defaultBlockState());
        setHangingLanternWorld(x, y, z);
    }

    private void placeDoor(int lx, int y, int lz, Direction facing) {
        BlockState lower = wood.door.defaultBlockState();
        if (lower.hasProperty(DoorBlock.FACING)) {
            lower = lower.setValue(DoorBlock.FACING, facing);
        }
        if (lower.hasProperty(DoorBlock.HINGE)) {
            lower = lower.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        }
        if (lower.hasProperty(DoorBlock.HALF)) {
            lower = lower.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        }
        BlockState upper = wood.door.defaultBlockState();
        if (upper.hasProperty(DoorBlock.FACING)) {
            upper = upper.setValue(DoorBlock.FACING, facing);
        }
        if (upper.hasProperty(DoorBlock.HINGE)) {
            upper = upper.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        }
        if (upper.hasProperty(DoorBlock.HALF)) {
            upper = upper.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        }
        setBlockLocal(lx, y, lz, lower);
        setBlockLocal(lx, y + 1, lz, upper);
    }

    private Direction directionToCenter(int lx, int lz) {
        if (Math.abs(lx) > Math.abs(lz)) {
            return lx > 0 ? Direction.WEST : Direction.EAST;
        }
        return lz > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    private LocalXZ stallOffset(int cx, int cz, int rx, int rz, Direction face) {
        Direction right = face.getClockWise();
        int dx = (right.getStepX() * rx) + (-face.getStepX() * rz);
        int dz = (right.getStepZ() * rx) + (-face.getStepZ() * rz);
        return new LocalXZ(cx + dx, cz + dz);
    }

    private BlockState bottomSlab(Block block) {
        BlockState slab = block.defaultBlockState();
        if (slab.hasProperty(SlabBlock.TYPE)) {
            slab = slab.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        }
        return slab;
    }

    private BlockState pickExterior(int x, int y, int z) {
        return exteriorPalette.pick(worldSeed ^ 701L, x, y, z);
    }

    private BlockState pickInterior(int x, int y, int z) {
        return interiorPalette.pick(worldSeed ^ 719L, x, y, z);
    }

    private BlockState pickFloor(int x, int y, int z) {
        return floorPalette.pick(worldSeed ^ 733L, x, y, z);
    }

    private void setLavaLocal(int lx, int y, int lz) {
        int x = wx(lx);
        int z = wz(lz);
        setBlockWorld(x, y, z, Blocks.LAVA.defaultBlockState());
        intentionalLava.add(BlockPos.asLong(x, y, z));
    }

    private void setBlockLocal(int lx, int y, int lz, BlockState state) {
        setBlockWorld(wx(lx), y, wz(lz), state);
    }

    private void setBlockLocalForced(int lx, int y, int lz, BlockState state) {
        setBlockWorldForced(wx(lx), y, wz(lz), state);
    }

    private void setAirLocal(int lx, int y, int lz) {
        setAirWorld(wx(lx), y, wz(lz));
    }

    private void setAirLocalForced(int lx, int y, int lz) {
        setAirWorldForced(wx(lx), y, wz(lz));
    }

    private void setAirWorld(int x, int y, int z) {
        setBlockWorld(x, y, z, Blocks.AIR.defaultBlockState());
    }

    private void setAirWorldForced(int x, int y, int z) {
        setBlockWorldForced(x, y, z, Blocks.AIR.defaultBlockState());
    }

    private void setBlockWorld(int x, int y, int z, BlockState state) {
        setBlockWorldInternal(x, y, z, state, false);
    }

    private void setBlockWorldForced(int x, int y, int z, BlockState state) {
        setBlockWorldInternal(x, y, z, state, true);
    }

    private void setBlockWorldInternal(int x, int y, int z, BlockState state, boolean force) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState current = level.getBlockState(pos);
        if (current.equals(state) || current.is(Blocks.BEDROCK)) {
            return;
        }
        if (!force && protectedBlocks.contains(BlockPos.asLong(x, y, z))) {
            return;
        }
        level.setBlock(pos, state, 2);
    }

    private int wx(int lx) {
        return origin.getX() + lx;
    }

    private int wz(int lz) {
        return origin.getZ() + lz;
    }

    private double noise2D(double x, double z, long salt) {
        int x0 = Mth.floor(x);
        int z0 = Mth.floor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        double tx = x - x0;
        double tz = z - z0;
        double v00 = randomSigned(mix(worldSeed ^ salt, x0, 0, z0));
        double v10 = randomSigned(mix(worldSeed ^ salt, x1, 0, z0));
        double v01 = randomSigned(mix(worldSeed ^ salt, x0, 0, z1));
        double v11 = randomSigned(mix(worldSeed ^ salt, x1, 0, z1));
        double u = fade(tx);
        double v = fade(tz);
        return lerp(v, lerp(u, v00, v10), lerp(u, v01, v11));
    }

    private double noise3D(double x, double y, double z, long salt) {
        int x0 = Mth.floor(x);
        int y0 = Mth.floor(y);
        int z0 = Mth.floor(z);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        int z1 = z0 + 1;
        double tx = x - x0;
        double ty = y - y0;
        double tz = z - z0;
        double v000 = randomSigned(mix(worldSeed ^ salt, x0, y0, z0));
        double v100 = randomSigned(mix(worldSeed ^ salt, x1, y0, z0));
        double v010 = randomSigned(mix(worldSeed ^ salt, x0, y1, z0));
        double v110 = randomSigned(mix(worldSeed ^ salt, x1, y1, z0));
        double v001 = randomSigned(mix(worldSeed ^ salt, x0, y0, z1));
        double v101 = randomSigned(mix(worldSeed ^ salt, x1, y0, z1));
        double v011 = randomSigned(mix(worldSeed ^ salt, x0, y1, z1));
        double v111 = randomSigned(mix(worldSeed ^ salt, x1, y1, z1));
        double u = fade(tx);
        double v = fade(ty);
        double w = fade(tz);
        double x00 = lerp(u, v000, v100);
        double x10 = lerp(u, v010, v110);
        double x01 = lerp(u, v001, v101);
        double x11 = lerp(u, v011, v111);
        return lerp(w, lerp(v, x00, x10), lerp(v, x01, x11));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double random01(long h) {
        return ((h >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    private static double randomSigned(long h) {
        return (random01(h) * 2.0D) - 1.0D;
    }

    private static long mix(long seed, int x, int y, int z) {
        long h = seed;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) y * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) z * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static final class WeightedPalette {
        private final List<PaletteEntry> entries;
        private final int total;

        private WeightedPalette(List<PaletteEntry> entries) {
            this.entries = entries;
            this.total = entries.stream().mapToInt(e -> e.weight).sum();
        }

        private BlockState pick(long seed, int x, int y, int z) {
            int roll = (int) Math.floorMod(mix(seed, x, y, z), total);
            int acc = 0;
            for (PaletteEntry e : entries) {
                acc += e.weight;
                if (roll < acc) {
                    return e.block.defaultBlockState();
                }
            }
            return entries.get(entries.size() - 1).block.defaultBlockState();
        }
    }

    private record PaletteEntry(Block block, int weight) {
    }

    private record WoodPalette(Block strippedLog, Block planks, Block slab, Block stairs, Block door, Block trapdoor) {
    }

    private record Pocket(String name, int localX, int localY, int localZ, int sizeX, int sizeY, int sizeZ) {
    }

    private record BuildingSpec(String name, int x, int z, int width, int depth) {
    }

    private record HubExit(Direction direction, int width, int height, int endStep, int endX, int endZ, boolean smallSouth) {
    }

    private record ConstellationLamp(int lx, int lz, int lanternY, int chainLen) {
    }

    private record LadderShaft(int ladderX, int ladderZ, int yMin, int yMax, Direction facing) {
    }

    private record PerchAnchor(int x, int y, int z) {
    }

    private record ServiceStairNode(int xMin, int xMax, int y, int z, boolean landing) {
    }

    private record CoreLadderAccess(int ladderX, int ladderZ, int yMin, int yMax, Direction facing, List<Integer> landingYs) {
    }

    private record LocalXYZ(int x, int y, int z) {
    }

    private record LocalXZ(int x, int z) {
    }
}
