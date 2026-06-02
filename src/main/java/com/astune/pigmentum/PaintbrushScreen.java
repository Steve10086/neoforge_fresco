package com.astune.pigmentum;

import com.astune.painter.api.BlendMode;
import com.astune.painter.registry.ModDataComponents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 画笔参数调整屏幕，直接读写 ItemStack 上的 data components。
 */
public class PaintbrushScreen extends Screen {

    private final ItemStack brushStack;

    private static final int SLIDER_WIDTH = 150;
    private static final int SLIDER_HEIGHT = 20;
    private static final int LEFT_MARGIN = 30;

    private double brushSize;
    private float opacity;
    private BlendMode blendMode;

    // 按钮/滑块用到的去 bounce
    private int sliderCount = 0;

    public PaintbrushScreen(ItemStack brushStack) {
        super(Component.translatable("pigmentum.paintbrush_screen.title"));
        this.brushStack = brushStack;
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
        int centerX = this.width / 2;
        int y = 40;

        // ── 画笔大小滑块 ──
        addRenderableWidget(new BrushSlider(
                centerX - SLIDER_WIDTH / 2, y, SLIDER_WIDTH, SLIDER_HEIGHT,
                Component.translatable("pigmentum.paintbrush_screen.size"),
                (brushSize - 0.01) / (0.25 - 0.01),  // normalized 0..1
                val -> {
                    brushSize = 0.01 + val * (1.0 - 0.01);
                    saveParameters();
                }
        ));
        y += 30;

        // ── 不透明度滑块 ──
        addRenderableWidget(new BrushSlider(
                centerX - SLIDER_WIDTH / 2, y, SLIDER_WIDTH, SLIDER_HEIGHT,
                Component.translatable("pigmentum.paintbrush_screen.opacity"),
                opacity,  // already 0..1
                val -> {
                    opacity = (float) (double) val;
                    saveParameters();
                }
        ));
        y += 30;

        // ── 混合模式切换 ──
        var modes = BlendMode.values();
        int modeIdx = blendMode.ordinal();
        addRenderableWidget(Button.builder(
                Component.translatable("pigmentum.paintbrush_screen.blend",
                        Component.translatable(blendModeKey(modes[modeIdx]))),
                btn -> {
                    int next = (blendMode.ordinal() + 1) % modes.length;
                    blendMode = modes[next];
                    btn.setMessage(Component.translatable("pigmentum.paintbrush_screen.blend",
                            Component.translatable(blendModeKey(modes[modeIdx]))));
                    saveParameters();
                }
        ).pos(centerX - SLIDER_WIDTH / 2, y).size(SLIDER_WIDTH, SLIDER_HEIGHT).build());
        y += 40;

        // ── 关闭按钮 ──
        addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                btn -> this.onClose()
        ).pos(centerX - 50, y).size(100, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        // 当前值提示
        int color = CustomPaintbrush.resolveOffhandColor(
                this.minecraft != null ? this.minecraft.player : null);
        String hex = String.format("#%06X", color & 0x00FFFFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("pigmentum.paintbrush_screen.current_color", hex),
                this.width / 2, this.height - 30, color & 0x00FFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        saveParameters();
        super.onClose();
    }

    private void saveParameters() {
        brushStack.set(ModDataComponents.BRUSH_SIZE.get(), brushSize);
        brushStack.set(ModDataComponents.OPACITY.get(), opacity);
        brushStack.set(ModDataComponents.BLEND_MODE.get(), blendMode.name());
    }

    // ── 滑动条 ───────────────────────────────────────────────

    private class BrushSlider extends AbstractWidget {
        private double value; // 0..1
        private final Component label;
        private final java.util.function.Consumer<Double> onChanged;

        BrushSlider(int x, int y, int w, int h, Component label, double initial, java.util.function.Consumer<Double> cb) {
            super(x, y, w, h, label);
            this.label = label;
            this.value = initial;
            this.onChanged = cb;
            setTooltip(Tooltip.create(Component.literal(String.format("%.0f%%", initial * 100))));
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
            // 背景条
            int barY = this.getY() + this.height / 2 - 1;
            guiGraphics.fill(this.getX(), barY, this.getX() + this.width, barY + 2, 0xFF555555);
            // 填充条
            int fillEnd = this.getX() + (int) (this.width * value);
            guiGraphics.fill(this.getX(), barY, fillEnd, barY + 2, 0xFF00AA00);
            // 把手
            int handleX = fillEnd - 2;
            guiGraphics.fill(handleX, this.getY(), handleX + 4, this.getY() + this.height, 0xFFFFFFFF);

            // 标签
            Component display = Component.literal(label.getString() + ": " + String.format("%.0f%%", value * 100));
            guiGraphics.drawString(PaintbrushScreen.this.font, display, this.getX(), this.getY() - 12, 0xCCCCCC);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            updateValue(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            updateValue(mouseX);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }

        private void updateValue(double mouseX) {
            value = Math.clamp((mouseX - this.getX()) / this.width, 0.0, 1.0);
            setTooltip(Tooltip.create(Component.literal(String.format("%.0f%%", value * 100))));
            onChanged.accept(value);
        }
    }
}
