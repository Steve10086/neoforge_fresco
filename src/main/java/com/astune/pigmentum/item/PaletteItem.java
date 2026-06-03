package com.astune.pigmentum.item;

import com.astune.pigmentum.Pigmentum;
import com.astune.pigmentum.network.SetPaletteColorPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
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

public class PaletteItem extends Item {

    private static boolean sPickingColor = false;

    public PaletteItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @OnlyIn(Dist.CLIENT)
    private static void pickColorClient(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        //if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;

        // 从屏幕中心读取像素颜色
        int centerX = mc.getWindow().getWidth() / 2;
        int centerY = mc.getWindow().getHeight() / 2;

        // 从帧缓冲区读取颜色
        int offset = 3;
        int color = readPixelFromScreen(centerX + offset, centerY + offset);
        //System.out.println("new color: " + color);
        if (color != 0) {
            setCurrentColor(stack, color);
            String hex = String.format("#%06X", color & 0x00FFFFFF);
            Component name = Component.translatable(stack.getItem().getDescriptionId())
                    .append(Component.literal(" (#" + hex + ")"))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color & 0x00FFFFFF)));
            stack.set(DataComponents.CUSTOM_NAME, name);

            if (mc.player != null) {
                Component message = Component.literal("Picked color: " + hex)
                        .withStyle(Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(color & 0x00FFFFFF)));
                mc.gui.setOverlayMessage(message, false);
            }
        }
    }

    /**
     * 将颜色存储到 ItemStack 的 PALETTE_COLOR 组件。
     */
    public static void setCurrentColor(ItemStack stack, int color) {
        stack.set(Pigmentum.PALETTE_COLOR.get(), color);
    }

    /**
     * 从 ItemStack 读取存储的颜色，若未设置则返回 0xFFFFFFFF（白色）。
     */
    public static int getCurrentColor(ItemStack stack) {
        return stack.getOrDefault(Pigmentum.PALETTE_COLOR.get(), 0xFFFFFFFF);
    }

    /**
     * 从屏幕指定坐标读取像素颜色（ARGB格式）。
     */
    @OnlyIn(Dist.CLIENT)
    private static int readPixelFromScreen(int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();

        // 1. 坐标系转换
        int glY = window.getHeight() - y;

        // 2. 准备一个缓冲区来接收像素数据
        IntBuffer buffer = BufferUtils.createIntBuffer(1);

        // 3. 从帧缓冲区读取 1x1 像素
        // 关键参数：位置(x, glY)，尺寸(1,1)，格式(RGBA)，数据类型，存储缓冲区
        GL11.glReadPixels(x, glY, 1, 1, GL12.GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);

        // 4. 格式转换
        int rgba = buffer.get(0);
        int a = 255;
        int r = (rgba) & 0xFF;
        int g = (rgba >> 8) & 0xFF;
        int b = (rgba >> 16) & 0xFF;

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @EventBusSubscriber(modid = "pigmentum", value = Dist.CLIENT)
    public static class CrosshairHandler {
        private static final ResourceLocation CROSSHAIR_ID = ResourceLocation.withDefaultNamespace("crosshair");

        @SubscribeEvent
        public static void onRenderCrosshair(RenderGuiLayerEvent.Pre event) {
            // 检查正在渲染的是否为 CROSSHAIR 覆盖层
            if (event.getName().equals(CROSSHAIR_ID) && sPickingColor) {
                event.setCanceled(true);      // 取消准星渲染
            }
        }
    }

    @EventBusSubscriber(modid = "pigmentum", value = Dist.CLIENT)
    private static class PickColorKeyHandler {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;

            if (!sPickingColor && KEY_PICK_COLOR.isDown()) {
                ItemStack palette = mc.player.getOffhandItem();
                if (palette.getItem() instanceof PaletteItem) {
                    sPickingColor = true;     // 设置标志，准备拦截下一帧的准星渲染
                }
            }

            if (sPickingColor) {
                ItemStack palette = mc.player.getOffhandItem();
                if (palette.getItem() instanceof PaletteItem) {
                    pickColorClient(palette); // 执行吸色
                }
            }


            if (sPickingColor && !KEY_PICK_COLOR.isDown()){
                ItemStack palette = mc.player.getOffhandItem();
                if (palette.getItem() instanceof PaletteItem) {
                    int color = getCurrentColor(palette);
                    PacketDistributor.sendToServer(new SetPaletteColorPayload(color));
                    sPickingColor = false;
                }
            }
        }
    }
}
