package com.astune.fresco.item;

import com.astune.fresco.Fresco;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class PaletteItem extends Item {

    public PaletteItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() && player.isShiftKeyDown()) {
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }
        return super.useOn(context);
    }

    /** Stores the picked ARGB color and updates the item's visible name. */
    public static void setCurrentColor(ItemStack stack, int color) {
        stack.set(Fresco.PALETTE_COLOR.get(), color);
        updateDisplayName(stack);
    }

    /** Keeps the item name and its stored color in sync for every color editing path. */
    public static void updateDisplayName(ItemStack stack) {
        int color = getCurrentColor(stack);
        String hex = String.format("#%06X", color & 0x00FFFFFF);
        Component name = Component.translatable(stack.getItem().getDescriptionId())
                .append(Component.literal(" (" + hex + ")"))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color & 0x00FFFFFF)));
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    /** Returns the stored palette color, or white when no color has been set. */
    public static int getCurrentColor(ItemStack stack) {
        return stack.getOrDefault(Fresco.PALETTE_COLOR.get(), 0xFFFFFFFF);
    }
}
