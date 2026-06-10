package com.astune.fresco.item;

import com.astune.painter.api.*;
import com.astune.painter.api.blend.BlendContext;
import com.astune.painter.api.blend.BlendFunction;
import com.astune.painter.network.ItemSyncPacket;
import com.astune.painter.registry.ModDataComponents;
import com.astune.fresco.Fresco;
import com.astune.fresco.screen.EraserScreen;
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

public class EraserItem extends Item implements IPaintProvider {

    private static final double SIZE_DEF = 0.1;
    private static final float OPACITY_DEF = 0.5f;
    private static final float FEATHER_DEF = 0.3f;

    public EraserItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .component(ModDataComponents.BRUSH_SIZE.get(), SIZE_DEF)
                .component(ModDataComponents.OPACITY.get(), OPACITY_DEF)
                .component(ModDataComponents.FEATHER_STRENGTH.get(), FEATHER_DEF));
        PaintProviders.register(this, this);
    }

    @Override
    public Integer getColor(ItemStack brush, Player p, Level l, BlockPos pos, CanvasFace f, int px, int py) {
        return null;
    }

    @Override
    public Double getStep() { return 0.03; }

    // ── 面方向固定（同 ClothItem，垂直面 v=(0,-1,0)）──
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

    // ── 绘画条件：client + 按住右键 + 距离步进 ──
    private static Vec3 lastHitLoc = null;

    @Override
    public boolean shouldPaint(Player player, BlockHitResult result) {
        if (!(player.level().isClientSide
                && Minecraft.getInstance().options.keyUse.isDown())) return false;
        if (lastHitLoc == null) {
            lastHitLoc = result.getLocation();
            return true;
        }
        if (lastHitLoc.distanceTo(result.getLocation()) > getStep()) {
            lastHitLoc = result.getLocation();
            return true;
        }
        return false;
    }

    // ── 自定义 BlendFunction：降低现有像素 alpha ──
    @Override
    public BlendFunction getCustomBlendFunction(ItemStack brushStack) {
        return ctx -> {
            int existing = ctx.existingColor;
            int newVal   = ctx.newColor;
            if (newVal == 0) return false;

            int existingAlpha = (existing >> 24) & 0xFF;
            float strength = newVal / 255.0f;

            int newAlpha = Math.round(existingAlpha * (1.0f - strength));
            if (newAlpha < 0) newAlpha = 0;
            if (newAlpha > 255) newAlpha = 255;

            if (newAlpha == existingAlpha) return false;

            int result = (newAlpha << 24) | (existing & 0x00FFFFFF);
            if (newAlpha == 0){
                ctx.face.pixels().setPixel(ctx.px, ctx.py, 0);
            }else{
                ctx.face.pixels().setPixel(ctx.px, ctx.py, result);
            }
            return true;
        };
    }

    // ── 圆形 Pattern，getPixel 用 alpha 编码擦除强度 ──
    @Override
    public PaintPattern getPattern(ItemStack brush, Player player, Level level,
                                    BlockPos pos, Vec3 hit) {
        double size = brush.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), SIZE_DEF);
        float opacity = brush.getOrDefault(ModDataComponents.OPACITY.get(), OPACITY_DEF);
        float feather = brush.getOrDefault(ModDataComponents.FEATHER_STRENGTH.get(), FEATHER_DEF);
        if (size <= 0) return null;

        double wW = size * 2, wH = size * 2;
        double r = size;
        double inner = 1.0 - feather;

        return new PaintPattern(wW, wH, new PixelProvider() {
            @Override
            public BlendMode getBlendMode() { return BlendMode.OVERWRITE; }

            @Override
            public Integer getPixel(double dx, double dy) {
                double d = Math.sqrt((dx - r) * (dx - r) + (dy - r) * (dy - r)) / r;
                if (d > 1) return null;

                float f;
                if (d <= inner) f = 1.0f;
                else if (inner >= 1.0) f = 0.0f;
                else f = (float) (1.0 - (d - inner) / (1.0 - inner));
                if (f <= 0) return null;

                // alpha 编码擦除强度 0-255，RGB 无关
                int strength = Math.round(f * opacity * 255);
                return strength <= 0 ? null : strength;
            }
        });
    }

    // ── Shift+右键 → 配置界面 ──
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() && player.isShiftKeyDown()) {
            int slot = hand == InteractionHand.MAIN_HAND
                    ? player.getInventory().selected
                    : 40;
            Minecraft.getInstance().setScreen(new EraserScreen(stack, slot));
        }
        return InteractionResultHolder.success(stack);
    }

    // ── Tooltip ──
    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx,
                                java.util.List<Component> tip, TooltipFlag f) {
        double size = s.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), SIZE_DEF);
        float opacity = s.getOrDefault(ModDataComponents.OPACITY.get(), OPACITY_DEF);
        float feather = s.getOrDefault(ModDataComponents.FEATHER_STRENGTH.get(), FEATHER_DEF);
        tip.add(Component.translatable("item.fresco.eraser.size", String.format("%.3f", size)));
        tip.add(Component.translatable("item.fresco.eraser.opacity", String.format("%.0f", opacity * 100)));
        tip.add(Component.translatable("item.fresco.eraser.feather", String.format("%.0f", feather * 100)));
        tip.add(Component.translatable("item.fresco.eraser.help")
                .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
    }

}
