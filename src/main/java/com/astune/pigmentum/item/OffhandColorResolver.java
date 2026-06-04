package com.astune.pigmentum.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 副手颜色解析工具类。
 * 从 CustomPaintbrush 提取，供画笔、喷罐等所有需要副手颜色的 IPaintProvider 复用。
 */
public final class OffhandColorResolver {

    private OffhandColorResolver() {}

    /**
     * 从玩家副手物品获取 ARGB 颜色：
     * <ul>
     *   <li>染料 → 对应 DyeColor 的纹理色</li>
     *   <li>墨囊 → 黑色 (0xFF000000)</li>
     *   <li>Palette → 已存储的颜色</li>
     *   <li>其他 → 白色 (0xFFFFFFFF)</li>
     * </ul>
     */
    public static int resolve(Player player) {
        if (player == null) return 0xFFFFFFFF;
        ItemStack offhand = player.getOffhandItem();

        if (offhand.getItem() instanceof DyeItem dyeItem) {
            return 0xFF000000 | dyeItem.getDyeColor().getTextureDiffuseColor();
        }
        if (offhand.is(Items.INK_SAC)) {
            return 0xFF000000;
        }
        if (offhand.getItem() instanceof PaletteItem) {
            return PaletteItem.getCurrentColor(offhand);
        }
        return 0xFFFFFFFF;
    }
}
