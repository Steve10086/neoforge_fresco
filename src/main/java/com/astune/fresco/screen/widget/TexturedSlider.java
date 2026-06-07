package com.astune.fresco.screen.widget;

import com.astune.fresco.Fresco;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.function.Consumer;
import java.util.function.Function;

public class TexturedSlider extends AbstractWidget {

    private static final ResourceLocation TEX_SLIDER =
            ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "widget/slider");
    private static final ResourceLocation TEX_SLIDER_HIGHLIGHTED =
            ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "widget/slider_highlighted");
    private static final ResourceLocation TEX_SLIDER_HANDLE =
            ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "widget/slider_handle");
    private static final ResourceLocation TEX_SLIDER_HANDLE_HIGHLIGHTED =
            ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "widget/slider_handle_highlighted");

    private static final int HANDLE_W = 8;

    private double value;
    private final Consumer<Double> onChanged;
    private final Function<Double, String> valueFormatter;

    public TexturedSlider(int x, int y, int w, int h, Component label, double init, Consumer<Double> cb) {
        this(x, y, w, h, label, init, cb, v -> String.format("%.0f%%", v * 100));
    }

    public TexturedSlider(int x, int y, int w, int h, Component label, double init,
                           Consumer<Double> cb, Function<Double, String> valueFormatter) {
        super(x, y, w, h, label);
        this.value = init;
        this.onChanged = cb;
        this.valueFormatter = valueFormatter;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        Component display = Component.literal(
                this.getMessage().getString() + ": " + valueFormatter.apply(value));
        int labelW = Minecraft.getInstance().font.width(display);
        g.drawString(Minecraft.getInstance().font, display,
                this.getX() + (this.getWidth() - labelW) / 2, this.getY() - 12, 0xFFCCCCCC);

        g.blitSprite(this.isFocused() ? TEX_SLIDER_HIGHLIGHTED : TEX_SLIDER,
                this.getX(), this.getY(), this.getWidth(), this.getHeight());
        g.blitSprite(this.isHovered ? TEX_SLIDER_HANDLE_HIGHLIGHTED : TEX_SLIDER_HANDLE,
                this.getX() + (int) (this.value * (this.width - HANDLE_W)),
                this.getY(), HANDLE_W, this.getHeight());
    }

    @Override
    public void onClick(double mx, double my) {
        updateValue(mx);
    }

    @Override
    protected void onDrag(double mx, double my, double dx, double dy) {
        updateValue(mx);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput o) {
        this.defaultButtonNarrationText(o);
    }

    private void updateValue(double mx) {
        value = Mth.clamp((mx - (this.getX() + HANDLE_W / 2.0)) / (this.width - HANDLE_W), 0.0, 1.0);
        onChanged.accept(value);
    }
}
