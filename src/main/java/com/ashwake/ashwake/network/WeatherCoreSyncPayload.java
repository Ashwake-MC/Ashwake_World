package com.ashwake.ashwake.network;

import com.ashwake.ashwake.AshwakeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WeatherCoreSyncPayload(
        String stateId,
        String phaseId,
        int ticksRemaining,
        int totalTicks,
        String nextStateId,
        boolean sleepLocked,
        float intensity,
        int safeRadiusBlocks,
        BlockPos hubCenter,
        boolean lightningSuppressedInSafeZone,
        long worldTime,
        boolean isRaining,
        boolean isThundering) implements CustomPacketPayload {

    public static final Type<WeatherCoreSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AshwakeMod.MODID, "weather_core_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WeatherCoreSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WeatherCoreSyncPayload decode(RegistryFriendlyByteBuf buf) {
                    String stateId = buf.readUtf(48);
                    String phaseId = buf.readUtf(16);
                    int ticksRemaining = buf.readVarInt();
                    int totalTicks = buf.readVarInt();
                    String nextStateId = buf.readUtf(48);
                    boolean sleepLocked = buf.readBoolean();
                    float intensity = buf.readFloat();
                    int safeRadiusBlocks = buf.readVarInt();
                    BlockPos hubCenter = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
                    boolean lightningSuppressedInSafeZone = buf.readBoolean();
                    long worldTime = buf.readVarLong();
                    boolean isRaining = buf.readBoolean();
                    boolean isThundering = buf.readBoolean();
                    return new WeatherCoreSyncPayload(
                            stateId,
                            phaseId,
                            ticksRemaining,
                            totalTicks,
                            nextStateId,
                            sleepLocked,
                            intensity,
                            safeRadiusBlocks,
                            hubCenter,
                            lightningSuppressedInSafeZone,
                            worldTime,
                            isRaining,
                            isThundering);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, WeatherCoreSyncPayload payload) {
                    buf.writeUtf(payload.stateId(), 48);
                    buf.writeUtf(payload.phaseId(), 16);
                    buf.writeVarInt(payload.ticksRemaining());
                    buf.writeVarInt(payload.totalTicks());
                    buf.writeUtf(payload.nextStateId(), 48);
                    buf.writeBoolean(payload.sleepLocked());
                    buf.writeFloat(payload.intensity());
                    buf.writeVarInt(payload.safeRadiusBlocks());
                    buf.writeInt(payload.hubCenter().getX());
                    buf.writeInt(payload.hubCenter().getY());
                    buf.writeInt(payload.hubCenter().getZ());
                    buf.writeBoolean(payload.lightningSuppressedInSafeZone());
                    buf.writeVarLong(payload.worldTime());
                    buf.writeBoolean(payload.isRaining());
                    buf.writeBoolean(payload.isThundering());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
