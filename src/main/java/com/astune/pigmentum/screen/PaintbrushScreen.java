package com.astune.pigmentum.screen;

import com.astune.pigmentum.Pigmentum;
import com.astune.pigmentum.item.CustomPaintbrush;
import com.astune.pigmentum.network.SyncItemStackPayload;
import com.astune.pigmentum.screen.widget.TexturedButton;
import com.astune.pigmentum.screen.widget.TexturedSlider;

import com.astune.painter.api.BlendMode;
import com.astune.painter.registry.ModDataComponents;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class PaintbrushScreen extends Screen {

    private static final ResourceLocation TEX_PANEL_BG =
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/panel_background");

    private final ItemStack brushStack;
    private final int slot;

    private static final int BUTTON_W = 150;
    private static final int BUTTON_H = 20;

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
}
