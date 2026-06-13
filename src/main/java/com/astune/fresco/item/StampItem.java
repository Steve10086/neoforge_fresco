package com.astune.fresco.item;

import com.astune.painter.api.BlendMode;
import com.astune.painter.api.CanvasData;
import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.IPaintProvider;
import com.astune.painter.api.PaintPattern;
import com.astune.painter.api.PaintProviders;
import com.astune.painter.api.PixelMatrix;
import com.astune.painter.api.PixelProvider;
import com.astune.painter.block.CanvasBlockEntity;
import com.astune.painter.network.ItemSyncPacket;
import com.astune.painter.registry.ModAttachments;
import com.astune.fresco.Fresco;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class StampItem extends Item implements IPaintProvider, OnRightClickHandler {

    public StampItem() {
        super(new Item.Properties().stacksTo(1));
        PaintProviders.register(this, this);
    }

    static boolean sActive = true;

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            boolean bg = !stack.getOrDefault(Fresco.STAMP_BACKGROUND.get(), false);
            stack.set(Fresco.STAMP_BACKGROUND.get(), bg);
            if (level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("item.fresco.stamp.mode",
                                Component.translatable(bg
                                        ? "item.fresco.stamp.mode.background"
                                        : "item.fresco.stamp.mode.default")),
                        true);
            }
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean shouldPaint(Player player){
        return player.level().isClientSide && !player.isShiftKeyDown() && Minecraft.getInstance().options.keyUse.isDown();
    }

    // ── Shift+右键方块：提取 ──────────────────────────────────────

    @Override
    public void onRightClickedBlock(PlayerInteractEvent.RightClickBlock ctx){
        Player player = ctx.getEntity();
        if (!player.isShiftKeyDown()) return;

        Level level = ctx.getLevel();
        BlockPos pos = ctx.getPos();
        ItemStack stack = ctx.getItemStack();
        int slot = player.getMainHandItem().getItem().equals(this)
                ? player.getInventory().selected : 40;
        boolean bgMode = stack.getOrDefault(Fresco.STAMP_BACKGROUND.get(), false);
        Direction face = ctx.getHitVec().getDirection();

        if (level.isClientSide() && bgMode) {
            captureBackgroundMode(level, pos, face, stack, slot);
        } else if (!level.isClientSide() && !bgMode) {
            captureDefaultMode(level, pos, ctx.getHitVec().getLocation(), stack, player);
        }
    }


    // ── 默认模式：服务端复制 CanvasFace ───────────────────────────

    private static void captureDefaultMode(Level level, BlockPos pos, Vec3 hitLoc,
                                            ItemStack stack, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;
        CanvasData data = be.getExistingData(ModAttachments.CANVAS_DATA.get()).orElse(null);
        if (data == null) return;
        var faces = data.getFaceAtHit(pos, hitLoc);
        if (faces.isEmpty()) return;
        CanvasFace src = faces.get(0);
        if (src.pixels().isEmpty()) return;
        stack.set(Fresco.STAMP_FACE.get(), clonePixelsOnly(src));
        player.displayClientMessage(Component.translatable("item.fresco.stamp.stored"), true);
    }

    // ── 背景模式：客户端 → 方块纹理 + 画布 → 单矩阵 ──────────────

    @OnlyIn(Dist.CLIENT)
    private static void captureBackgroundMode(Level level, BlockPos pos, Direction face,
                                               ItemStack stack, int slot) {
        BlockEntity be = level.getBlockEntity(pos);

        // 1. 解析实际纹理来源：画布方块用 mimickedState，普通方块用自身 state
        BlockState textureSource;
        if (be instanceof CanvasBlockEntity cbe) {
            textureSource = cbe.getMimickedState();
        } else {
            textureSource = level.getBlockState(pos);
        }

        // 2. 从模型提取纹理 → PixelMatrix
        PixelMatrix matrix = captureBlockFaceTexture(textureSource, face);
        if (matrix == null) return;

        // 3. 叠加画布像素（如果该面有 CanvasData）
        if (be != null) {
            CanvasData data = be.getExistingData(ModAttachments.CANVAS_DATA.get()).orElse(null);
            if (data != null) {
                Vec3 probe = pos.getCenter().add(
                        face.getStepX() * 0.49, face.getStepY() * 0.49, face.getStepZ() * 0.49);
                var faces = data.getFaceAtHit(pos, probe);
                if (!faces.isEmpty()) {
                    CanvasFace overlay = faces.get(0);
                    if (!overlay.pixels().isEmpty()) {
                        mergeAligned(matrix, overlay);
                    }
                }
            }
        }

        // 4. 包装为单 CanvasFace（全块面）
        stack.set(Fresco.STAMP_FACE.get(), new CanvasFace(face, pos.getCenter(), matrix));
        PacketDistributor.sendToServer(new ItemSyncPacket(slot, stack));
    }

    /** 将 overlay 的像素按角坐标对齐合并到 bg 矩阵中 */
    private static void mergeAligned(PixelMatrix bg, CanvasFace overlay) {
        // 构建块面坐标系 (u,v ∈ [0,1])：corner0=原点, corner1=u轴, corner3=v轴
        Vec3 o = overlay.corner0();
        Vec3 uAxis = overlay.corner1().subtract(o);
        Vec3 vAxis = overlay.corner3().subtract(o);
        double uLen = uAxis.length();
        double vLen = vAxis.length();
        if (uLen <= 0 || vLen <= 0) return;

        // overlay 在块面上的归一化坐标范围
        // ——简化：默认块面是 1×1，overlay 也是 1×1 时 uLen=vLen=1
        double uMin = 0, vMin = 0, uMax = 1, vMax = 1;
        // 角位置相对块面中心的偏移（块面始终 1×1）
        // 这里不做复杂的 UV 反算，假设 overlay 角是标准全块面
        // 实际对齐依赖 overlay.primaryFace() 和 face 的匹配

        PixelMatrix overlayPx = overlay.pixels();
        int ow = overlayPx.getWidth();
        int oh = overlayPx.getHeight();
        int bw = bg.getWidth();
        int bh = bg.getHeight();

        // overlay 角在块面空间的位置 → bg 像素范围
        int x0 = (int)(uMin * bw);
        int y0 = (int)(vMin * bh);
        int x1 = (int)(uMax * bw);
        int y1 = (int)(vMax * bh);

        for (int py = y0; py < y1 && py < bh; py++) {
            for (int px = x0; px < x1 && px < bw; px++) {
                // bg 像素 → 反算 overlay UV
                double u = (double)(px - x0) / (x1 - x0);
                double v = (double)(py - y0) / (y1 - y0);
                int ox = (int)(u * ow);
                int oy = (int)(v * oh);
                if (ox < 0 || ox >= ow || oy < 0 || oy >= oh) continue;
                int c = overlayPx.getPixel(ox, oy);
                if (((c >> 24) & 0xFF) > 0) {
                    bg.setPixel(px, py, c);
                }
            }
        }
    }

    /** 从方块模型纹理中提取 PixelMatrix */
    @OnlyIn(Dist.CLIENT)
    private static PixelMatrix captureBlockFaceTexture(BlockState state, Direction face) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getBlockModelShaper().getBlockModel(state);
        RandomSource rand = RandomSource.create();
        List<net.minecraft.client.renderer.block.model.BakedQuad> quads = model.getQuads(state, face, rand);
        if (quads.isEmpty()) quads = model.getQuads(state, null, rand);
        if (quads.isEmpty()) return null;

        TextureAtlasSprite sprite = quads.getFirst().getSprite();
        NativeImage img = sprite.contents().getOriginalImage();
        if (img == null) return null;

        int w = sprite.contents().width();
        int h = sprite.contents().height();
        PixelMatrix matrix = new PixelMatrix(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = img.getPixelRGBA(x, y);
                int a = (abgr >> 24) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int r = abgr & 0xFF;
                matrix.setPixel(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return matrix;
    }

    /** CanvasFace 仅拷贝像素（保留原有角坐标和方向） */
    private static CanvasFace clonePixelsOnly(CanvasFace src) {
        PixelMatrix sp = src.pixels();
        PixelMatrix dp = new PixelMatrix(sp.getWidth(), sp.getHeight());
        int[] ps = sp.getPixels();
        for (int i = 0; i < ps.length; i++) {
            dp.setPixel(i % sp.getWidth(), i / sp.getWidth(), ps[i]);
        }
        return new CanvasFace(src.primaryFace(), src.corner0(), src.corner1(),
                src.corner2(), src.corner3(), dp, src.getEffectLayers());
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
        CanvasFace stampFace = brush.getOrDefault(Fresco.STAMP_FACE.get(), null);
        if (stampFace == null) return null;

        PixelMatrix matrix = stampFace.pixels();
        if (matrix.isEmpty()) return null;

        double faceW = stampFace.corner0().distanceTo(stampFace.corner1());
        double faceH = stampFace.corner0().distanceTo(stampFace.corner3());
        if (faceW <= 0 || faceH <= 0) return null;

        final int pw = matrix.getWidth();
        final int ph = matrix.getHeight();
        final int[] pixels = matrix.getPixels().clone();

        return new PaintPattern(faceW, faceH, new PixelProvider() {
            @Override
            public BlendMode getBlendMode() { return BlendMode.OVERWRITE; }

            @Override
            public Integer getPixel(double dx, double dy) {
                int x = (int)(dx / faceW * pw);
                int y = (int)(dy / faceH * ph);
                if (x < 0 || x >= pw || y < 0 || y >= ph) return null;
                int c = pixels[y * pw + x];
                return ((c >> 24) & 0xFF) == 0 ? null : c;
            }
        });
    }

    private static Vec3[] tangents(Direction face) {
        Vec3 u = switch (face) {
            case NORTH, SOUTH, UP, DOWN -> new Vec3(1, 0, 0);
            case EAST, WEST -> new Vec3(0, 0, 1);
        };
        Vec3 v = switch (face) {
            case NORTH, SOUTH, EAST, WEST -> new Vec3(0, -1, 0);
            case UP, DOWN -> new Vec3(0, 0, 1);
        };
        return new Vec3[] { u, v };
    }

    @Override
    public Vec3[] transformPatternAxes(Player player, Vec3 hitPoint, Direction face, double w, double h) {
        Vec3[] axes = tangents(face);
        Vec3 origin = hitPoint.subtract(axes[0].scale(w / 2)).subtract(axes[1].scale(h / 2));
        return new Vec3[] { origin, axes[0], axes[1] };
    }

    @Override
    public Double getStep() { return 100.0; }

    // ── Tooltip ──────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                java.util.List<Component> tooltip, TooltipFlag flag) {
        boolean bg = stack.getOrDefault(Fresco.STAMP_BACKGROUND.get(), false);
        tooltip.add(Component.translatable("item.fresco.stamp.mode",
                Component.translatable(bg
                        ? "item.fresco.stamp.mode.background"
                        : "item.fresco.stamp.mode.default")));

        CanvasFace face = stack.getOrDefault(Fresco.STAMP_FACE.get(), null);
        if (face != null && !face.pixels().isEmpty()) {
            PixelMatrix m = face.pixels();
            tooltip.add(Component.translatable("item.fresco.stamp.has_data",
                    m.getWidth(), m.getHeight()));
        } else {
            tooltip.add(Component.translatable("item.fresco.stamp.empty"));
        }
        tooltip.add(Component.translatable("item.fresco.stamp.help"));
    }
}
