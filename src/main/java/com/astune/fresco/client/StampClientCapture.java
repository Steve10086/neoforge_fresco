package com.astune.fresco.client;

import com.astune.fresco.Fresco;
import com.astune.fresco.item.StampItem;
import com.astune.painter.api.CanvasData;
import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.PixelMatrix;
import com.astune.painter.block.CanvasBlockEntity;
import com.astune.painter.network.ItemSyncPacket;
import com.astune.painter.registry.ModAttachments;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Client-only model texture capture for the stamp's background mode. */
public final class StampClientCapture {
    private StampClientCapture() {}

    public static void captureBackgroundMode(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!player.isShiftKeyDown()
                || !stack.getOrDefault(Fresco.STAMP_BACKGROUND.get(), false)) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Direction face = event.getHitVec().getDirection();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        BlockState textureSource = blockEntity instanceof CanvasBlockEntity canvas
                ? canvas.getMimickedState()
                : level.getBlockState(pos);

        PixelMatrix matrix = captureBlockFaceTexture(textureSource, face);
        if (matrix == null) return;

        int slot = event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? player.getInventory().selected
                : 40;
        if (blockEntity != null) {
            CanvasData data = blockEntity.getExistingData(ModAttachments.CANVAS_DATA.get()).orElse(null);
            if (data != null) {
                Vec3 probe = pos.getCenter().add(
                        face.getStepX() * 0.49, face.getStepY() * 0.49, face.getStepZ() * 0.49);
                var faces = data.getFaceAtHit(pos, probe);
                if (!faces.isEmpty()) {
                    CanvasFace overlay = faces.get(0);
                    stack.set(Fresco.STAMP_FACE.get(), StampItem.clonePixelsOnly(overlay, matrix));
                    PacketDistributor.sendToServer(new ItemSyncPacket(slot, stack));
                    return;
                }
            }
        }

        stack.set(Fresco.STAMP_FACE.get(), new CanvasFace(face, pos.getCenter(), matrix));
        PacketDistributor.sendToServer(new ItemSyncPacket(slot, stack));
    }

    private static PixelMatrix captureBlockFaceTexture(BlockState state, Direction face) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getBlockModelShaper().getBlockModel(state);
        RandomSource random = RandomSource.create();
        List<net.minecraft.client.renderer.block.model.BakedQuad> quads = model.getQuads(state, face, random);
        if (quads.isEmpty()) quads = model.getQuads(state, null, random);
        if (quads.isEmpty()) return null;

        TextureAtlasSprite sprite = quads.getFirst().getSprite();
        NativeImage image = sprite.contents().getOriginalImage();
        if (image == null) return null;

        int width = sprite.contents().width();
        int height = sprite.contents().height();
        PixelMatrix matrix = new PixelMatrix(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int abgr = image.getPixelRGBA(x, y);
                int a = (abgr >> 24) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int r = abgr & 0xFF;
                matrix.setPixel(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return matrix;
    }
}
