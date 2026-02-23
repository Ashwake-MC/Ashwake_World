package com.ashwake.ashwake.network;

import com.ashwake.ashwake.AshwakeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenIntroGuiPayload(
        int introVersion,
        int openDelayTicks,
        boolean allowDontShowAgain,
        boolean allowLearnMore,
        boolean allowDisableTutorialPopups) implements CustomPacketPayload {
    public static final Type<OpenIntroGuiPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AshwakeMod.MODID, "open_intro_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenIntroGuiPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenIntroGuiPayload decode(RegistryFriendlyByteBuf buf) {
                    return new OpenIntroGuiPayload(
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, OpenIntroGuiPayload payload) {
                    buf.writeVarInt(payload.introVersion());
                    buf.writeVarInt(payload.openDelayTicks());
                    buf.writeBoolean(payload.allowDontShowAgain());
                    buf.writeBoolean(payload.allowLearnMore());
                    buf.writeBoolean(payload.allowDisableTutorialPopups());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
