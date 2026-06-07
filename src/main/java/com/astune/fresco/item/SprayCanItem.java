package com.astune.fresco.item;

import com.astune.painter.api.BlendMode;
import com.astune.painter.api.IPaintProvider;
import com.astune.painter.api.PaintPattern;
import com.astune.painter.api.PaintProviders;
import com.astune.painter.registry.ModDataComponents;
import com.astune.fresco.Fresco;
import com.astune.fresco.screen.SprayCanScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

public class SprayCanItem extends Item implements IPaintProvider {

    public SprayCanItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .component(ModDataComponents.BRUSH_SIZE.get(), 0.5)
                .component(ModDataComponents.FEATHER_STRENGTH.get(), 0.7f)
                .component(Fresco.SPRAY_DENSITY.get(), 0.5)
                .component(ModDataComponents.OPACITY.get(), 0.5f)
                .component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
        );
        PaintProviders.register(this, this);
    }

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
        double density = brush.getOrDefault(Fresco.SPRAY_DENSITY.get(), 0.5);
        int color = OffhandColorResolver.resolve(player);
        int tint = getStoredDyeColor(brush);

        double dist = hit.distanceTo(player.getEyePosition());
        double t = Math.clamp((dist - 1.0) / 3.0, 0.0, 1.0);
        double size = Math.min(baseSize * (1.0 + t * 2.0), 1.5);
        double effectiveOpacity = baseOpacity * (1.0 - t * 0.9);

        return CircularBrushPattern.create(size, (float) effectiveOpacity, BlendMode.ADD,
                color, (double) feather, density, tint);
    }

    @Override
    public Double getStep() {
        return 0.02;
    }

    // ── 右键：Shift=打开配置 ────────────────────────────────────

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
        return InteractionResultHolder.pass(stack);
    }

    @OnlyIn(Dist.CLIENT)
    private void openConfigScreen(ItemStack stack, int slot) {
        Minecraft.getInstance().setScreen(new SprayCanScreen(stack, slot));
    }

    // ── 库存内右键存取染料（类似束口袋）──────────────────────────

    /** 手持喷罐右键某槽位：槽位有染料→存入 / 槽位空→取出 */
    @Override
    public boolean overrideStackedOnOther(ItemStack sprayCan, Slot slot,
                                          ClickAction action, Player player) {
        if (sprayCan.getCount() != 1 || action != ClickAction.SECONDARY) return false;

        ItemStack slotItem = slot.getItem();
        ItemStack stored = sprayCan.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne();

        if (slotItem.getItem() instanceof DyeItem) {
            // 槽位有染料 → 存入喷罐；喷罐已有染料则先归还旧染料
            ItemStack newDye = slot.safeTake(1, 1, player);
            if (newDye.isEmpty()) return false;
            if (!stored.isEmpty()) {
                slot.safeInsert(stored.copy());
            }
            sprayCan.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(newDye)));
            playDyeSound(player);
            return true;
        }

        if (stored.isEmpty()) return false;
        // 空手或非染料槽位 → 取出染料到槽位
        ItemStack remainder = slot.safeInsert(stored.copy());
        if (remainder.isEmpty()) {
            sprayCan.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            playDyeSound(player);
        }
        return true;
    }

    /** 右键槽位中的喷罐：手持染料→存入 / 空手→取出 */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack sprayCan, ItemStack carried,
                                            Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (sprayCan.getCount() != 1 || action != ClickAction.SECONDARY) return false;

        ItemStack stored = sprayCan.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne();

        if (carried.isEmpty()) {
            // 空手 → 取出染料到光标
            if (stored.isEmpty()) return false;
            sprayCan.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            access.set(stored.copy());
            playDyeSound(player);
            return true;
        }

        if (carried.getItem() instanceof DyeItem) {
            // 手持染料 → 存入喷罐；喷罐已有染料则先归还旧染料到槽位
            if (!stored.isEmpty()) {
                ItemStack remaining = slot.safeInsert(stored.copy());
                if (!remaining.isEmpty()) return false; // 旧染料放不回去则放弃
            }
            ItemStack insert = carried.copyWithCount(1);
            carried.shrink(1);
            sprayCan.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(insert)));
            playDyeSound(player);
            return true;
        }

        return false;
    }

    private static void playDyeSound(Player player) {
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.DYE_USE, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    // ── 从存储的染料中提取颜色 ───────────────────────────────────

    public static int getStoredDyeColor(ItemStack stack) {
        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        ItemStack dye = contents.copyOne();
        if (dye.getItem() instanceof DyeItem dyeItem) {
            return 0xFF000000 | dyeItem.getDyeColor().getTextureDiffuseColor();
        }
        return 0;
    }

    public static boolean hasDye(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .copyOne().isEmpty();
    }

    // ── Tooltip ──────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                java.util.List<Component> tooltip, TooltipFlag flag) {
        double size = stack.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.5);
        float feather = stack.getOrDefault(ModDataComponents.FEATHER_STRENGTH.get(), 0.7f);
        double density = stack.getOrDefault(Fresco.SPRAY_DENSITY.get(), 0.5);
        float opacity = stack.getOrDefault(ModDataComponents.OPACITY.get(), 0.5f);

        tooltip.add(Component.translatable("item.fresco.spray_can.size", String.format("%.3f", size)));
        tooltip.add(Component.translatable("item.fresco.spray_can.feather", String.format("%.0f", feather * 100)));
        tooltip.add(Component.translatable("item.fresco.spray_can.density", String.format("%.0f", density * 100)));
        tooltip.add(Component.translatable("item.fresco.spray_can.opacity", String.format("%.0f", opacity * 100)));

        int tint = getStoredDyeColor(stack);
        if (tint != 0) {
            String hex = String.format("#%06X", tint & 0x00FFFFFF);
            tooltip.add(Component.translatable("item.fresco.spray_can.tint", hex)
                    .withStyle(Style.EMPTY.withColor(tint & 0x00FFFFFF)));
        }
        tooltip.add(Component.translatable("item.fresco.spray_can.help")
                .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
    }
}
