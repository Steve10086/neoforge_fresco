package com.astune.fresco.client;

import com.astune.fresco.Fresco;
import com.astune.fresco.item.PaletteItem;
import com.astune.fresco.network.SetPaletteColorPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.IntBuffer;

import static com.astune.painter.client.ClientSetup.KEY_PICK_COLOR;

@EventBusSubscriber(modid = Fresco.MODID, value = Dist.CLIENT)
public final class PaletteClientHandler {
    private static final ResourceLocation CROSSHAIR_ID = ResourceLocation.withDefaultNamespace("crosshair");
    private static boolean pickingColor;

    private PaletteClientHandler() {}

    @SubscribeEvent
    public static void onRenderCrosshair(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(CROSSHAIR_ID) && pickingColor) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (!pickingColor && KEY_PICK_COLOR.isDown()) {
            ItemStack palette = mc.player.getOffhandItem();
            if (palette.getItem() instanceof PaletteItem) {
                pickingColor = true;
            }
        }

        if (pickingColor) {
            ItemStack palette = mc.player.getOffhandItem();
            if (palette.getItem() instanceof PaletteItem) {
                pickColorClient(palette);
            }
        }

        if (pickingColor && !KEY_PICK_COLOR.isDown()) {
            ItemStack palette = mc.player.getOffhandItem();
            if (palette.getItem() instanceof PaletteItem) {
                int color = PaletteItem.getCurrentColor(palette);
                PacketDistributor.sendToServer(new SetPaletteColorPayload(color));
            }
            pickingColor = false;
        }
    }

    private static void pickColorClient(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        int centerX = mc.getWindow().getWidth() / 2;
        int centerY = mc.getWindow().getHeight() / 2;
        int color = readPixelFromScreen(centerX + 3, centerY + 3);
        if (color == 0) return;

        PaletteItem.setCurrentColor(stack, color);
        String hex = String.format("#%06X", color & 0x00FFFFFF);
        if (mc.player != null) {
            Component message = Component.literal("Picked color: " + hex)
                    .withStyle(Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(color & 0x00FFFFFF)));
            mc.gui.setOverlayMessage(message, false);
        }
    }

    private static int readPixelFromScreen(int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        int glY = window.getHeight() - y;
        IntBuffer buffer = BufferUtils.createIntBuffer(1);
        GL11.glReadPixels(x, glY, 1, 1, GL12.GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);

        int rgba = buffer.get(0);
        int a = 255;
        int r = rgba & 0xFF;
        int g = (rgba >> 8) & 0xFF;
        int b = (rgba >> 16) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
