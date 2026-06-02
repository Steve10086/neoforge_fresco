package com.astune.pigmentum;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetPaletteColorPayload(int color) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetPaletteColorPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "set_palette_color"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPaletteColorPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    SetPaletteColorPayload::color,
                    SetPaletteColorPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
