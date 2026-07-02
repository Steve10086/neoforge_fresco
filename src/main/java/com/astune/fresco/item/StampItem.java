package com.astune.fresco.item;

import com.astune.fresco.client.BrushParticleSpawner;
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
import net.minecraft.world.phys.BlockHitResult;
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

    private static Vec3 lastHitLoc = null;

    @Override
    public boolean shouldPaint(Player player, BlockHitResult result){
        if (!(player.level().isClientSide && !player.isShiftKeyDown() && Minecraft.getInstance().options.keyUse.isDown())) return false;
        if (lastHitLoc == null) {
            lastHitLoc = result.getLocation();
            spawnStampParticles(player, result);
            return true;
        }
        if (lastHitLoc.distanceTo(result.getLocation()) > getStep()){
            lastHitLoc = result.getLocation();
            spawnStampParticles(player, result);
            return true;
        }

        return false;
    }

    private void spawnStampParticles(Player player, BlockHitResult result) {
        CanvasFace stampFace = player.getMainHandItem().getOrDefault(Fresco.STAMP_FACE.get(), null);
        if (stampFace == null) return;
        PixelMatrix matrix = stampFace.pixels();
        if (matrix.isEmpty()) return;

        double faceW = stampFace.corner0().distanceTo(stampFace.corner1());
        double faceH = stampFace.corner0().distanceTo(stampFace.corner3());
        if (faceW <= 0 || faceH <= 0) return;

        int pw = matrix.getWidth(), ph = matrix.getHeight();
        Direction face = result.getDirection();
        Vec3[] axes = tangents(face);
        Vec3 origin = result.getLocation().subtract(axes[0].scale(faceW / 2)).subtract(axes[1].scale(faceH / 2));
        int[] pixels = matrix.getPixels();

        for (int i = 0; i < 10; i++) {
            int px = player.level().random.nextInt(pw);
            int py = player.level().random.nextInt(ph);
            int color = pixels[py * pw + px];
            if (((color >> 24) & 0xFF) == 0) continue;
            double dx = (px + 0.5) / pw * faceW;
            double dy = (py + 0.5) / ph * faceH;
            Vec3 pos = origin.add(axes[0].scale(dx)).add(axes[1].scale(dy));
            BrushParticleSpawner.spawn(player.level(), pos, color);
        }
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
        stack.set(Fresco.STAMP_FACE.get(), clonePixelsOnly(src, new PixelMatrix(src.pixels().getWidth(), src.pixels().getHeight())));
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
                    stack.set(Fresco.STAMP_FACE.get(), clonePixelsOnly(overlay, matrix));
                    PacketDistributor.sendToServer(new ItemSyncPacket(slot, stack));
                    return;
                }
            }
        }

        // 4. 包装为单 CanvasFace（全块面）
        stack.set(Fresco.STAMP_FACE.get(), new CanvasFace(face, pos.getCenter(), matrix));
        PacketDistributor.sendToServer(new ItemSyncPacket(slot, stack));
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

    /** CanvasFace 仅拷贝像素（保留原有角坐标和方向），根据来源面方向做镜像使存储像素与 tangents 坐标系对齐 */
    private static CanvasFace clonePixelsOnly(CanvasFace src, PixelMatrix dp) {
        PixelMatrix sp = src.pixels();
        int pw = sp.getWidth(), ph = sp.getHeight();
        PixelMatrix rp = new PixelMatrix(pw, ph);
        int[] ps = sp.getPixels();

        Direction face = src.primaryFace();
        boolean flipX = needFlipX(face);
        boolean flipY = needFlipY(face);

        for (int y = 0; y < ph; y++) {
            for (int x = 0; x < pw; x++) {
                int sx = flipX ? pw - 1 - x : x;
                int sy = flipY ? ph - 1 - y : y;
                if (sx > 0 && sx < dp.getWidth() && sy > 0 && sy < dp.getHeight()) {
                    rp.setPixel(sx, sy, dp.getPixel(sx, sy));
                }
                if(ps[sy * pw + sx] != 0){
                    int e = dp.getPixel(x, y);
                    int n = ps[sy * pw + sx];
                    int finalColor = getFinalColor(e, n);
                    rp.setPixel(x, y, finalColor);
                }
            }
        }
        return new CanvasFace(src.primaryFace(), src.corner0(), src.corner1(),
                src.corner2(), src.corner3(), rp, src.getEffectLayers());
    }

    private static int getFinalColor(int e, int n) {
        int eA = e >> 24 & 255;
        int eR = e >> 16 & 255;
        int eG = e >> 8 & 255;
        int eB = e & 255;
        int nA = n >> 24 & 255;
        int nR = n >> 16 & 255;
        int nG = n >> 8 & 255;
        int nB = n & 255;
        float srcA = (float)nA / 255.0F;
        float dstA = (float)eA / 255.0F;
        float outA = srcA + dstA * (1.0F - srcA);

        int outR = (int)(((float)nR * srcA + (float)eR * dstA * (1.0F - srcA)) / outA);
        int outG = (int)(((float)nG * srcA + (float)eG * dstA * (1.0F - srcA)) / outA);
        int outB = (int)(((float)nB * srcA + (float)eB * dstA * (1.0F - srcA)) / outA);
        int outAInt = Math.min(255, (int)(outA * 255.0F));
        return outAInt << 24 | outR << 16 | outG << 8 | outB;
    }

    private static boolean needFlipX(Direction face) {
        return switch (face) {
            case NORTH, WEST -> true;
            default -> false;
        };
    }

    private static boolean needFlipY(Direction face) {
        return switch (face) {
            case SOUTH, NORTH, DOWN, EAST, WEST -> true;
            default -> false;
        };
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
            public BlendMode getBlendMode() { return BlendMode.ADD; }

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
    public Double getStep() { return 0.02; }

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
