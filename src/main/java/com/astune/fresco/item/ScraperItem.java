package com.astune.fresco.item;

import com.astune.painter.api.*;
import com.astune.painter.api.blend.BlendContext;
import com.astune.painter.api.blend.BlendFunction;
import com.astune.painter.network.ItemSyncPacket;
import com.astune.painter.registry.ModDataComponents;
import com.astune.fresco.Fresco;
import com.astune.fresco.screen.ScraperScreen;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.astune.fresco.item.ScraperPattern.SCRAPER_MASK;

public class ScraperItem extends Item implements IPaintProvider {

    private static final double SIZE_DEF = 0.12;
    private static final float THICKNESS_DEF = 0.5f;

    public ScraperItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .component(ModDataComponents.BRUSH_SIZE.get(), SIZE_DEF)
                .component(Fresco.SCRAPER_THICKNESS.get(), THICKNESS_DEF)
                .component(Fresco.SCRAPER_ANGLE_LOCKED.get(), false));
        PaintProviders.register(this, this);
    }

    @Override public Integer getColor(ItemStack brush, Player p, Level l, BlockPos pos, CanvasFace f, int px, int py) { return null; }

    @Override public Double getStep() { return 0.02; }


    @Override
    public Vec3[] transformPatternAxes(Player player, Vec3 hitPoint, Direction face, double w, double h) {
        boolean locked = player.getMainHandItem().getOrDefault(Fresco.SCRAPER_ANGLE_LOCKED.get(), false);
        if (locked && lockedAxes != null) {
            Vec3 origin = hitPoint.subtract(lockedAxes[0].scale(w / 2)).subtract(lockedAxes[1].scale(h / 2));
            return new Vec3[] { origin, lockedAxes[0], lockedAxes[1] };
        }
        Vec3 normalVec = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 right = lookVec.cross(normalVec).normalize();
        Vec3 up = normalVec.cross(right).normalize();
        Vec3 origin;
        if (right.lengthSqr() < 0.001 || up.lengthSqr() < 0.001) {
            origin = new Vec3(0.0, 1.0, 0.0);
            right = origin.cross(normalVec).normalize();
            up = normalVec.cross(right).normalize();
        }

        origin = hitPoint.subtract(right.scale(w / 2.0)).subtract(up.scale(h / 2.0));
        Vec3 rightTotal = right.scale(w);
        Vec3 upTotal = up.scale(h);
        if (locked) lockedAxes = new Vec3[] { rightTotal, upTotal };
        return new Vec3[]{origin, rightTotal, upTotal};
    }

    // ── 笔画状态（自上次落笔开始一直维持）──
    private static Vec3 lastHitLoc = null;
    private static PixelMatrix strokeBuffer = null;
    private static Vec3[] lockedAxes = null;
    // 用于 BlendFunction 传递当前映射到的 buffer 位置
    private static int currentBufX = 0, currentBufY = 0;

    // ── 新笔画开始：初始化缓冲区（全填充）──
    private static void initBuffer(PixelMatrix buf, int fillColor) {
        buf.fill(fillColor);
    }

    // ── 绘画条件 ──
    @Override
    public boolean shouldPaint(Player player, BlockHitResult result) {
        if (!(player.level().isClientSide
                && Minecraft.getInstance().options.keyUse.isDown())) return false;
        if (lastHitLoc == null) {
            // ── 新笔画开始：初始化缓冲区 ──
            lastHitLoc = result.getLocation();
            lockedAxes = null;
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof ScraperItem)) return true;
            double brushSize = stack.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), SIZE_DEF);
            int res = Math.max(1, (int) Math.ceil(brushSize * 16));
            strokeBuffer = new PixelMatrix(res, res * 2);
            int color = OffhandColorResolver.resolve(player);
            initBuffer(strokeBuffer, color);
            return true;
        }
        if (lastHitLoc.distanceTo(result.getLocation()) > getStep()) {
            lastHitLoc = result.getLocation();
            return true;
        }
        return false;
    }



    // ── getPattern ──
    @Override
    public PaintPattern getPattern(ItemStack brush, Player player, Level level,
                                    BlockPos pos, Vec3 hit) {
        double brushSize = brush.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), SIZE_DEF);
        if (brushSize <= 0 || strokeBuffer == null) return null;

        double wW = brushSize * 2, wH = brushSize * 4;
        final int bufW = strokeBuffer.getWidth();
        final int bufH = strokeBuffer.getHeight();

        return new PaintPattern(wW, wH, new PixelProvider() {
            @Override
            public BlendMode getBlendMode() { return BlendMode.OVERWRITE; }

            @Override
            public Integer getPixel(double dx, double dy) {
                // 缩放坐标到 16×16 掩码
                int mx = (int) (dx / wW * 16);
                int my = (int) (dy / wH * 16);
                if (mx < 0 || mx >= 16 || my < 0 || my >= 16) return null;
                if (SCRAPER_MASK[my][mx] == 0) return null;

                int bx = (int) (dx / wW * bufW);
                int by = (int) (dy / wH * bufH);
                if (bx < 0 || bx >= bufW || by < 0 || by >= bufH) return null;

                currentBufX = bx;
                currentBufY = by;
                int c = strokeBuffer.getPixel(bx, by);
                return ((c >> 24) & 0xFF) == 0 ? null : c;
            }
        });
    }

    // ── 自定义 BlendFunction：吸收画布颜色 ──
    @Override
    public BlendFunction getCustomBlendFunction(ItemStack brushStack) {
        float thickness = brushStack.getOrDefault(Fresco.SCRAPER_THICKNESS.get(), THICKNESS_DEF);

        return ctx -> {
            int newColor = ctx.newColor;
            int existing = ctx.existingColor;
            if (newColor == 0) return false;

            // 写 buffer 颜色到画布
            ctx.face.pixels().setPixel(ctx.px, ctx.py, newColor);

            // 厚度决定吸收程度
            if (thickness > 0.8f || strokeBuffer == null) return true;

            float existingAlpha = ((existing >> 24) & 0xFF) / 255.0f;
            float factor = existingAlpha * thickness;

            int bufColor = strokeBuffer.getPixel(currentBufX, currentBufY);
            int bufR = (bufColor >> 16) & 0xFF;
            int bufG = (bufColor >> 8) & 0xFF;
            int bufB = bufColor & 0xFF;

            int existR = (existing >> 16) & 0xFF;
            int existG = (existing >> 8) & 0xFF;
            int existB = existing & 0xFF;

            int newR = Math.round(bufR + (existR - bufR) * factor);
            int newG = Math.round(bufG + (existG - bufG) * factor);
            int newB = Math.round(bufB + (existB - bufB) * factor);
            newR = Math.max(0, Math.min(255, newR));
            newG = Math.max(0, Math.min(255, newG));
            newB = Math.max(0, Math.min(255, newB));

            strokeBuffer.setPixel(currentBufX, currentBufY,
                    (255 << 24) | (newR << 16) | (newG << 8) | newB);
            return true;
        };
    }

    // ── Shift+右键 → 配置界面 ──
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() && player.isShiftKeyDown()) {
            int slot = hand == InteractionHand.MAIN_HAND
                    ? player.getInventory().selected
                    : 40;
            Minecraft.getInstance().setScreen(new ScraperScreen(stack, slot));
        }
        return InteractionResultHolder.success(stack);
    }

    // ── Tooltip ──
    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx,
                                java.util.List<Component> tip, TooltipFlag f) {
        double size = s.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), SIZE_DEF);
        float thickness = s.getOrDefault(Fresco.SCRAPER_THICKNESS.get(), THICKNESS_DEF);
        boolean locked = s.getOrDefault(Fresco.SCRAPER_ANGLE_LOCKED.get(), false);
        tip.add(Component.translatable("item.fresco.scraper.size", String.format("%.3f", size)));
        tip.add(Component.translatable("item.fresco.scraper.thickness", String.format("%.0f", thickness * 100)));
        tip.add(Component.translatable("item.fresco.scraper.angle",
                Component.translatable(locked ? "item.fresco.scraper.angle_locked" : "item.fresco.scraper.angle_default")));
        tip.add(Component.translatable("item.fresco.scraper.help")
                .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
    }

    @OnlyIn(Dist.CLIENT)
    @EventBusSubscriber(modid = Fresco.MODID, value = Dist.CLIENT)
    private static class MouseUpHandler {
        private static boolean wasPressed = false;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            if (!(mc.player.getMainHandItem().getItem() instanceof ScraperItem)) return;

            if (mc.options.keyUse.isDown()) {
                wasPressed = true;
            } else if (wasPressed) {
                wasPressed = false;
                lastHitLoc = null;
            }
        }
    }
}
