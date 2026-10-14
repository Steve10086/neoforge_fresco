package com.astune.fresco.client;

import com.astune.fresco.ClientInputState;
import com.astune.fresco.Fresco;
import com.astune.fresco.item.ClothItem;
import com.astune.fresco.item.ScraperItem;
import com.astune.fresco.item.StampItem;
import com.astune.painter.api.IPaintProvider;
import com.astune.painter.network.ItemSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Fresco.MODID, value = Dist.CLIENT)
public final class ClientItemEvents {
    private static boolean clothUseWasDown;
    private static boolean scraperUseWasDown;

    private ClientItemEvents() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        openConfigScreen(event.getEntity(), event.getHand(), event.getItemStack());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (openConfigScreen(event.getEntity(), event.getHand(), event.getItemStack())) {
            cancelBlockInteraction(event);
            return;
        }

        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof IPaintProvider) {
            if (stack.getItem() instanceof StampItem) {
                StampClientCapture.captureBackgroundMode(event);
            }
            cancelBlockInteraction(event);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        boolean useDown = mc.player != null && mc.level != null && mc.options.keyUse.isDown();
        ClientInputState.setUseKeyDown(useDown);
        if (mc.player == null || mc.level == null) return;

        ItemStack mainHand = mc.player.getMainHandItem();
        boolean holdingCloth = mainHand.getItem() instanceof ClothItem;
        if (holdingCloth) {
            if (useDown) {
                clothUseWasDown = true;
            } else if (clothUseWasDown) {
                clothUseWasDown = false;
                PacketDistributor.sendToServer(new ItemSyncPacket(mc.player.getInventory().selected, mainHand));
            }
        } else if (!useDown) {
            clothUseWasDown = false;
        }

        boolean holdingScraper = mainHand.getItem() instanceof ScraperItem;
        if (holdingScraper) {
            if (useDown) {
                scraperUseWasDown = true;
            } else if (scraperUseWasDown) {
                scraperUseWasDown = false;
                ScraperItem.resetStroke();
            }
        } else if (!useDown) {
            scraperUseWasDown = false;
        }
    }

    private static boolean openConfigScreen(Player player, InteractionHand hand, ItemStack stack) {
        if (!player.isShiftKeyDown()) return false;

        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
        Screen screen = switch (stack.getItem()) {
            case com.astune.fresco.item.PaletteItem ignored -> new com.astune.fresco.screen.PaletteScreen(stack, slot);
            case com.astune.fresco.item.CustomPaintbrush ignored -> new com.astune.fresco.screen.PaintbrushScreen(stack, slot);
            case com.astune.fresco.item.SprayCanItem ignored -> new com.astune.fresco.screen.SprayCanScreen(stack, slot);
            case com.astune.fresco.item.EraserItem ignored -> new com.astune.fresco.screen.EraserScreen(stack, slot);
            case ClothItem ignored -> new com.astune.fresco.screen.ClothScreen(stack, slot);
            case ScraperItem ignored -> new com.astune.fresco.screen.ScraperScreen(stack, slot);
            default -> null;
        };
        if (screen == null) return false;
        Minecraft.getInstance().setScreen(screen);
        return true;
    }

    private static void cancelBlockInteraction(PlayerInteractEvent.RightClickBlock event) {
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
