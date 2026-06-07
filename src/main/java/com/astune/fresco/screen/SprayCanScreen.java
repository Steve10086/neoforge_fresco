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

public class SprayCanScreen extends Screen {

    private static final ResourceLocation TEX_PANEL_BG =
            ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "widget/panel_background");

    private final ItemStack stack;
    private final int slot;

    private static final int WIDGET_W = 150;
    private static final int WIDGET_H = 20;

    private int panelX, panelY, panelW, panelH;

    private double brushSize, density;
    private float feather;
    private float opacity;

    // 各参数的实际值范围
    private static final double SIZE_MIN = 0.05, SIZE_MAX = 1.0;
    private static final double FEATHER_MIN = 0.2, FEATHER_MAX = 1.0;
    private static final double DENSITY_MIN = 0.01, DENSITY_MAX = 1.0;

    public SprayCanScreen(ItemStack stack, int slot) {
        super(Component.translatable("fresco.spray_can_screen.title"));
        this.stack = stack;
        this.slot = slot;
        this.brushSize = stack.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.5);
        this.feather = stack.getOrDefault(ModDataComponents.FEATHER_STRENGTH.get(), 0.7f);
        this.density = stack.getOrDefault(Fresco.SPRAY_DENSITY.get(), 0.5);
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

        // 大小
        addRenderableWidget(new TexturedSlider(cx - WIDGET_W / 2, y, WIDGET_W, WIDGET_H,
                Component.translatable("fresco.spray_can_screen.size"),
                (brushSize - SIZE_MIN) / (SIZE_MAX - SIZE_MIN),
                val -> { brushSize = SIZE_MIN + val * (SIZE_MAX - SIZE_MIN); save(); },
                v -> String.format("%.3f", SIZE_MIN + v * (SIZE_MAX - SIZE_MIN))));
        y += 30;

        // 羽化
        addRenderableWidget(new TexturedSlider(cx - WIDGET_W / 2, y, WIDGET_W, WIDGET_H,
                Component.translatable("fresco.spray_can_screen.feather"),
                (feather - FEATHER_MIN) / (FEATHER_MAX - FEATHER_MIN),
                val -> { feather = (float) (FEATHER_MIN + val * (FEATHER_MAX - FEATHER_MIN)); save(); },
                v -> String.format("%.0f%%", (FEATHER_MIN + v * (FEATHER_MAX - FEATHER_MIN)) * 100)));
        y += 30;

        // 密度
        addRenderableWidget(new TexturedSlider(cx - WIDGET_W / 2, y, WIDGET_W, WIDGET_H,
                Component.translatable("fresco.spray_can_screen.density"),
                (density - DENSITY_MIN) / (DENSITY_MAX - DENSITY_MIN),
                val -> { density = DENSITY_MIN + val * (DENSITY_MAX - DENSITY_MIN); save(); },
                v -> String.format("%.0f%%", (DENSITY_MIN + v * (DENSITY_MAX - DENSITY_MIN)) * 100)));
        y += 30;

        // 不透明度（0~1，直接映射）
        addRenderableWidget(new TexturedSlider(cx - WIDGET_W / 2, y, WIDGET_W, WIDGET_H,
                Component.translatable("fresco.spray_can_screen.opacity"),
                opacity,
                val -> { opacity = (float)(double)val; save(); },
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
        PacketDistributor.sendToServer(new SyncItemStackPayload(slot, stack.copy()));
        super.onClose();
    }

    private void save() {
        stack.set(ModDataComponents.BRUSH_SIZE.get(), brushSize);
        stack.set(ModDataComponents.FEATHER_STRENGTH.get(), feather);
        stack.set(Fresco.SPRAY_DENSITY.get(), density);
        stack.set(ModDataComponents.OPACITY.get(), opacity);
    }
}
