package com.astune.pigmentum.item;

import com.astune.painter.api.BlendMode;
import com.astune.painter.api.IPaintProvider;
import com.astune.painter.api.PaintPattern;
import com.astune.painter.api.PaintProviders;
import com.astune.painter.api.PixelProvider;
import com.astune.painter.registry.ModDataComponents;
import com.astune.pigmentum.Pigmentum;
import com.astune.pigmentum.screen.SprayCanScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Random;

public class SprayCanItem extends Item implements IPaintProvider {

    public SprayCanItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .component(ModDataComponents.BRUSH_SIZE.get(), 0.5)
                .component(ModDataComponents.FEATHER_STRENGTH.get(), 0.7f)
                .component(Pigmentum.SPRAY_DENSITY.get(), 0.5)
                .component(ModDataComponents.OPACITY.get(), 0.5f)
                .component(Pigmentum.SPRAY_TINT.get(), 0)
        );
        PaintProviders.register(this, this);
    }

    // ── IPaintProvider ──────────────────────────────────────────

    @Override
    public Integer getColor(ItemStack brush, Player player, Level level,
                            net.minecraft.core.BlockPos pos,
                            com.astune.painter.api.CanvasFace face, int pixelX, int pixelY) {
        return OffhandColorResolver.resolve(player);
    }

    @Override
    public PaintPattern getPattern(ItemStack brush, Player player, Level level,
                                   net.minecraft.core.BlockPos pos,
                                   net.minecraft.world.phys.Vec3 hit) {
        double baseSize = brush.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.5);
        float baseOpacity = brush.getOrDefault(ModDataComponents.OPACITY.get(), 0.5f);
        float feather = brush.getOrDefault(ModDataComponents.FEATHER_STRENGTH.get(), 0.7f);
        double density = brush.getOrDefault(Pigmentum.SPRAY_DENSITY.get(), 0.5);
        int color = OffhandColorResolver.resolve(player);
        int tint = brush.getOrDefault(Pigmentum.SPRAY_TINT.get(), 0);

        // 距离缩放（1格→×1.0, 4格→×1.5 大小; ×1.0→×0.5 不透明度）
        double dist = hit.distanceTo(player.getEyePosition());
        double t = Math.clamp((dist - 1.0) / 3.0, 0.0, 1.0);
        double size = baseSize * (1.0 + t * 0.5);       // 1.0× → 1.5×
        double effectiveOpacity = baseOpacity * (1.0 - t * 0.5); // 1.0× → 0.5×

        return CircularBrushPattern.create(size, (float)effectiveOpacity, BlendMode.ADD,
                color, (double)feather, density, tint);
    }

    @Override
    public Double getStep() {
        return 0.02;
    }

    // ── 右键：Shift+配置 / 无Shift+染料副手 = 着色 / 纯点击 = 配置 ──

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                int slot = hand == InteractionHand.MAIN_HAND
                        ? player.getInventory().selected : 40;
                openConfigScreen(stack, slot);
            }
            return InteractionResultHolder.success(stack);
        }

        // 右手点击，副手有染料 → 着色
        if (hand == InteractionHand.MAIN_HAND) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.getItem() instanceof DyeItem dye) {
                int tint = 0xFF000000 | dye.getDyeColor().getTextureDiffuseColor();
                stack.set(Pigmentum.SPRAY_TINT.get(), tint);
                if (!player.isCreative()) offhand.shrink(1);
                if (!level.isClientSide()) {
                    level.playSound(null, player.blockPosition(),
                            SoundEvents.DYE_USE, SoundSource.PLAYERS, 0.6f, 1.2f);
                }
                return InteractionResultHolder.success(stack);
            }
        }

        return InteractionResultHolder.pass(stack);
    }

    @OnlyIn(Dist.CLIENT)
    private void openConfigScreen(ItemStack stack, int slot) {
        Minecraft.getInstance().setScreen(new SprayCanScreen(stack, slot));
    }

    // ── Tooltip ──────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                java.util.List<Component> tooltip, TooltipFlag flag) {
        double size = stack.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.5);
        float feather = stack.getOrDefault(ModDataComponents.FEATHER_STRENGTH.get(), 0.7f);
        double density = stack.getOrDefault(Pigmentum.SPRAY_DENSITY.get(), 0.5);
        float opacity = stack.getOrDefault(ModDataComponents.OPACITY.get(), 0.5f);
        int tint = stack.getOrDefault(Pigmentum.SPRAY_TINT.get(), 0);

        tooltip.add(Component.translatable("item.pigmentum.spray_can.size", String.format("%.3f", size)));
        tooltip.add(Component.translatable("item.pigmentum.spray_can.feather", String.format("%.0f", feather * 100)));
        tooltip.add(Component.translatable("item.pigmentum.spray_can.density", String.format("%.0f", density * 100)));
        tooltip.add(Component.translatable("item.pigmentum.spray_can.opacity", String.format("%.0f", opacity * 100)));

        if (tint != 0) {
            String hex = String.format("#%06X", tint & 0x00FFFFFF);
            tooltip.add(Component.translatable("item.pigmentum.spray_can.tint", hex)
                    .withStyle(Style.EMPTY.withColor(tint & 0x00FFFFFF)));
        }
        tooltip.add(Component.translatable("item.pigmentum.spray_can.help")
                .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
    }
}
