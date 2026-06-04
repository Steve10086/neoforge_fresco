package com.astune.pigmentum.screen.widget;

import com.astune.pigmentum.Pigmentum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class TexturedButton extends AbstractButton {

    private static final WidgetSprites SPRITES = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/button"),
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/button_disabled"),
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/button_highlighted")
    );

    private final OnPress onPress;

    public TexturedButton(int x, int y, int w, int h, Component msg, OnPress onPress) {
        super(x, y, w, h, msg);
        this.onPress = onPress;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        g.blitSprite(SPRITES.get(this.active, this.isHovered()),
                this.getX(), this.getY(), this.getWidth(), this.getHeight());
        int tc = this.active ? 0xFFFFFF : 0xA0A0A0;
        g.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                this.getX() + this.getWidth() / 2,
                this.getY() + (this.getHeight() - 8) / 2,
                tc | Mth.ceil(this.alpha * 255.0F) << 24);
    }

    @Override
    public void onPress() {
        this.onPress.onPress(this);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput o) {
        this.defaultButtonNarrationText(o);
    }

    @FunctionalInterface
    public interface OnPress {
        void onPress(TexturedButton button);
    }
}
