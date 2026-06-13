package com.astune.fresco;

import com.astune.fresco.item.OnRightClickHandler;
import com.astune.painter.api.IPaintProvider;
import com.astune.painter.api.render.CanvasRendererRegistry;
import com.astune.fresco.glow.GlowPixelRenderer;
import com.astune.fresco.item.DyeTooltipComponent;
import com.astune.fresco.item.SprayCanItem;
import com.astune.painter.item.DebugPaintbrush;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mod(value = Fresco.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Fresco.MODID, value = Dist.CLIENT)
public class FrescoClient {
    public FrescoClient(ModContainer container, IEventBus modBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(this::onRegisterTooltipComponents);
    }

    private void onRegisterTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(DyeTooltipComponent.class, dye ->
                new ClientTooltipComponent() {
                    @Override public int getHeight() { return 20; }
                    @Override public int getWidth(Font font) { return 20; }
                    @Override
                    public void renderImage(Font font, int x, int y, GuiGraphics g) {
                        g.renderItem(((DyeTooltipComponent) dye).dye(), x, y + 2);
                    }
                });
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        CanvasRendererRegistry.registerPixelRenderer(new GlowPixelRenderer(), 10);

        ItemProperties.register(Fresco.SPRAY_CAN.get(),
                ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "dirty"),
                (stack, level, entity, seed) -> SprayCanItem.hasDye(stack) ? 1.0F : 0.0F);

        ItemProperties.register(Fresco.STAMP.get(),
                ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "active"),
                (stack, level, entity, seed) ->
                        stack.has(Fresco.STAMP_FACE.get()) ? 1.0F : 0.0F);
        ItemProperties.register(Fresco.STAMP.get(),
                ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "background"),
                (stack, level, entity, seed) ->
                        stack.getOrDefault(Fresco.STAMP_BACKGROUND.get(), false) ? 1.0F : 0.0F);

        ItemProperties.register(Fresco.CLOTH.get(),
                ResourceLocation.fromNamespaceAndPath(Fresco.MODID, "saturation"),
                (stack, level, entity, seed) ->
                        stack.getOrDefault(Fresco.CLOTH_SATURATION.get(), 0) / 100f);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof IPaintProvider) {
            if(stack.getItem() instanceof OnRightClickHandler handler){
                handler.onRightClickedBlock(event);
            }
            // 取消对原方块的交互，只让画笔处理
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onTooltipGather(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SprayCanItem)) return;

        ItemStack dye = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne();
        if (!dye.isEmpty()) {
            event.getTooltipElements().add(Either.right(new DyeTooltipComponent(dye)));
        }
    }
}
