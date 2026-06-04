package com.astune.pigmentum.item;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** 标记性 TooltipComponent — 携带染料的 ItemStack，由 DyeTooltipRenderer 渲染为图标 */
public record DyeTooltipComponent(ItemStack dye) implements TooltipComponent {}
