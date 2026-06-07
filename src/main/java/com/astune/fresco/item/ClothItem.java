package com.astune.fresco.item;

import com.astune.painter.api.*;
import com.astune.painter.network.ItemSyncPacket;
import com.astune.painter.registry.ModAttachments;
import com.astune.painter.registry.ModDataComponents;
import com.astune.fresco.Fresco;
import com.astune.fresco.screen.ClothScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class ClothItem extends Item implements IPaintProvider {

    private static final int FACE_PX = 16;
    private static final int SAT_MAX = 100;

    public ClothItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .component(ModDataComponents.BRUSH_SIZE.get(), 0.12)
                .component(ModDataComponents.OPACITY.get(), 0.8f)
                .component(Fresco.CLOTH_TINT.get(), 0xFFFFFFFF)
                .component(Fresco.CLOTH_SATURATION.get(), 0)
                .component(ModDataComponents.FEATHER_STRENGTH.get(), 1.0f));
        PaintProviders.register(this, this);
    }

    @Override
    public Integer getColor(ItemStack b, Player p, Level l, BlockPos pos, CanvasFace f, int px, int py) { return null; }

    @Override
    public boolean onPaintTick(ItemStack brush, Player player, Level level) {
        return true;
    }

    private static Vec3 lastHitLoc = null;

    @Override
    public boolean shouldPaint(Player player, BlockHitResult result){
        if (!(player.level().isClientSide
                && net.minecraft.client.Minecraft.getInstance().options.keyUse.isDown())) return false;
        if (lastHitLoc == null) {
            lastHitLoc = result.getLocation();
            return true;
        }
        if (lastHitLoc.distanceTo(result.getLocation()) > getStep()){
            lastHitLoc = result.getLocation();
            return true;
        }

        return false;
    }

    private static Vec3[] tangents(Direction face) {
        Vec3 u = switch (face) {
            case NORTH, SOUTH, UP, DOWN -> new Vec3(1, 0, 0);
            case EAST, WEST -> new Vec3(0, 0, 1);
        };
        Vec3 v = switch (face) {
            case NORTH, SOUTH, EAST, WEST -> new Vec3(0, 1, 0);
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
    public PaintPattern getPattern(ItemStack brush, Player player, Level level,
                                   BlockPos pos, Vec3 hit) {
        double size = brush.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.12);
        float opacity = brush.getOrDefault(ModDataComponents.OPACITY.get(), 0.8f);
        float feather = brush.getOrDefault(ModDataComponents.FEATHER_STRENGTH.get(), 1.0f);
        if (size <= 0) return null;

        CanvasFace cur = hitFace(level, pos, hit);
        if (cur == null) return null;

        Direction face = cur.primaryFace();
        Vec3[] axes = tangents(face);
        Vec3 uWorld = axes[0], vWorld = axes[1];

        Vec3 blockC = Vec3.atLowerCornerOf(pos);
        Vec3 rel = hit.subtract(blockC);
        double hitU = rel.dot(uWorld), hitV = rel.dot(vWorld);
        double minU = hitU - size, maxU = hitU + size;
        double minV = hitV - size, maxV = hitV + size;
        double wW = maxU - minU, wH = maxV - minV;

        int pw = Math.max(1, (int) Math.ceil(wW * FACE_PX));
        int ph = Math.max(1, (int) Math.ceil(wH * FACE_PX));
        PixelMatrix mat = new PixelMatrix(pw, ph);

        int fBU = (int) Math.floor(minU / 1.0), lBU = (int) Math.floor(maxU / 1.0);
        int fBV = (int) Math.floor(minV / 1.0), lBV = (int) Math.floor(maxV / 1.0);

        long totalR = 0, totalG = 0, totalB = 0, totalA = 0;
        int pixelCount = 0;

        for (int bu = fBU; bu <= lBU; bu++) {
            for (int bv = fBV; bv <= lBV; bv++) {
                BlockPos bp = (bu == 0 && bv == 0) ? pos
                        : pos.offset((int)(bu * uWorld.x + bv * vWorld.x),
                                     (int)(bu * uWorld.y + bv * vWorld.y),
                                     (int)(bu * uWorld.z + bv * vWorld.z));

                PixelMatrix src;
                if (bu == 0 && bv == 0) {
                    src = cur.pixels();
                } else {
                    CanvasFace nf = peerFace(level, bp, face);
                    src = nf != null ? nf.pixels() : null;
                }
                if (src == null || src.isEmpty()) continue;

                int sw = src.getWidth(), sh = src.getHeight();
                for (int sy = 0; sy < sh; sy++) {
                    double wV = bv + (sy + 0.5) / sh;
                    if (wV < minV || wV > minV + wH) continue;
                    int dy = (int) ((wV - minV) / wH * ph);
                    if (dy < 0 || dy >= ph) continue;
                    for (int sx = 0; sx < sw; sx++) {
                        double wU = bu + (sx + 0.5) / sw;
                        if (wU < minU || wU > minU + wW) continue;
                        int dx = (int) ((wU - minU) / wW * pw);
                        if (dx < 0 || dx >= pw) continue;
                        int c = src.getPixel(sx, sy);
                        if (((c >> 24) & 0xFF) > 0) {
                            mat.setPixel(dx, dy, c);
                            totalA += (c >> 24) & 0xFF;
                            totalR += (c >> 16) & 0xFF;
                            totalG += (c >> 8) & 0xFF;
                            totalB += c & 0xFF;
                            pixelCount++;
                        }
                    }
                }
            }
        }
        int sat = Math.min(brush.getOrDefault(Fresco.CLOTH_SATURATION.get(), 0) + 1, SAT_MAX);
        brush.set(Fresco.CLOTH_SATURATION.get(), sat);

        // ── 色调积累 ──
        int oldTint = brush.getOrDefault(Fresco.CLOTH_TINT.get(), 0xFFFFFFFF);
        int newTint = oldTint;
        double ratio = Math.max((1 - (sat / (double) SAT_MAX)), 0.01);
        if (pixelCount > 0) {
            int avgR = (int) (totalR / pixelCount);
            int avgG = (int) (totalG / pixelCount);
            int avgB = (int) (totalB / pixelCount);
            // 1% 权重混合
            int r = (int) (((oldTint >> 16) & 0xFF) * (1-ratio) + avgR * ratio);
            int g = (int) (((oldTint >> 8) & 0xFF) * (1-ratio) + avgG * ratio);
            int b = (int) ((oldTint & 0xFF) * (1-ratio) + avgB * ratio);
            newTint = 0xFF000000 | (r << 16) | (g << 8) | b;
            brush.set(Fresco.CLOTH_TINT.get(), newTint);
        }

        blur(mat, Math.max(1, (int) (size * 8)));

        // ── 色调注入模糊矩阵 ──
        double tintBoost = (sat / (double) SAT_MAX) * 0.1;
        if (tintBoost > 0 && !mat.isEmpty()) {
            int tr = (newTint >> 16) & 0xFF;
            int tg = (newTint >> 8) & 0xFF;
            int tb = newTint & 0xFF;
            for (int y = 0; y < ph; y++) {
                for (int x = 0; x < pw; x++) {
                    int c = mat.getPixel(x, y);
                    int a = (c >> 24) & 0xFF;
                    if (a == 0) continue;
                    int r = (int)Math.min(255, ((c >> 16) & 0xFF) * (1 - tintBoost) + (tr * tintBoost));
                    int g = (int)Math.min(255, ((c >> 8) & 0xFF) * (1 - tintBoost) + (tg * tintBoost));
                    int b = (int)Math.min(255, (c & 0xFF) * (1 - tintBoost) + (tb * tintBoost));
                    mat.setPixel(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }

        return clothPattern(mat, wW, wH, opacity, feather);
    }

    @Override public Double getStep() { return 0.03; }

    private static CanvasFace hitFace(Level l, BlockPos p, Vec3 hit) {
        BlockEntity be = l.getBlockEntity(p);
        if (be == null) return null;
        CanvasData d = be.getExistingData(ModAttachments.CANVAS_DATA.get()).orElse(null);
        if (d == null) return null;
        var fs = d.getFaceAtHit(p, hit);
        return fs.isEmpty() ? null : fs.getFirst();
    }

    private static CanvasFace peerFace(Level l, BlockPos bp, Direction dir) {
        BlockEntity be = l.getBlockEntity(bp);
        if (be == null) return null;
        CanvasData d = be.getExistingData(ModAttachments.CANVAS_DATA.get()).orElse(null);
        if (d == null) return null;
        for (CanvasFace f : d.faces())
            if (f.primaryFace() == dir) return f;
        return null;
    }

    private static void blur(PixelMatrix m, int r) {
        int w = m.getWidth(), h = m.getHeight();
        int[] s = m.getPixels().clone();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            long ra = 0, ga = 0, ba = 0, aa = 0; int n = 0;
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                int c = s[ny * w + nx]; int ca = (c >>> 24) & 0xFF;
                if (ca > 0) { aa += ca; ra += (c >> 16) & 0xFF; ga += (c >> 8) & 0xFF; ba += c & 0xFF; n++; }
            }
            m.setPixel(x, y, n > 0 ? ((int)(aa/n) << 24) | ((int)(ra/n) << 16) | ((int)(ga/n) << 8) | (int)(ba/n) : 0);
        }
    }

    private static PaintPattern clothPattern(PixelMatrix m, double wW, double wH, float op, float feather) {
        if (m.isEmpty()) return null;
        int pw = m.getWidth(), ph = m.getHeight();
        int[] data = m.getPixels().clone();
        double r = Math.min(wW, wH) / 2, cx = wW / 2, cy = wH / 2;
        double inner = 1.0 - feather; // 0=full gradient, 1=sharp
        return new PaintPattern(wW, wH, new PixelProvider() {
            @Override public BlendMode getBlendMode() { return BlendMode.ADD; }
            @Override
            public Integer getPixel(double dx, double dy) {
                double d = Math.sqrt((dx - cx) * (dx - cx) + (dy - cy) * (dy - cy)) / r;
                if (d > 1) return null;
                double f;
                if (d <= inner) f = 1;
                else if (inner >= 1.0) f = 0;
                else f = 1 - (d - inner) / (1.0 - inner);
                if (f <= 0) return null;
                int ix = (int)(dx / wW * pw), iy = (int)(dy / wH * ph);
                if (ix < 0 || ix >= pw || iy < 0 || iy >= ph) return null;
                int c = data[iy * pw + ix];
                int a = (int)(((c >>> 24) & 0xFF) * f * op);
                return a <= 0 ? null : (a << 24) | (c & 0x00FFFFFF);
            }
        });
    }

    // shift+block → 配置面板
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() && player.isShiftKeyDown()) {
            int slot = hand == InteractionHand.MAIN_HAND
                    ? player.getInventory().selected
                    : 40; // offhand slot
                Minecraft.getInstance().setScreen(new ClothScreen(stack, slot));
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx, java.util.List<Component> tip, TooltipFlag f) {
        double z = s.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.12);
        float op = s.getOrDefault(ModDataComponents.OPACITY.get(), 0.8f);
        int tint = s.getOrDefault(Fresco.CLOTH_TINT.get(), 0xFFFFFFFF);
        int sat = s.getOrDefault(Fresco.CLOTH_SATURATION.get(), 0);
        tip.add(Component.translatable("item.fresco.cloth.size", String.format("%.3f", z)));
        tip.add(Component.translatable("item.fresco.cloth.opacity", String.format("%.0f", op * 100)));
        if (sat > 0) {
            String hex = String.format("#%06X", tint & 0x00FFFFFF);
            tip.add(Component.translatable("item.fresco.cloth.tint", hex)
                    .withStyle(Style.EMPTY.withColor(tint & 0x00FFFFFF)));
        }
        tip.add(Component.translatable("item.fresco.cloth.help").withStyle(Style.EMPTY.withColor(0xAAAAAA)));
    }

    @OnlyIn(Dist.CLIENT)
    @EventBusSubscriber(modid = Fresco.MODID, value = Dist.CLIENT)
    private static class MouseUpHandler {
        private static boolean wasPressed = false;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            Player player = mc.player;
            if (!player.level().isClientSide) return;

            if (net.minecraft.client.Minecraft.getInstance().options.keyUse.isDown()) {
                wasPressed = true;
            } else if(wasPressed){
                wasPressed = false;
                int slot = player.getInventory().selected;
                PacketDistributor.sendToServer(new ItemSyncPacket(slot, player.getMainHandItem()));
            }
        }
    }
}
