package com.ashwake.ashwake.world;

import com.ashwake.ashwake.config.AshwakeConfig;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

public final class VolcanoPlacementFinder {
    private static final ResourceLocation BOP_VOLCANO = ResourceLocation.fromNamespaceAndPath("biomesoplenty", "volcano");
    private static final ResourceLocation BOP_VOLCANIC_PLAINS = ResourceLocation.fromNamespaceAndPath("biomesoplenty", "volcanic_plains");

    private VolcanoPlacementFinder() {
    }

    public static PlacementResult findPlacement(ServerLevel level, AshwakeConfig.Settings settings) {
        BlockPos center = level.getSharedSpawnPos();
        Candidate best = null;

        int maxRing = Math.max(1, settings.searchRadius() / settings.sampleStep());
        for (int ring = 0; ring <= maxRing; ring++) {
            int radius = ring * settings.sampleStep();
            int points = ring == 0 ? 1 : Math.max(8, (int) Math.ceil((2.0D * Math.PI * radius) / settings.sampleStep()));
            double phase = ((mix(level.getSeed(), ring, 17, 31) & 1L) == 0L) ? 0.0D : (Math.PI / points);

            for (int i = 0; i < points; i++) {
                double angle = phase + ((Math.PI * 2.0D) * i / points);
                int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

                if (y < settings.surfaceMinY()) {
                    continue;
                }

                BlockPos samplePos = new BlockPos(x, y, z);
                Holder<Biome> biomeHolder = level.getBiome(samplePos);
                ResourceLocation biomeId = resolveBiomeId(biomeHolder);
                if (biomeId == null || isRejectedBiome(biomeHolder, biomeId)) {
                    continue;
                }

                double score = scoreCandidate(level, settings, x, z, y, biomeId);
                Candidate candidate = new Candidate(samplePos, biomeId, score);
                if (best == null || candidate.score() > best.score()) {
                    best = candidate;
                }
            }
        }

        if (best == null) {
            int fallbackY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
            BlockPos fallbackPos = new BlockPos(center.getX(), fallbackY, center.getZ());
            ResourceLocation fallbackBiome = resolveBiomeId(level.getBiome(fallbackPos));
            if (fallbackBiome == null) {
                fallbackBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");
            }
            return new PlacementResult(fallbackPos, fallbackBiome, -9999.0D);
        }

        return new PlacementResult(best.pos(), best.biomeId(), best.score());
    }

    private static double scoreCandidate(ServerLevel level, AshwakeConfig.Settings settings, int x, int z, int surfaceY, ResourceLocation biomeId) {
        double score = 0.0D;

        if (Objects.equals(biomeId, BOP_VOLCANO)) {
            score += 100.0D;
        } else if (Objects.equals(biomeId, BOP_VOLCANIC_PLAINS)) {
            score += 70.0D;
        }

        score += surfaceY * 0.2D;

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int slopeRadius = settings.slopeSampleRadius();
        int points = 16;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D) * i / points;
            int sampleX = x + (int) Math.round(Math.cos(angle) * slopeRadius);
            int sampleZ = z + (int) Math.round(Math.sin(angle) * slopeRadius);
            int sampleY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);
            minY = Math.min(minY, sampleY);
            maxY = Math.max(maxY, sampleY);
        }

        int variance = maxY - minY;
        if (variance > settings.slopeVarianceLimit()) {
            score -= 40.0D;
        }

        return score;
    }

    private static boolean isRejectedBiome(Holder<Biome> biomeHolder, ResourceLocation biomeId) {
        if (biomeId.getPath().contains("ocean")) {
            return true;
        }
        return biomeHolder.is(BiomeTags.IS_OCEAN);
    }

    private static ResourceLocation resolveBiomeId(Holder<Biome> biomeHolder) {
        return biomeHolder.unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);
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

    public record PlacementResult(BlockPos origin, ResourceLocation biomeId, double score) {
    }

    private record Candidate(BlockPos pos, ResourceLocation biomeId, double score) {
    }
}
