package com.astune.fresco.screen.widget;

import com.astune.fresco.Fresco;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

/** HSV saturation/value picker whose hue can be changed without replacing the widget. */
public final class ColorSquareWidget extends AbstractWidget implements AutoCloseable {
    private final NativeImage image;
    private final DynamicTexture texture;
    private final ResourceLocation textureLocation;
    private final BiConsumer<Double, Double> onChanged;
    private double hue;
    private double saturation;
    private double value;

    public ColorSquareWidget(int x, int y, int size, double hue, double saturation,
                             double value, BiConsumer<Double, Double> onChanged) {
        super(x, y, size, size, Component.empty());
        this.hue = ColorPickerMath.clamp01(hue);
        this.saturation = ColorPickerMath.clamp01(saturation);
        this.value = ColorPickerMath.clamp01(value);
        this.onChanged = onChanged;

        this.image = new NativeImage(Math.max(1, size), Math.max(1, size), false);
        this.texture = new DynamicTexture(this.image);
        this.textureLocation = Minecraft.getInstance().getTextureManager()
                .register("fresco_palette_square", this.texture);
        rebuildTexture();
    }

    public void setHue(double hue) {
        this.hue = ColorPickerMath.clamp01(hue);
        rebuildTexture();
    }

    public void setSelection(double saturation, double value) {
        this.saturation = ColorPickerMath.clamp01(saturation);
        this.value = ColorPickerMath.clamp01(value);
    }

    public double getSaturation() {
        return saturation;
    }

    public double getValue() {
        return value;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        graphics.blit(textureLocation, getX(), getY(), getWidth(), getHeight(),
                0.0F, 0.0F, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight());

        int markerX = getX() + Mth.floor(saturation * (getWidth() - 1));
        int markerY = getY() + Mth.floor((1.0 - value) * (getHeight() - 1));
        graphics.fill(markerX - 3, markerY - 3, markerX + 4, markerY + 4, 0xFF17110B);
        graphics.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, 0xFFF8F0D8);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        updateSelection(mouseX, mouseY);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        updateSelection(mouseX, mouseY);
    }

    private void updateSelection(double mouseX, double mouseY) {
        saturation = Mth.clamp((mouseX - getX()) / Math.max(1.0, getWidth() - 1.0), 0.0, 1.0);
        value = 1.0 - Mth.clamp((mouseY - getY()) / Math.max(1.0, getHeight() - 1.0), 0.0, 1.0);
        onChanged.accept(saturation, value);
    }

    private void rebuildTexture() {
        for (int y = 0; y < image.getHeight(); y++) {
            double pixelValue = 1.0 - (image.getHeight() <= 1 ? 0.0 : (double) y / (image.getHeight() - 1));
            for (int x = 0; x < image.getWidth(); x++) {
                double pixelSaturation = image.getWidth() <= 1 ? 0.0 : (double) x / (image.getWidth() - 1);
                int argb = ColorPickerMath.fromHsv(hue, pixelSaturation, pixelValue);
                image.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(argb));
            }
        }
        texture.upload();
    }

    @Override
    public void close() {
        Minecraft.getInstance().getTextureManager().release(textureLocation);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        this.defaultButtonNarrationText(narration);
    }
}
