package com.ashwake.ashwake.network;

import com.ashwake.ashwake.AshwakeMod;
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
        float intensity) implements CustomPacketPayload {

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
                    return new WeatherCoreSyncPayload(
                            stateId,
                            phaseId,
                            ticksRemaining,
                            totalTicks,
                            nextStateId,
                            sleepLocked,
                            intensity);
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
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
