package jp.houlab.mochidsuki.medicalsystemcore;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.medicalsystemcore.block.HeadsideMonitorBlock;
import jp.houlab.mochidsuki.medicalsystemcore.block.DefibrillatorBlock;
import jp.houlab.mochidsuki.medicalsystemcore.block.StretcherBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.HeadsideMonitorBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.DefibrillatorBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.IVStandBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.StretcherBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.capability.IPlayerMedicalData;
import jp.houlab.mochidsuki.medicalsystemcore.client.PackColor;
import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.StretcherPlayerRenderer;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import jp.houlab.mochidsuki.medicalsystemcore.item.*;
import jp.houlab.mochidsuki.medicalsystemcore.menu.IVStandMenu;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import jp.houlab.mochidsuki.medicalsystemcore.block.IVStandBlock;
import jp.houlab.mochidsuki.medicalsystemcore.effect.TransfusionEffect;
import jp.houlab.mochidsuki.medicalsystemcore.effect.BandageEffect;
import jp.houlab.mochidsuki.medicalsystemcore.effect.AdrenalineEffect;
import jp.houlab.mochidsuki.medicalsystemcore.effect.*;
import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity.DefibrillatorBlockEntityRenderer;
import jp.houlab.mochidsuki.medicalsystemcore.client.screen.IVStandScreen;
import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity.HeadsideMonitorBlockEntityRenderer;
import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity.IVStandBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Medicalsystemcore.MODID)
public class Medicalsystemcore {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "medicalsystemcore";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "medicalsystemcore" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "medicalsystemcore" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "medicalsystemcore" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

    //Menu
    public static final RegistryObject<MenuType<IVStandMenu>> IV_STAND_MENU =
            MENU_TYPES.register("iv_stand_menu", () -> IForgeMenuType.create(IVStandMenu::new));


    //Effect
    public static final RegistryObject<MobEffect> TRANSFUSION = MOB_EFFECTS.register("transfusion",
            TransfusionEffect::new);
    public static final RegistryObject<MobEffect> BANDAGE_EFFECT = MOB_EFFECTS.register("bandage_effect",
            BandageEffect::new);
    public static final RegistryObject<MobEffect> ADRENALINE_EFFECT = MOB_EFFECTS.register("adrenaline_effect",
            AdrenalineEffect::new);
    public static final RegistryObject<MobEffect> FIBRINOGEN_EFFECT = MOB_EFFECTS.register("fibrinogen_effect",
            FibrinogenEffect::new);
    public static final RegistryObject<MobEffect> TRANEXAMIC_ACID_EFFECT = MOB_EFFECTS.register("tranexamic_acid_effect",
            TranexamicAcidEffect::new);

