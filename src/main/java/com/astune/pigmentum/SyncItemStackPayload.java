package com.astune.pigmentum;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 通用的物品同步包 — 客户端→服务端，携带物品所在槽位和完整 ItemStack。
 * 可用于任何需要将客户端修改后的物品数据同步到服务端的场景。
 */
public record SyncItemStackPayload(int slot, ItemStack stack) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncItemStackPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "sync_item_stack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncItemStackPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    SyncItemStackPayload::slot,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    SyncItemStackPayload::stack,
                    SyncItemStackPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
