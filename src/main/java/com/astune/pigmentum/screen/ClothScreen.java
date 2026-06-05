package com.astune.pigmentum.screen;

import com.astune.painter.registry.ModDataComponents;
import com.astune.pigmentum.Pigmentum;
import com.astune.pigmentum.network.SyncItemStackPayload;
import com.astune.pigmentum.screen.widget.TexturedButton;
import com.astune.pigmentum.screen.widget.TexturedSlider;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class ClothScreen extends Screen {

    private static final ResourceLocation TEX_PANEL_BG =
            ResourceLocation.fromNamespaceAndPath(Pigmentum.MODID, "widget/panel_background");

    private final ItemStack clothStack;
    private final int slot;

    private static final int WIDGET_W = 150;
    private static final int WIDGET_H = 20;
    private static final double SIZE_MIN = 0.01, SIZE_MAX = 0.5;

    private int panelX, panelY, panelW, panelH;
    private double brushSize;
    private float opacity, feather;
    private boolean needsSync = false;

    public ClothScreen(ItemStack clothStack, int slot) {
        super(Component.translatable("pigmentum.cloth_screen.title"));
        this.clothStack = clothStack;
        this.slot = slot;
        this.brushSize = clothStack.getOrDefault(ModDataComponents.BRUSH_SIZE.get(), 0.12);
        this.opacity = clothStack.getOrDefault(ModDataComponents.OPACITY.get(), 0.8f);
        this.feather = clothStack.getOrDefault(ModDataComponents.FEATHER_STRENGTH.get(), 1.0f);
    }

    @Override
    protected void init() {
        int contentW = WIDGET_W + 20;
        int contentH = 180;
        this.panelX = (this.width - contentW) / 2;
        this.panelY = (this.height - contentH) / 2;
        this.panelW = contentW;
        this.panelH = contentH;

        int cx = this.width / 2;
        int y = panelY + 35;

        addRenderableWidget(new TexturedSlider(cx - WIDGET_W / 2, y, WIDGET_W, WIDGET_H,
                Component.translatable("pigmentum.cloth_screen.size"),
                (brushSize - SIZE_MIN) / (SIZE_MAX - SIZE_MIN),
                val -> { brushSize = SIZE_MIN + val * (SIZE_MAX - SIZE_MIN); save(); },
                v -> String.format("%.3f", SIZE_MIN + v * (SIZE_MAX - SIZE_MIN))));
        y += 30;

        addRenderableWidget(new TexturedSlider(cx - WIDGET_W / 2, y, WIDGET_W, WIDGET_H,
                Component.translatable("pigmentum.cloth_screen.opacity"),
                opacity,
                val -> { opacity = (float)(double)val; save(); },
                v -> String.format("%.0f%%", v * 100)));
        y += 30;

        addRenderableWidget(new TexturedSlider(cx - WIDGET_W / 2, y, WIDGET_W, WIDGET_H,
                Component.translatable("pigmentum.cloth_screen.feather"),
                feather,
                val -> { feather = (float)(double)val; save(); },
                v -> String.format("%.0f%%", v * 100)));
        y += 30;

        addRenderableWidget(new TexturedButton(cx - WIDGET_W / 2, y, WIDGET_W, WIDGET_H,
                Component.translatable("pigmentum.cloth_screen.clean"),
                btn -> {
                    clothStack.set(Pigmentum.CLOTH_TINT.get(), 0xFFFFFFFF);
                    clothStack.set(Pigmentum.CLOTH_SATURATION.get(), 0);
                    needsSync = true;
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.displayClientMessage(
                                Component.translatable("item.pigmentum.cloth.reset"), true);
                    }
                }));
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
        clothStack.set(ModDataComponents.BRUSH_SIZE.get(), brushSize);
        clothStack.set(ModDataComponents.OPACITY.get(), opacity);
        clothStack.set(ModDataComponents.FEATHER_STRENGTH.get(), feather);
    }

    private void sync() {
        PacketDistributor.sendToServer(new SyncItemStackPayload(slot, clothStack.copy()));
    }

}