    //Entity
    public static final RegistryObject<EntityType<StretcherEntity>> STRETCHER_ENTITY =
            ENTITY_TYPES.register("stretcher_entity", () -> EntityType.Builder.<StretcherEntity>of(
                            StretcherEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("stretcher_entity"));
    //Block
    public static final RegistryObject<Block> IV_STAND = BLOCKS.register("iv_stand", IVStandBlock::new);
    public static final RegistryObject<Block> DEFIBRILLATOR_BLOCK = BLOCKS.register("defibrillator_block",
            () -> new DefibrillatorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));
    public static final RegistryObject<Block> HEAD_SIDE_MONITOR_BLOCK = BLOCKS.register("head_side_monitor",
            () -> new HeadsideMonitorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));
    public static final RegistryObject<Block> STRETCHER_BLOCK = BLOCKS.register("stretcher",
            () -> new StretcherBlock(BlockBehaviour.Properties.copy(Blocks.WHITE_WOOL)));



    //BlockEntity
    public static final RegistryObject<BlockEntityType<IVStandBlockEntity>> IV_STAND_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("iv_stand_block_entity", () ->
                    BlockEntityType.Builder.of(IVStandBlockEntity::new, IV_STAND.get()).build(null));

    public static final RegistryObject<BlockEntityType<DefibrillatorBlockEntity>> DEFIBRILLATOR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("defibrillator_block_entity", () ->
                    BlockEntityType.Builder.of(DefibrillatorBlockEntity::new, DEFIBRILLATOR_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<HeadsideMonitorBlockEntity>> HEAD_SIDE_MONITOR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("head_side_monitor_block_entity", () ->
                    BlockEntityType.Builder.of(HeadsideMonitorBlockEntity::new, HEAD_SIDE_MONITOR_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<StretcherBlockEntity>> STRETCHER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("stretcher_block_entity", () ->
                    BlockEntityType.Builder.of(StretcherBlockEntity::new, STRETCHER_BLOCK.get()).build(null));


    //Item
    public static final RegistryObject<Item> DEFIBRILLATOR_BLOCK_ITEM = ITEMS.register("defibrillator_block",
            () -> new BlockItem(DEFIBRILLATOR_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> BANDAGE = ITEMS.register("bandage",
            () -> new BandageItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> IV_STAND_ITEM = ITEMS.register("iv_stand",
            () -> new BlockItem(IV_STAND.get(), new Item.Properties()));

    public static final RegistryObject<Item> HEAD_SIDE_MONITOR_BLOCK_ITEM = ITEMS.register("head_side_monitor",
            () -> new BlockItem(HEAD_SIDE_MONITOR_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> HEAD_SIDE_CABLE = ITEMS.register("head_side_cable",
            () -> new HeadsideCableItem(new Item.Properties().stacksTo(1)));

    // 各種パック
    public static final RegistryObject<Item> BLOOD_PACK = ITEMS.register("blood_pack",
            () -> new FluidPackItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ADRENALINE_PACK = ITEMS.register("adrenaline_pack",
            () -> new FluidPackItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TRANEXAMIC_ACID_PACK = ITEMS.register("tranexamic_acid_pack",
            () -> new FluidPackItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FIBRINOGEN_PACK = ITEMS.register("fibrinogen_pack",
            () -> new FluidPackItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> NORADRENALINE_PACK = ITEMS.register("noradrenaline_pack",
            () -> new FluidPackItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GLUCOSE_PACK = ITEMS.register("glucose_pack",
            () -> new FluidPackItem(new Item.Properties().stacksTo(1)));


    public static final RegistryObject<Item> TUBE = ITEMS.register("tube",
            () -> new TubeItem(new Item.Properties()));

    public static final RegistryObject<Item> ELECTRODE = ITEMS.register("electrode",
            () -> new ElectrodeItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> STRETCHER =ITEMS.register("stretcher",
            () -> new StretcherItem(new Item.Properties().stacksTo(1)) );

    // Creates a creative tab with the id "medicalsystemcore:example_tab" for the example item, that is placed after the combat tab
    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder().withTabsBefore(CreativeModeTabs.COMBAT).icon(() -> DEFIBRILLATOR_BLOCK_ITEM.get().getDefaultInstance()).displayItems((parameters, output) -> {
        output.accept(DEFIBRILLATOR_BLOCK_ITEM.get());
        output.accept(BANDAGE.get());// Add the example item to the tab. For your own tabs, this method is preferred over the event
        output.accept(IV_STAND_ITEM.get());
        output.accept(BLOOD_PACK.get());
        output.accept(ADRENALINE_PACK.get());
        output.accept(TRANEXAMIC_ACID_PACK.get());
        output.accept(FIBRINOGEN_PACK.get());
        output.accept(NORADRENALINE_PACK.get());
        output.accept(GLUCOSE_PACK.get()); // ブドウ糖パックを追加
        output.accept(TUBE.get());
        output.accept(HEAD_SIDE_MONITOR_BLOCK_ITEM.get());
        output.accept(STRETCHER.get());

    }).build());


    public Medicalsystemcore() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        modEventBus.addListener(this::registerCapabilities);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);

        ENTITY_TYPES.register(modEventBus);

        BLOCK_ENTITIES.register(modEventBus);

        MOB_EFFECTS.register(modEventBus);
        MENU_TYPES.register(modEventBus);

        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        ModPackets.register();


        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC, "medicalsystemcore-server.toml");

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
        LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BANDAGE);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            EntityRenderers.register(STRETCHER_ENTITY.get(), NoopRenderer::new);


            BlockEntityRenderers.register(Medicalsystemcore.DEFIBRILLATOR_BLOCK_ENTITY.get(), DefibrillatorBlockEntityRenderer::new);
            BlockEntityRenderers.register(Medicalsystemcore.HEAD_SIDE_MONITOR_BLOCK_ENTITY.get(), HeadsideMonitorBlockEntityRenderer::new);
            BlockEntityRenderers.register(Medicalsystemcore.IV_STAND_BLOCK_ENTITY.get(), IVStandBlockEntityRenderer::new);

            MenuScreens.register(IV_STAND_MENU.get(), IVStandScreen::new);

            event.enqueueWork(() -> {
                ItemBlockRenderTypes.setRenderLayer(Medicalsystemcore.HEAD_SIDE_MONITOR_BLOCK.get(), RenderType.cutout());
            });

            Minecraft.getInstance().getItemColors().register(new PackColor(0xFF0000), BLOOD_PACK.get());
            Minecraft.getInstance().getItemColors().register(new PackColor(0xFFEA00), ADRENALINE_PACK.get());
            Minecraft.getInstance().getItemColors().register(new PackColor(0x00F7FF), TRANEXAMIC_ACID_PACK.get());
            Minecraft.getInstance().getItemColors().register(new PackColor(0x00FFAA), FIBRINOGEN_PACK.get());
            Minecraft.getInstance().getItemColors().register(new PackColor(0x00FF00), GLUCOSE_PACK.get());


            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }

    @Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public class PlayerAnimationInitializer {

        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            // PlayerAnimationライブラリの初期化
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IPlayerMedicalData.class);
    }
}
