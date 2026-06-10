package com.astune.fresco;

import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.imageProvider.CanvasImageProviderRegistry;
import com.astune.fresco.glow.GlowImageProvider;
import com.astune.fresco.item.ClothItem;
import com.astune.fresco.item.CustomPaintbrush;
import com.astune.fresco.item.EraserItem;
import com.astune.fresco.item.GlowPaintbrush;
import com.astune.fresco.item.PaletteItem;
import com.astune.fresco.item.SprayCanItem;
import com.astune.fresco.item.StampItem;
import com.astune.fresco.network.SetPaletteColorPayload;
import com.astune.fresco.network.SyncItemStackPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import com.mojang.serialization.Codec;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(Fresco.MODID)
public class Fresco {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "fresco";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "fresco" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "fresco" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "fresco" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    // Create a Deferred Register to hold DataComponentTypes
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);

    // ---- Data Components ----

    /** Stores the ARGB color picked by the palette. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PALETTE_COLOR = DATA_COMPONENTS.register(
            "palette_color",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build()
    );

    /** Stores the stamped CanvasFace (pixel data + dimensions) for the stamp item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CanvasFace>> STAMP_FACE = DATA_COMPONENTS.register(
            "stamp_face",
            () -> DataComponentType.<CanvasFace>builder()
                    .persistent(CanvasFace.CODEC)
                    .networkSynchronized(CanvasFace.STREAM_CODEC)
                    .build()
    );

    /** Spray density (0~1) — probability that a pixel is placed. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Double>> SPRAY_DENSITY = DATA_COMPONENTS.register(
            "spray_density",
            () -> DataComponentType.<Double>builder()
                    .persistent(Codec.DOUBLE)
                    .networkSynchronized(ByteBufCodecs.DOUBLE)
                    .build()
    );

    /** Spray tint (ARGB) — dye color stored on spray can; 0 = no tint. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SPRAY_TINT = DATA_COMPONENTS.register(
            "spray_tint",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build()
    );

    /** Cloth accumulated tint (ARGB). Starts white, blends with painted canvas pixels. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CLOTH_TINT = DATA_COMPONENTS.register(
            "cloth_tint",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build()
    );

    /** Cloth saturation (0~100). Increments per paint tick, normalized to tint multiplier. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CLOTH_SATURATION = DATA_COMPONENTS.register(
            "cloth_saturation",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build()
    );

    /** Stamp mode: false=default (canvas only), true=background (block texture + canvas). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> STAMP_BACKGROUND = DATA_COMPONENTS.register(
            "stamp_background",
            () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build()
    );

    // ---- Blocks ----


    // ---- Items ----

    /** Palette - right-click to pick the color under the crosshair. */
    public static final DeferredItem<Item> PALETTE = ITEMS.register("palette", PaletteItem::new);

    /** Custom paintbrush with circular pattern and offhand color source. */
    public static final DeferredItem<Item> CUSTOM_PAINTBRUSH = ITEMS.register("custom_paintbrush", CustomPaintbrush::new);

    /** Glow paintbrush — inherits CustomPaintbrush and writes glow effect layer. */
    public static final DeferredItem<Item> GLOW_PAINTBRUSH = ITEMS.register("glow_paintbrush", GlowPaintbrush::new);

    /** Stamp — copies pixel data from canvas and places as one-shot pattern. */
    public static final DeferredItem<Item> STAMP = ITEMS.register("stamp", StampItem::new);

    /** Spray can — circular spray with configurable density, fixed ADD blend mode. */
    public static final DeferredItem<Item> SPRAY_CAN = ITEMS.register("spray_can", SprayCanItem::new);

    /** Cloth — blur tool that blends surrounding canvas pixels. */
    public static final DeferredItem<Item> CLOTH = ITEMS.register("cloth", ClothItem::new);

    /** Eraser — reduces opacity of painted pixels with circular pattern. */
    public static final DeferredItem<Item> ERASER = ITEMS.register("eraser", EraserItem::new);

    // ---- Creative Tabs ----

    // Creates a creative tab with the id "fresco:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.fresco")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> PALETTE.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(PALETTE.get()); // Add the palette to the tab
                output.accept(CUSTOM_PAINTBRUSH.get()); // Add the custom paintbrush to the tab
                output.accept(GLOW_PAINTBRUSH.get()); // Add the glow paintbrush to the tab
                output.accept(STAMP.get()); // Add the stamp to the tab
                output.accept(SPRAY_CAN.get()); // Add the spray can to the tab
                output.accept(CLOTH.get()); // Add the cloth to the tab
                output.accept(ERASER.get()); // Add the eraser to the tab
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Fresco(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so data components get registered
        DATA_COMPONENTS.register(modEventBus);

        // Register networking payloads
        modEventBus.addListener(this::registerNetworking);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (fresco) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerNetworking(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MODID);

        // Palette color sync
        registrar.playToServer(
                SetPaletteColorPayload.TYPE,
                SetPaletteColorPayload.STREAM_CODEC,
                (payload, context) -> {
                    var player = context.player();
                    ItemStack stack = player.getOffhandItem();
                    if (stack.getItem() instanceof PaletteItem) {
                        int color = payload.color();
                        int colorRgb = color & 0x00FFFFFF;
                        PaletteItem.setCurrentColor(stack, color);
                        String hex = String.format("#%06X", colorRgb);
                        Component name = Component.translatable(stack.getItem().getDescriptionId())
                                .append(Component.literal(" (" + hex + ")"))
                                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(colorRgb)));
                        stack.set(DataComponents.CUSTOM_NAME, name);
                    }
                }
        );

        // Generic item stack sync (used by PaintbrushScreen and other config screens)
        registrar.playToServer(
                SyncItemStackPayload.TYPE,
                SyncItemStackPayload.STREAM_CODEC,
                (payload, context) -> {
                    var player = context.player();
                    int slot = payload.slot();
                    ItemStack clientStack = payload.stack();
                    // 仅当槽位物品类型一致时才更新（安全检查）
                    if (slot >= 0 && slot < player.getInventory().getContainerSize()) {
                        ItemStack serverStack = player.getInventory().getItem(slot);
                        if (!ItemStack.isSameItem(serverStack, clientStack)) return;
                        serverStack.applyComponents(clientStack.getComponentsPatch());
                        // ClothItem tint/sat may be filtered from patch if equal to default; copy explicitly
                        if (clientStack.getItem() instanceof com.astune.fresco.item.ClothItem) {
                            serverStack.set(CLOTH_TINT.get(), clientStack.getOrDefault(CLOTH_TINT.get(), 0));
                            serverStack.set(CLOTH_SATURATION.get(), clientStack.getOrDefault(CLOTH_SATURATION.get(), 0));
                        }
                    }
                }
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Register glow image provider for the night-glow pipeline
        CanvasImageProviderRegistry.register(new GlowImageProvider(), 1);
        LOGGER.info("GlowImageProvider registered");
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
