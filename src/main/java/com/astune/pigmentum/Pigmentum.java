package com.astune.pigmentum;

import com.astune.painter.api.imageProvider.CanvasImageProviderRegistry;
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
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
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
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(Pigmentum.MODID)
public class Pigmentum {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "pigmentum";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "pigmentum" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "pigmentum" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "pigmentum" namespace
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

    // ---- Blocks ----

    // Creates a new Block with the id "pigmentum:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "pigmentum:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // ---- Items ----

    /** Palette - right-click to pick the color under the crosshair. */
    public static final DeferredItem<Item> PALETTE = ITEMS.register("palette", PaletteItem::new);

    /** Custom paintbrush with circular pattern and offhand color source. */
    public static final DeferredItem<Item> CUSTOM_PAINTBRUSH = ITEMS.register("custom_paintbrush", CustomPaintbrush::new);

    /** Glow paintbrush — inherits CustomPaintbrush and writes glow effect layer. */
    public static final DeferredItem<Item> GLOW_PAINTBRUSH = ITEMS.register("glow_paintbrush", GlowPaintbrush::new);

    // Creates a new food item with the id "pigmentum:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // ---- Creative Tabs ----

    // Creates a creative tab with the id "pigmentum:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.pigmentum")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> PALETTE.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(PALETTE.get()); // Add the palette to the tab
                output.accept(CUSTOM_PAINTBRUSH.get()); // Add the custom paintbrush to the tab
                output.accept(GLOW_PAINTBRUSH.get()); // Add the glow paintbrush to the tab
                output.accept(EXAMPLE_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Pigmentum(IEventBus modEventBus, ModContainer modContainer) {
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
        // Note that this is necessary if and only if we want *this* class (Pigmentum) to respond directly to events.
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
                        if (ItemStack.isSameItem(serverStack, clientStack)) {
                            serverStack.applyComponents(clientStack.getComponentsPatch());
                        }
                    }
                }
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Register glow image provider for the night-glow pipeline
        CanvasImageProviderRegistry.register(new GlowImageProvider(), 1);
        LOGGER.info("GlowImageProvider registered");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
