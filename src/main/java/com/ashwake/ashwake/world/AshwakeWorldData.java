package com.ashwake.ashwake.world;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class AshwakeWorldData extends SavedData {
    private static final String DATA_NAME = "ashwake_world_data";

    private boolean placed;
    private BlockPos origin;
    private BlockPos spawnPos;
    private int radiusOuter;
    private int height;
    private long seedUsed;
    private String biomeChosen;
    private BlockPos worldCorePos;
    private UUID worldCoreUuid;
    private BlockPos runeDiscPos;
    private UUID runeDiscUuid;

    public AshwakeWorldData() {
        this.placed = false;
        this.origin = BlockPos.ZERO;
        this.spawnPos = BlockPos.ZERO;
        this.radiusOuter = 0;
        this.height = 0;
        this.seedUsed = 0L;
        this.biomeChosen = "";
        this.worldCorePos = BlockPos.ZERO;
        this.worldCoreUuid = null;
        this.runeDiscPos = BlockPos.ZERO;
        this.runeDiscUuid = null;
    }

    public static AshwakeWorldData get(ServerLevel level) {
        SavedData.Factory<AshwakeWorldData> factory = new SavedData.Factory<>(
                AshwakeWorldData::new,
                AshwakeWorldData::load);
        return level.getDataStorage().computeIfAbsent(factory, DATA_NAME);
    }

    private static AshwakeWorldData load(CompoundTag tag, HolderLookup.Provider provider) {
        AshwakeWorldData data = new AshwakeWorldData();
        data.placed = tag.getBoolean("placed");
        data.origin = readPos(tag, "origin", BlockPos.ZERO);
        data.spawnPos = readPos(tag, "spawnPos", BlockPos.ZERO);
        data.radiusOuter = tag.getInt("radiusOuter");
        data.height = tag.getInt("height");
        data.seedUsed = tag.getLong("seedUsed");
        data.biomeChosen = tag.getString("biomeChosen");
        data.worldCorePos = readPos(tag, "worldCorePos", BlockPos.ZERO);
        data.worldCoreUuid = tag.hasUUID("worldCoreUuid") ? tag.getUUID("worldCoreUuid") : null;
        data.runeDiscPos = readPos(tag, "runeDiscPos", BlockPos.ZERO);
        data.runeDiscUuid = tag.hasUUID("runeDiscUuid") ? tag.getUUID("runeDiscUuid") : null;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("placed", this.placed);
        writePos(tag, "origin", this.origin);
        writePos(tag, "spawnPos", this.spawnPos);
        tag.putInt("radiusOuter", this.radiusOuter);
        tag.putInt("height", this.height);
        tag.putLong("seedUsed", this.seedUsed);
        tag.putString("biomeChosen", this.biomeChosen);
        writePos(tag, "worldCorePos", this.worldCorePos);
        if (this.worldCoreUuid != null) {
            tag.putUUID("worldCoreUuid", this.worldCoreUuid);
        }
        writePos(tag, "runeDiscPos", this.runeDiscPos);
        if (this.runeDiscUuid != null) {
            tag.putUUID("runeDiscUuid", this.runeDiscUuid);
        }
        return tag;
    }

    public boolean isPlaced() {
        return placed;
    }

    public BlockPos getOrigin() {
        return origin;
    }

    public BlockPos getSpawnPos() {
        return spawnPos;
    }

    public int getRadiusOuter() {
        return radiusOuter;
    }

    public int getHeight() {
        return height;
    }

    public long getSeedUsed() {
        return seedUsed;
    }

    public String getBiomeChosen() {
        return biomeChosen;
    }

    public BlockPos getWorldCorePos() {
        return worldCorePos;
    }

    public UUID getWorldCoreUuid() {
        return worldCoreUuid;
    }

    public BlockPos getRuneDiscPos() {
        return runeDiscPos;
    }

    public UUID getRuneDiscUuid() {
        return runeDiscUuid;
    }

    public boolean hasWorldCore() {
        return !this.worldCorePos.equals(BlockPos.ZERO) && this.worldCoreUuid != null;
    }

    public boolean hasRuneDisc() {
        return !this.runeDiscPos.equals(BlockPos.ZERO) && this.runeDiscUuid != null;
    }

    public void markPlaced(BlockPos origin, BlockPos spawnPos, int radiusOuter, int height, long seedUsed, String biomeChosen) {
        this.placed = true;
        this.origin = origin.immutable();
        this.spawnPos = spawnPos.immutable();
        this.radiusOuter = radiusOuter;
        this.height = height;
        this.seedUsed = seedUsed;
        this.biomeChosen = biomeChosen;
        this.worldCorePos = BlockPos.ZERO;
        this.worldCoreUuid = null;
        this.runeDiscPos = BlockPos.ZERO;
        this.runeDiscUuid = null;
        this.setDirty();
    }

    public void markWorldCore(BlockPos worldCorePos, UUID worldCoreUuid) {
        this.worldCorePos = worldCorePos.immutable();
        this.worldCoreUuid = worldCoreUuid;
        this.setDirty();
    }

    public void clearWorldCore() {
        this.worldCorePos = BlockPos.ZERO;
        this.worldCoreUuid = null;
        this.setDirty();
    }

    public void markRuneDisc(BlockPos runeDiscPos, UUID runeDiscUuid) {
        this.runeDiscPos = runeDiscPos.immutable();
        this.runeDiscUuid = runeDiscUuid;
        this.setDirty();
    }

    public void clearRuneDisc() {
        this.runeDiscPos = BlockPos.ZERO;
        this.runeDiscUuid = null;
        this.setDirty();
    }

    private static void writePos(CompoundTag root, String key, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        root.put(key, tag);
    }

    private static BlockPos readPos(CompoundTag root, String key, BlockPos fallback) {
        if (!root.contains(key, Tag.TAG_COMPOUND)) {
            return fallback;
        }
        CompoundTag tag = root.getCompound(key);
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }
}
