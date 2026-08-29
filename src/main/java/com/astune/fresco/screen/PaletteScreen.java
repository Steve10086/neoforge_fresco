package com.astune.fresco.screen;

import com.astune.fresco.Fresco;
import com.astune.fresco.item.PaletteItem;
import com.astune.fresco.network.SyncItemStackPayload;
import com.astune.fresco.screen.widget.ColorBarWidget;
import com.astune.fresco.screen.widget.ColorEditBox;
import com.astune.fresco.screen.widget.ColorPickerMath;
import com.astune.fresco.screen.widget.ColorSquareWidget;
import com.astune.fresco.screen.widget.TexturedButton;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Local HSV color editor for the palette item. */
public final class PaletteScreen extends Screen {
    private static final ResourceLocation TEX_PANEL_BG =
            ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "widget/panel_background");
    private static final ResourceLocation TEX_HUE_TRACK =
            ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "widget/palette_hue");
    private static final ResourceLocation TEX_BRIGHTNESS_TRACK =
            ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "widget/palette_brightness");

    private final ItemStack paletteStack;
    private final int slot;

    private ColorSquareWidget colorSquare;
    private ColorBarWidget hueBar;
    private ColorBarWidget brightnessBar;
    private ColorEditBox hexField;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int squareX;
    private int squareY;
    private int squareSize;
    private int brightnessY;
    private int hexY;

    private double hue;
    private double saturation;
    private double brightness;
    private int color;
    private boolean updatingControls;
    private boolean committed;

    public PaletteScreen(ItemStack stack, int slot) {
        super(Component.translatable("fresco.palette_screen.title"));
        this.paletteStack = stack.copy();
        this.slot = slot;
        this.color = PaletteItem.getCurrentColor(this.paletteStack) | 0xFF000000;
        double[] hsv = ColorPickerMath.toHsv(this.color);
        this.hue = hsv[0];
        this.saturation = hsv[1];
        this.brightness = hsv[2];
    }

    @Override
    protected void init() {
        this.squareSize = Mth.clamp(Math.min(128, Math.min(this.width - 90, this.height - 160)), 32, 128);
        this.panelW = Math.max(180, this.squareSize + 44);
        this.panelH = this.squareSize + 148;
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        this.squareX = this.panelX + 30;
        this.squareY = this.panelY + 30;
        int hueX = this.squareX + this.squareSize + 8;
        this.brightnessY = this.squareY + this.squareSize + 14;
        this.hexY = this.squareY + this.squareSize + 44;

        this.colorSquare = addRenderableWidget(new ColorSquareWidget(
                squareX, squareY, squareSize, hue, saturation, brightness,
                (nextSaturation, nextBrightness) -> {
                    saturation = nextSaturation;
                    brightness = nextBrightness;
                    updateColorFromHsv();
                }));

        this.hueBar = addRenderableWidget(new ColorBarWidget(
                hueX, squareY, 18, squareSize, true, hue,
                TEX_HUE_TRACK,
                nextHue -> {
                    hue = nextHue;
                    colorSquare.setHue(hue);
                    updateColorFromHsv();
                }));

        this.brightnessBar = addRenderableWidget(new ColorBarWidget(
                squareX, brightnessY, squareSize, 16, false, brightness,
                TEX_BRIGHTNESS_TRACK,
                nextBrightness -> {
                    brightness = nextBrightness;
                    colorSquare.setSelection(saturation, brightness);
                    updateColorFromHsv();
                }));

        this.hexField = addRenderableWidget(new ColorEditBox(
                this.font, squareX, this.hexY, (int) (this.panelW * 0.6), 18,
                Component.translatable("fresco.palette_screen.hex")));
        this.hexField.setMaxLength(7);
        this.hexField.setFilter(value -> value != null
                && value.length() <= 7
                && value.matches("#?[0-9a-fA-F]*"));
        this.hexField.setValue(ColorPickerMath.toHex(this.color));
        this.hexField.setResponder(this::updateFromHex);
        this.hexField.setFocused(false);
        updateHexAppearance();

        addRenderableWidget(new TexturedButton(
                this.width / 2 - 50, this.panelY + this.panelH - 28, 100, 20,
                CommonComponents.GUI_DONE, button -> this.onClose()));
    }

    @Override
    protected void setInitialFocus() {
        // The hex field is intentionally opt-in instead of receiving focus on open.
        clearFocus();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        RenderSystem.enableBlend();
        graphics.blitSprite(TEX_PANEL_BG, panelX, panelY, panelW, panelH);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelY + 6, 0xFFFFFFFF);

        graphics.drawString(this.font, Component.translatable("fresco.palette_screen.color"),
                squareX, squareY - 12, 0xFFEEE2C7);
        graphics.drawString(this.font, Component.translatable("fresco.palette_screen.hue"),
                squareX + squareSize + 7, squareY - 12, 0xFFEEE2C7);
        graphics.drawString(this.font, Component.translatable("fresco.palette_screen.brightness"),
                squareX, brightnessY - 11, 0xFFEEE2C7);
        graphics.drawString(this.font, Component.translatable("fresco.palette_screen.hex"),
                squareX, this.hexY - 11, 0xFFEEE2C7);

        for (var renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (!committed) {
            committed = true;
            commit();
            colorSquare.close();
        }
        super.onClose();
    }

    private void updateColorFromHsv() {
        if (updatingControls) return;

        color = ColorPickerMath.fromHsv(hue, saturation, brightness);
        if (brightnessBar != null) {
            brightnessBar.setValue(brightness);
        }
        PaletteItem.setCurrentColor(paletteStack, color);
        updateHexField();
        updateHexAppearance();
    }

    private void updateFromHex(String value) {
        if (updatingControls) return;

        String digits = value.startsWith("#") ? value.substring(1) : value;
        if (digits.length() != 6) return;

        try {
            int nextColor = 0xFF000000 | Integer.parseInt(digits, 16);
            double[] hsv = ColorPickerMath.toHsv(nextColor);
            hue = hsv[0];
            saturation = hsv[1];
            brightness = hsv[2];
            color = nextColor;
            PaletteItem.setCurrentColor(paletteStack, color);
            updatingControls = true;
            colorSquare.setHue(hue);
            colorSquare.setSelection(saturation, brightness);
            hueBar.setValue(hue);
            brightnessBar.setValue(brightness);
            updatingControls = false;
            updateHexAppearance();
        } catch (NumberFormatException ignored) {
            // The edit box accepts partial input while the player is typing.
        }
    }

    private void updateHexField() {
        if (hexField == null) return;
        updatingControls = true;
        hexField.setValue(ColorPickerMath.toHex(color));
        updatingControls = false;
    }

    private void updateHexAppearance() {
        if (hexField == null) return;

        boolean lowBrightness = brightness <= 0.50;
        hexField.setTextColor(color);
        hexField.setTextShadow(!lowBrightness);
    }

    private void commit() {
        if (minecraft == null || minecraft.player == null) return;

        ItemStack updated = paletteStack.copy();
        if (slot >= 0 && slot < minecraft.player.getInventory().getContainerSize()) {
            minecraft.player.getInventory().setItem(slot, updated.copy());
            PacketDistributor.sendToServer(new SyncItemStackPayload(slot, updated));
        }
    }
}
