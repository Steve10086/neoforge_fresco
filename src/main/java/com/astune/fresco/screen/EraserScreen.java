package com.astune.fresco.screen;

import com.astune.painter.registry.ModDataComponents;
import com.astune.fresco.Fresco;
import com.astune.fresco.network.SyncItemStackPayload;
import com.astune.fresco.screen.widget.TexturedButton;
import com.astune.fresco.screen.widget.TexturedSlider;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class EraserScreen extends Screen {

    private static final ResourceLocation TEX_PANEL_BG =
            ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "widget/panel_background");

    private final ItemStack eraserStack;
    private final int slot;

    private static final int WIDGET_W = 150;
    private static final int WIDGET_H = 20;
    private static final double SIZE_MIN = 0.01, SIZE_MAX = 0.5;

    private int panelX, panelY, panelW, panelH;
    private double brushSize;
    private float opacity, feather;

    public EraserScreen(ItemStack eraserStack, int slot) {
        super(Component.translatable("fresco.eraser_screen.title"));
        this.eraserStack = eraserStack;
        this.slot = slot;
        this.brushSize = eraserStack.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.1);
        this.opacity   = eraserStack.getOrDefault(ModDataComponents.OPACITY.get(), 0.5f);
        this.feather   = eraserStack.getOrDefault(ModDataComponents.FEATHER_STRENGTH.get(), 0.3f);
    }

    @Override
    protected void init() {
        int contentW = WIDGET_W + 20;
        int contentH = 150;
        this.panelX = (this.width - contentW) / 2;
        this.panelY = (this.height - contentH) / 2;
        this.panelW = contentW;
        this.panelH = contentH;

        int cx = this.width / 2;
        int y = panelY + 35;

        addRenderableWidget(new TexturedSlider(cx - WIDGET_W / 2, y, WIDGET_W, WIDGET_H,
                Component.translatable("fresco.eraser_screen.size"),
                (brushSize - SIZE_MIN) / (SIZE_MAX - SIZE_MIN),
                val -> { brushSize = SIZE_MIN + val * (SIZE_MAX - SIZE_MIN); save(); },
                v -> String.format("%.3f", SIZE_MIN + v * (SIZE_MAX - SIZE_MIN))));
        y += 30;

        addRenderableWidget(new TexturedSlider(cx - WIDGET_W / 2, y, WIDGET_W, WIDGET_H,
                Component.translatable("fresco.eraser_screen.opacity"),
                opacity,
                val -> { opacity = (float)(double)val; save(); },
                v -> String.format("%.0f%%", v * 100)));
        y += 30;

        addRenderableWidget(new TexturedSlider(cx - WIDGET_W / 2, y, WIDGET_W, WIDGET_H,
                Component.translatable("fresco.eraser_screen.feather"),
                feather,
                val -> { feather = (float)(double)val; save(); },
                v -> String.format("%.0f%%", v * 100)));
        y += 40;

        addRenderableWidget(new TexturedButton(cx - 50, panelY + panelH - 28, 100, WIDGET_H,
                CommonComponents.GUI_DONE, btn -> this.onClose()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        RenderSystem.enableBlend();
        g.blitSprite(TEX_PANEL_BG, panelX, panelY, panelW, panelH);
        g.drawCenteredString(this.font, this.title, this.width / 2, panelY + 6, 0xFFFFFF);
        for (var w : this.renderables) w.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        save();
        sync();
        super.onClose();
    }

    private void save() {
        eraserStack.set(ModDataComponents.BRUSH_SIZE.get(), brushSize);
        eraserStack.set(ModDataComponents.OPACITY.get(), opacity);
        eraserStack.set(ModDataComponents.FEATHER_STRENGTH.get(), feather);
    }

    private void sync() {
        PacketDistributor.sendToServer(new SyncItemStackPayload(slot, eraserStack.copy()));
    }
}
