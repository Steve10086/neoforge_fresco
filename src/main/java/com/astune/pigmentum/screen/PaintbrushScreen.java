package com.astune.pigmentum.screen;

import com.astune.pigmentum.Pigmentum;
import com.astune.pigmentum.item.CustomPaintbrush;
import com.astune.pigmentum.network.SyncItemStackPayload;

import com.astune.painter.api.BlendMode;
import com.astune.painter.registry.ModDataComponents;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class PaintbrushScreen extends Screen {

    private static final WidgetSprites BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/button"),
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/button_disabled"),
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/button_highlighted")
    );
    private static final ResourceLocation TEX_SLIDER =
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/slider");
    private static final ResourceLocation TEX_SLIDER_HIGHLIGHTED =
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/slider_highlighted");
    private static final ResourceLocation TEX_SLIDER_HANDLE =
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/slider_handle");
    private static final ResourceLocation TEX_SLIDER_HANDLE_HIGHLIGHTED =
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/slider_handle_highlighted");
    private static final ResourceLocation TEX_PANEL_BG =
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/panel_background");

    private final ItemStack brushStack;
    private final int slot;

    private static final int BUTTON_W = 150;
    private static final int BUTTON_H = 20;
    private static final int HANDLE_W = 8;

    private int panelX, panelY, panelW, panelH;

    private double brushSize;
    private float opacity;
    private BlendMode blendMode;

    public PaintbrushScreen(ItemStack brushStack, int slot) {
        super(Component.translatable("pigmentum.paintbrush_screen.title"));
        this.brushStack = brushStack;
        this.slot = slot;
        this.brushSize = brushStack.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.06);
        this.opacity = brushStack.getOrDefault(ModDataComponents.OPACITY.get(), 1.0f);
        String modeStr = brushStack.getOrDefault(ModDataComponents.BLEND_MODE.get(), BlendMode.OVERWRITE.name());
        try {
            this.blendMode = BlendMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            this.blendMode = BlendMode.OVERWRITE;
        }
    }

    private static String blendModeKey(BlendMode mode) {
        return "painter.blend_mode." + mode.name().toLowerCase();
    }

    @Override
    protected void init() {
        int contentW = BUTTON_W + 20;
        int contentH = 190;
        this.panelX = (this.width - contentW) / 2;
        this.panelY = (this.height - contentH) / 2;
        this.panelW = contentW;
        this.panelH = contentH;

        int cx = this.width / 2;
        int y = panelY + 35;

        addRenderableWidget(new TexturedSlider(cx - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Component.translatable("pigmentum.paintbrush_screen.size"),
                (brushSize - 0.01) / (0.25 - 0.01),
                val -> { brushSize = 0.01 + val * (0.25 - 0.01); saveParameters(); }));
        y += 30;

        addRenderableWidget(new TexturedSlider(cx - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Component.translatable("pigmentum.paintbrush_screen.opacity"),
                opacity,
                val -> { opacity = (float)(double)val; saveParameters(); }));
        y += 30;

        var modes = BlendMode.values();
        addRenderableWidget(new TexturedButton(cx - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Component.translatable("pigmentum.paintbrush_screen.blend",
                        Component.translatable(blendModeKey(blendMode))),
                btn -> {
                    int next = (blendMode.ordinal() + 1) % modes.length;
                    blendMode = modes[next];
                    btn.setMessage(Component.translatable("pigmentum.paintbrush_screen.blend",
                            Component.translatable(blendModeKey(blendMode))));
                    saveParameters();
                }));
        y += 40;

        addRenderableWidget(new TexturedButton(cx - 50, panelY + panelH - 28, 100, BUTTON_H,
                CommonComponents.GUI_DONE, btn -> this.onClose()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);

        RenderSystem.enableBlend();
        g.blitSprite(TEX_PANEL_BG, panelX, panelY, panelW, panelH);
        g.drawCenteredString(this.font, this.title, this.width / 2, panelY + 6, 0xFFFFFF);

        for (var w : this.renderables) w.render(g, mouseX, mouseY, partialTick);

        int color = CustomPaintbrush.resolveOffhandColor(
                this.minecraft != null ? this.minecraft.player : null);
        String hex = String.format("#%06X", color & 0x00FFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("pigmentum.paintbrush_screen.current_color", hex),
                this.width / 2, panelY + panelH - 10, color & 0x00FFFFFF);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        saveParameters();
        PacketDistributor.sendToServer(new SyncItemStackPayload(slot, brushStack.copy()));
        super.onClose();
    }

    private void saveParameters() {
        brushStack.set(ModDataComponents.BRUSH_SIZE.get(), brushSize);
        brushStack.set(ModDataComponents.OPACITY.get(), opacity);
        brushStack.set(ModDataComponents.BLEND_MODE.get(), blendMode.name());
    }

    // ── TexturedButton ────────────────────────────────────────

    private static class TexturedButton extends AbstractButton {
        private final OnPress onPress;
        TexturedButton(int x, int y, int w, int h, Component msg, OnPress onPress) {
            super(x, y, w, h, msg); this.onPress = onPress;
        }
        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            g.blitSprite(BUTTON_SPRITES.get(this.active, this.isHovered()),
                    this.getX(), this.getY(), this.getWidth(), this.getHeight());
            int tc = this.active ? 0xFFFFFF : 0xA0A0A0;
            g.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                    this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2,
                    tc | Mth.ceil(this.alpha * 255.0F) << 24);
        }

        @Override
        public void onPress() { this.onPress.onPress(this); }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput o) { this.defaultButtonNarrationText(o); }
    }

    @FunctionalInterface
    private interface OnPress { void onPress(TexturedButton button); }

    // ── TexturedSlider ────────────────────────────────────────

    private static class TexturedSlider extends AbstractWidget {
        private double value;
        private final java.util.function.Consumer<Double> onChanged;
        TexturedSlider(int x, int y, int w, int h, Component label, double init,
                       java.util.function.Consumer<Double> cb) {
            super(x, y, w, h, label); this.value = init; this.onChanged = cb;
        }
        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            Component display = Component.literal(
                    this.getMessage().getString() + ": " + String.format("%.0f%%", value * 100));
            int labelW = Minecraft.getInstance().font.width(display);
            g.drawString(Minecraft.getInstance().font, display,
                    this.getX() + (this.getWidth() - labelW) / 2, this.getY() - 12, 0xFFCCCCCC);

            g.blitSprite(this.isFocused() ? TEX_SLIDER_HIGHLIGHTED : TEX_SLIDER,
                    this.getX(), this.getY(), this.getWidth(), this.getHeight());
            g.blitSprite(this.isHovered ? TEX_SLIDER_HANDLE_HIGHLIGHTED : TEX_SLIDER_HANDLE,
                    this.getX() + (int)(this.value * (this.width - HANDLE_W)),
                    this.getY(), HANDLE_W, this.getHeight());
        }
        @Override public void onClick(double mx, double my) { updateValue(mx); }
        @Override protected void onDrag(double mx, double my, double dx, double dy) { updateValue(mx); }
        @Override protected void updateWidgetNarration(NarrationElementOutput o) { this.defaultButtonNarrationText(o); }
        private void updateValue(double mx) {
            value = Mth.clamp((mx - (this.getX() + HANDLE_W / 2.0)) / (this.width - HANDLE_W), 0.0, 1.0);
            onChanged.accept(value);
        }
    }
}
