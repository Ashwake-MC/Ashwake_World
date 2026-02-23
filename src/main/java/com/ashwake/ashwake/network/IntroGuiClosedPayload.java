package com.ashwake.ashwake.network;

import com.ashwake.ashwake.AshwakeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IntroGuiClosedPayload(
        int introVersion,
        boolean dontShowAgain,
        boolean disableTutorialPopups) implements CustomPacketPayload {
    public static final Type<IntroGuiClosedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AshwakeMod.MODID, "intro_gui_closed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IntroGuiClosedPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public IntroGuiClosedPayload decode(RegistryFriendlyByteBuf buf) {
                    return new IntroGuiClosedPayload(
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readBoolean());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, IntroGuiClosedPayload payload) {
                    buf.writeVarInt(payload.introVersion());
                    buf.writeBoolean(payload.dontShowAgain());
                    buf.writeBoolean(payload.disableTutorialPopups());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
