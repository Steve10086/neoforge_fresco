package com.astune.pigmentum.screen;

import com.astune.painter.registry.ModDataComponents;
import com.astune.pigmentum.Pigmentum;
import com.astune.pigmentum.item.OffhandColorResolver;
import com.astune.pigmentum.network.SyncItemStackPayload;
import com.astune.pigmentum.screen.widget.TexturedButton;
import com.astune.pigmentum.screen.widget.TexturedSlider;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class SprayCanScreen extends Screen {

    private static final ResourceLocation TEX_PANEL_BG =
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/panel_background");

    private final ItemStack stack;
    private final int slot;

    private static final int WIDGET_W = 150;
    private static final int WIDGET_H = 20;

    private int panelX, panelY, panelW, panelH;

    private double brushSize, density;
    private float feather;
    private float opacity;

    public SprayCanScreen(ItemStack stack, int slot) {
        super(Component.translatable("pigmentum.spray_can_screen.title"));
        this.stack = stack;
        this.slot = slot;
        this.brushSize = stack.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.5);
        this.feather = stack.getOrDefault(ModDataComponents.FEATHER_STRENGTH.get(), 0.7f);
        this.density = stack.getOrDefault(Pigmentum.SPRAY_DENSITY.get(), 0.5);
        this.opacity = stack.getOrDefault(ModDataComponents.OPACITY.get(), 0.5f);
    }

    @Override
    protected void init() {
        int contentW = WIDGET_W + 20;
        int contentH = 225;
        this.panelX = (this.width - contentW) / 2;
        this.panelY = (this.height - contentH) / 2;
        this.panelW = contentW;
        this.panelH = contentH;

        int cx = this.width / 2;
        int y = panelY + 35;

        addSlider(cx, y, "pigmentum.spray_can_screen.size",
                (brushSize - 0.05) / (1.5 - 0.05),
                val -> { brushSize = 0.05 + val * (1.5 - 0.05); save(); });
        y += 30;

        addSlider(cx, y, "pigmentum.spray_can_screen.feather",
                (feather - 0.2) / (1.0 - 0.2),
                val -> { feather = (float)(0.2 + val * (1.0 - 0.2)); save(); });
        y += 30;

        addSlider(cx, y, "pigmentum.spray_can_screen.density",
                (density - 0.01) / (1.0 - 0.01),
                val -> { density = 0.01 + val * (1.0 - 0.01); save(); });
        y += 30;

        addSlider(cx, y, "pigmentum.spray_can_screen.opacity",
                opacity,
                val -> { opacity = (float)(double)val; save(); });
        y += 40;

        addRenderableWidget(new TexturedButton(cx - 50, panelY + panelH - 28, 100, WIDGET_H,
                CommonComponents.GUI_DONE, btn -> this.onClose()));
    }

    private void addSlider(int cx, int y, String key, double init, java.util.function.Consumer<Double> cb) {
        addRenderableWidget(new TexturedSlider(cx - WIDGET_W / 2, y, WIDGET_W, WIDGET_H,
                Component.translatable(key), init, cb));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);

        RenderSystem.enableBlend();
        g.blitSprite(TEX_PANEL_BG, panelX, panelY, panelW, panelH);
        g.drawCenteredString(this.font, this.title, this.width / 2, panelY + 6, 0xFFFFFF);

        for (var w : this.renderables) w.render(g, mouseX, mouseY, partialTick);

        int color = OffhandColorResolver.resolve(
                this.minecraft != null ? this.minecraft.player : null);
        String hex = String.format("#%06X", color & 0x00FFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("pigmentum.paintbrush_screen.current_color", hex),
                this.width / 2, panelY + panelH - 10, color & 0x00FFFFFF);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        save();
        PacketDistributor.sendToServer(new SyncItemStackPayload(slot, stack.copy()));
        super.onClose();
    }

    private void save() {
        stack.set(ModDataComponents.BRUSH_SIZE.get(), brushSize);
        stack.set(ModDataComponents.FEATHER_STRENGTH.get(), feather);
        stack.set(Pigmentum.SPRAY_DENSITY.get(), density);
        stack.set(ModDataComponents.OPACITY.get(), opacity);
    }
}
