package com.astune.fresco.item;

import com.astune.fresco.client.BrushParticleSpawner;
import com.astune.fresco.screen.PaintbrushScreen;

import com.astune.painter.api.BlendMode;
import com.astune.painter.api.IPaintProvider;
import com.astune.painter.api.PaintPattern;
import com.astune.painter.api.PaintProviders;
import com.astune.painter.registry.ModDataComponents;
import net.minecraft.client.Minecraft;
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

/**
 * 自定义画笔 - 圆形 pattern，副手取色，附带参数调整屏幕。
 * Feather 固定为 0.9。
 */
public class CustomPaintbrush extends Item implements IPaintProvider {

    public CustomPaintbrush() {
        super(new Item.Properties()
                .stacksTo(1)
                .component(ModDataComponents.BRUSH_SIZE.get(), 0.06)
                .component(ModDataComponents.OPACITY.get(), 1.0f)
                .component(ModDataComponents.BLEND_MODE.get(), BlendMode.OVERWRITE.name())
                .component(ModDataComponents.STEP_SIZE.get(), 0.01)
        );

        PaintProviders.register(this, this);
    }

    // ── IPaintProvider ──────────────────────────────────────────

    @Override
    public Integer getColor(ItemStack brush, Player player, Level level, net.minecraft.core.BlockPos pos,
                            com.astune.painter.api.CanvasFace face, int pixelX, int pixelY) {
        return OffhandColorResolver.resolve(player);
    }

    @Override
    public PaintPattern getPattern(ItemStack brush, Player player, Level level,
                                   net.minecraft.core.BlockPos pos, net.minecraft.world.phys.Vec3 hit) {
        double size = brush.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.06);
        float opacity = brush.getOrDefault(ModDataComponents.OPACITY.get(), 1.0f);
        String modeStr = brush.getOrDefault(ModDataComponents.BLEND_MODE.get(), BlendMode.OVERWRITE.name());
        BlendMode blendMode = safeBlendMode(modeStr);
        int color = OffhandColorResolver.resolve(player);

        return CircularBrushPattern.create(size, opacity, blendMode, color, CircularBrushPattern.DEFAULT_BLUR);
    }

    @Override
    public Double getStep() {
        return 0.02;
    }

    protected static BlendMode safeBlendMode(String name) {
        try {
            return BlendMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return BlendMode.OVERWRITE;
        }
    }

    private static Vec3 lastHitLoc = null;

    @Override
    public boolean shouldPaint(Player player, BlockHitResult result){
        if (!(player.level().isClientSide
                && net.minecraft.client.Minecraft.getInstance().options.keyUse.isDown())) return false;
        if (lastHitLoc == null) {
            lastHitLoc = result.getLocation();
            spawnParticle(player, result);
            return true;
        }
        if (lastHitLoc.distanceTo(result.getLocation()) > getStep()){
            lastHitLoc = result.getLocation();
            spawnParticle(player, result);
            return true;
        }

        return false;
    }

    private void spawnParticle(Player player, BlockHitResult result) {
        double size = player.getMainHandItem().getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.06);
        int color = OffhandColorResolver.resolve(player);
        BrushParticleSpawner.trySpawn(player.level(), result.getLocation(), color, size, player.level().random);
    }

    // ── 右键打开配置屏幕 ─────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() && player.isShiftKeyDown()) {
            int slot = hand == InteractionHand.MAIN_HAND
                    ? player.getInventory().selected
                    : 40; // offhand slot
            openConfigScreen(stack, slot);
        }
        return InteractionResultHolder.success(stack);
    }

    @OnlyIn(Dist.CLIENT)
    private void openConfigScreen(ItemStack stack, int slot) {
        Minecraft.getInstance().setScreen(new PaintbrushScreen(stack, slot));
    }

    // ── Tooltip ──────────────────────────────────────────────────

    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                java.util.List<Component> tooltip, TooltipFlag flag) {
        int color = stack.getOrDefault(ModDataComponents.CURRENT_COLOR.get(), 0xFFFFFFFF);
        if (color != 0xFFFFFFFF) {
            String hex = String.format("#%06X", color & 0x00FFFFFF);
            tooltip.add(Component.translatable("item.fresco.custom_paintbrush.color", hex)
                    .withStyle(Style.EMPTY.withColor(color & 0x00FFFFFF)));
        }

        double size = stack.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.06);
        float opacity = stack.getOrDefault(ModDataComponents.OPACITY.get(), 1.0f);
        String mode = stack.getOrDefault(ModDataComponents.BLEND_MODE.get(), BlendMode.OVERWRITE.name());

        tooltip.add(Component.translatable("item.fresco.custom_paintbrush.size", String.format("%.3f", size)));
        tooltip.add(Component.translatable("item.fresco.custom_paintbrush.opacity", String.format("%.0f", opacity * 100)));
        tooltip.add(Component.translatable("item.fresco.custom_paintbrush.blend", mode));
        tooltip.add(Component.translatable("item.fresco.custom_paintbrush.help"));
    }
}
