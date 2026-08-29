package com.astune.fresco.screen.widget;

import com.astune.fresco.Fresco;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/** A reusable horizontal or vertical color bar with a static image track. */
public final class ColorBarWidget extends AbstractWidget implements AutoCloseable {
    private static final ResourceLocation TEX_BAR =
            ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "widget/slider");
    private static final ResourceLocation TEX_BAR_HIGHLIGHTED =
            ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "widget/slider_highlighted");
    private static final ResourceLocation TEX_HANDLE =
            ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "widget/slider_handle");
    private static final ResourceLocation TEX_HANDLE_HIGHLIGHTED =
            ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "widget/slider_handle_highlighted");

    private static final int HANDLE_SIZE = 8;

    private final boolean vertical;
    private final DoubleConsumer onChanged;
    private final ResourceLocation trackTexture;
    private double value;

    public ColorBarWidget(int x, int y, int width, int height, boolean vertical,
                          double initialValue, ResourceLocation trackTexture,
                          DoubleConsumer onChanged) {
        super(x, y, width, height, Component.empty());
        this.vertical = vertical;
        this.value = ColorPickerMath.clamp01(initialValue);
        this.trackTexture = trackTexture;
        this.onChanged = onChanged;
    }

    public void setValue(double value) {
        this.value = ColorPickerMath.clamp01(value);
    }

    public double getValue() {
        return value;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        renderRotatedSprite(graphics,
                this.isHoveredOrFocused() ? TEX_BAR_HIGHLIGHTED : TEX_BAR);
        renderRotatedSprite(graphics, trackTexture);

        ResourceLocation handle = this.isHoveredOrFocused() ? TEX_HANDLE_HIGHLIGHTED : TEX_HANDLE;
        if (vertical) {
            int markerY = getY() + Mth.floor(value * (getHeight() - 1));
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + getWidth() / 2.0F, markerY, 0.0F);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(90.0F));
            graphics.blitSprite(handle, -HANDLE_SIZE / 2, -getWidth() / 2,
                    HANDLE_SIZE, getWidth());
            graphics.pose().popPose();
        } else {
            int markerX = getX() + Mth.floor(value * (getWidth() - HANDLE_SIZE));
            graphics.blitSprite(handle, markerX, getY(), HANDLE_SIZE, getHeight());
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        updateValue(mouseX, mouseY);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        updateValue(mouseX, mouseY);
    }

    private void updateValue(double mouseX, double mouseY) {
        double next = vertical
                ? (mouseY - getY()) / Math.max(1.0, getHeight() - 1.0)
                : (mouseX - getX()) / Math.max(1.0, getWidth() - 1.0);
        value = Mth.clamp(next, 0.0, 1.0);
        onChanged.accept(value);
    }

    private void renderRotatedSprite(GuiGraphics graphics, ResourceLocation sprite) {
        if (!vertical) {
            graphics.blitSprite(sprite, getX(), getY(), getWidth(), getHeight());
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(getX() + getWidth() / 2.0F, getY() + getHeight() / 2.0F, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(90.0F));
        graphics.blitSprite(sprite, -getHeight() / 2, -getWidth() / 2,
                getHeight(), getWidth());
        graphics.pose().popPose();
    }

    @Override
    public void close() {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        this.defaultButtonNarrationText(narration);
    }
}
