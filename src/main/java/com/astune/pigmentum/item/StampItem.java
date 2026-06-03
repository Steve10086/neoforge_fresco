package com.astune.pigmentum.item;

import com.astune.painter.api.BlendMode;
import com.astune.painter.api.CanvasData;
import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.IPaintProvider;
import com.astune.painter.api.PaintPattern;
import com.astune.painter.api.PaintProviders;
import com.astune.painter.api.PixelMatrix;
import com.astune.painter.api.PixelProvider;
import com.astune.painter.network.ItemSyncPacket;
import com.astune.painter.registry.ModAttachments;
import com.astune.pigmentum.Pigmentum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 印章 — shift+右键从画布提取像素，作为画笔放置。
 * <ul>
 *   <li>Shift+右键画布 → 复制 CanvasFace 到物品（含 PixelMatrix + 四角坐标）</li>
 *   <li>作为 IPaintProvider → pattern 按原画布尺寸提供像素</li>
 *   <li>无限步长，不插值</li>
 *   <li>与自身合成 → 清除存储数据</li>
 * </ul>
 */
public class StampItem extends Item implements IPaintProvider {

    public StampItem() {
        super(new Item.Properties().stacksTo(1));
        PaintProviders.register(this, this);
    }

    // ── shift+右键：从画布提取整个 CanvasFace ──────────────────────

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        int slot = player.getMainHandItem().getItem().equals(this)
                ? player.getInventory().selected
                : 40;

        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        ItemStack stack = ctx.getItemInHand();

        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                CanvasData data = be.getExistingData(ModAttachments.CANVAS_DATA.get()).orElse(null);
                if (data != null) {
                    Vec3 hitLoc = ctx.getClickLocation();
                    var faces = data.getFaceAtHit(pos, hitLoc);
                    if (!faces.isEmpty()) {
                        CanvasFace src = faces.get(0);
                        if (!src.pixels().isEmpty()) {
                            // 重建 CanvasFace（深拷贝像素，保留四角坐标）
                            PixelMatrix srcPixels = src.pixels();
                            PixelMatrix copyPixels = new PixelMatrix(
                                    srcPixels.getWidth(), srcPixels.getHeight());
                            int[] ps = srcPixels.getPixels();
                            for (int i = 0; i < ps.length; i++) {
                                copyPixels.setPixel(
                                        i % srcPixels.getWidth(),
                                        i / srcPixels.getWidth(), ps[i]);
                            }
                            CanvasFace copy = new CanvasFace(
                                    src.primaryFace(),
                                    src.corner0(), src.corner1(),
                                    src.corner2(), src.corner3(),
                                    copyPixels,
                                    src.getEffectLayers()
                            );
                            stack.set(Pigmentum.STAMP_FACE.get(), copy);
                            player.displayClientMessage(
                                    Component.translatable("item.pigmentum.stamp.stored"), true);
                            PacketDistributor.sendToServer(new ItemSyncPacket(slot, stack));
                        }
                    }
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ── IPaintProvider ──────────────────────────────────────────

    @Override
    public Integer getColor(ItemStack brush, Player player, Level level,
                            BlockPos pos, CanvasFace face, int pixelX, int pixelY) {
        return null;
    }

    @Override
    public PaintPattern getPattern(ItemStack brush, Player player, Level level,
                                   BlockPos pos, Vec3 hit) {
        CanvasFace stampFace = brush.getOrDefault(Pigmentum.STAMP_FACE.get(), null);
        if (stampFace == null) return null;

        PixelMatrix matrix = stampFace.pixels();
        if (matrix.isEmpty()) return null;

        // 用 CanvasFace 的四角坐标计算实际尺寸
        double faceW = stampFace.corner0().distanceTo(stampFace.corner1());
        double faceH = stampFace.corner0().distanceTo(stampFace.corner3());
        if (faceW <= 0 || faceH <= 0) return null;

        final int pw = matrix.getWidth();
        final int ph = matrix.getHeight();
        final int[] pixels = matrix.getPixels().clone();

        return new PaintPattern(faceW, faceH, new PixelProvider() {
            @Override
            public BlendMode getBlendMode() {
                return BlendMode.OVERWRITE;
            }

            @Override
            public Integer getPixel(double dx, double dy) {
                int x = (int) (dx / faceW * pw);
                int y = (int) (dy / faceH * ph);
                if (x < 0 || x >= pw || y < 0 || y >= ph) return null;
                int color = pixels[y * pw + x];
                int a = (color >> 24) & 0xFF;
                return a == 0 ? null : color;
            }
        });
    }

    @Override
    public Double getStep() {
        return 100.0;
    }

    // ── Tooltip ──────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                java.util.List<Component> tooltip, TooltipFlag flag) {
        CanvasFace face = stack.getOrDefault(Pigmentum.STAMP_FACE.get(), null);
        if (face != null && !face.pixels().isEmpty()) {
            PixelMatrix m = face.pixels();
            tooltip.add(Component.translatable("item.pigmentum.stamp.has_data",
                    m.getWidth(), m.getHeight()));
        } else {
            tooltip.add(Component.translatable("item.pigmentum.stamp.empty"));
        }
        tooltip.add(Component.translatable("item.pigmentum.stamp.help"));
    }
}
