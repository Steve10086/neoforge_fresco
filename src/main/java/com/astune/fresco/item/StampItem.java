package com.astune.fresco.item;

import com.astune.fresco.particle.BrushParticleSpawner;
import com.astune.painter.api.BlendMode;
import com.astune.painter.api.CanvasData;
import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.IPaintProvider;
import com.astune.painter.api.PaintPattern;
import com.astune.painter.api.PaintProviders;
import com.astune.painter.api.PixelMatrix;
import com.astune.painter.api.PixelProvider;
import com.astune.painter.registry.ModAttachments;
import com.astune.fresco.Fresco;
import com.astune.fresco.ClientInputState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

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
        if (!(player.level().isClientSide && !player.isShiftKeyDown() && ClientInputState.isUseKeyDown())) return false;
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
        boolean bgMode = stack.getOrDefault(Fresco.STAMP_BACKGROUND.get(), false);

        if (!level.isClientSide() && !bgMode) {
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

    /** CanvasFace 仅拷贝像素（保留原有角坐标和方向），根据来源面方向做镜像使存储像素与 tangents 坐标系对齐 */
    public static CanvasFace clonePixelsOnly(CanvasFace src, PixelMatrix dp) {
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
