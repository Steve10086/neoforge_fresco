package com.astune.pigmentum.item;

import com.astune.pigmentum.screen.PaintbrushScreen;

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
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
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
        return resolveOffhandColor(player);
    }

    @Override
    public PaintPattern getPattern(ItemStack brush, Player player, Level level,
                                   net.minecraft.core.BlockPos pos, net.minecraft.world.phys.Vec3 hit) {
        double size = brush.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.06);
        float opacity = brush.getOrDefault(ModDataComponents.OPACITY.get(), 1.0f);
        String modeStr = brush.getOrDefault(ModDataComponents.BLEND_MODE.get(), BlendMode.OVERWRITE.name());
        BlendMode blendMode = safeBlendMode(modeStr);
        int color = resolveOffhandColor(player);

        return CircularBrushPattern.create(size, opacity, blendMode, color);
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

    // ── 副手颜色解析 ────────────────────────────────────────────

    /**
     * 从副手物品获取颜色：
     * <ul>
     *   <li>染料 → 对应 DyeColor</li>
     *   <li>墨囊 → 黑色 (0xFF000000)</li>
     *   <li>Palette → 已存储颜色</li>
     *   <li>其他 → 白色 (0xFFFFFFFF)</li>
     * </ul>
     */
    public static int resolveOffhandColor(Player player) {
        if (player == null) return 0xFFFFFFFF;
        ItemStack offhand = player.getOffhandItem();

        // 1. 染料
        if (offhand.getItem() instanceof DyeItem dyeItem) {
            return 0xFF000000 | dyeItem.getDyeColor().getTextureDiffuseColor();
        }

        // 2. 墨囊 → 黑色
        if (offhand.is(Items.INK_SAC)) {
            return 0xFF000000;
        }

        // 3. Palette 已存储颜色
        if (offhand.getItem() instanceof PaletteItem) {
            return PaletteItem.getCurrentColor(offhand);
        }

        // 4. 默认白色
        return 0xFFFFFFFF;
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
            tooltip.add(Component.translatable("item.pigmentum.custom_paintbrush.color", hex)
                    .withStyle(Style.EMPTY.withColor(color & 0x00FFFFFF)));
        }

        double size = stack.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.06);
        float opacity = stack.getOrDefault(ModDataComponents.OPACITY.get(), 1.0f);
        String mode = stack.getOrDefault(ModDataComponents.BLEND_MODE.get(), BlendMode.OVERWRITE.name());

        tooltip.add(Component.translatable("item.pigmentum.custom_paintbrush.size", String.format("%.3f", size)));
        tooltip.add(Component.translatable("item.pigmentum.custom_paintbrush.opacity", String.format("%.0f", opacity * 100)));
        tooltip.add(Component.translatable("item.pigmentum.custom_paintbrush.blend", mode));
        tooltip.add(Component.translatable("item.pigmentum.custom_paintbrush.help"));
    }
}
