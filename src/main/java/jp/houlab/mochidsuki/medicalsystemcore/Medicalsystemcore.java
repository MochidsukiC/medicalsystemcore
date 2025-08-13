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
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import jp.houlab.mochidsuki.medicalsystemcore.item.*;
import jp.houlab.mochidsuki.medicalsystemcore.menu.IVStandMenu;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import jp.houlab.mochidsuki.medicalsystemcore.block.IVStandBlock;
import jp.houlab.mochidsuki.medicalsystemcore.effect.*;
import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity.DefibrillatorBlockEntityRenderer;
import jp.houlab.mochidsuki.medicalsystemcore.client.screen.IVStandScreen;
import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity.HeadsideMonitorBlockEntityRenderer;
import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity.IVStandBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
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

@Mod(Medicalsystemcore.MODID)
public class Medicalsystemcore {

    public static final String MODID = "medicalsystemcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Deferred Registers
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

    // Blocks
    public static final RegistryObject<Block> IV_STAND = BLOCKS.register("iv_stand", IVStandBlock::new);
    public static final RegistryObject<Block> DEFIBRILLATOR_BLOCK = BLOCKS.register("defibrillator_block",
            () -> new DefibrillatorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));
    public static final RegistryObject<Block> HEAD_SIDE_MONITOR_BLOCK = BLOCKS.register("head_side_monitor",
            () -> new HeadsideMonitorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));
    public static final RegistryObject<Block> STRETCHER_BLOCK = BLOCKS.register("stretcher",
            () -> new StretcherBlock(BlockBehaviour.Properties.copy(Blocks.WHITE_WOOL)));

    // Block Entities
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

    // Items
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
    public static final RegistryObject<Item> ELECTRODE = ITEMS.register("electrode",
            () -> new ElectrodeItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> STRETCHER = ITEMS.register("stretcher",
            () -> new StretcherItem(new Item.Properties().stacksTo(1)));

    // Medical Packs
    public static final RegistryObject<Item> BLOOD_PACK = ITEMS.register("blood_pack",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> ADRENALINE_PACK = ITEMS.register("adrenaline_pack",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> TRANEXAMIC_ACID_PACK = ITEMS.register("tranexamic_acid_pack",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> FIBRINOGEN_PACK = ITEMS.register("fibrinogen_pack",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> NORADRENALINE_PACK = ITEMS.register("noradrenaline_pack",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> GLUCOSE_PACK = ITEMS.register("glucose_pack",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> TUBE = ITEMS.register("tube",
            () -> new TubeItem(new Item.Properties().stacksTo(1)));

    // Menu
    public static final RegistryObject<MenuType<IVStandMenu>> IV_STAND_MENU =
            MENU_TYPES.register("iv_stand_menu", () -> IForgeMenuType.create(IVStandMenu::new));

    // Effects
    public static final RegistryObject<MobEffect> TRANSFUSION = MOB_EFFECTS.register("transfusion", TransfusionEffect::new);
    public static final RegistryObject<MobEffect> BANDAGE_EFFECT = MOB_EFFECTS.register("bandage_effect", BandageEffect::new);
    public static final RegistryObject<MobEffect> ADRENALINE_EFFECT = MOB_EFFECTS.register("adrenaline_effect", AdrenalineEffect::new);
    public static final RegistryObject<MobEffect> FIBRINOGEN_EFFECT = MOB_EFFECTS.register("fibrinogen_effect", FibrinogenEffect::new);
    public static final RegistryObject<MobEffect> TRANEXAMIC_ACID_EFFECT = MOB_EFFECTS.register("tranexamic_acid_effect", TranexamicAcidEffect::new);

    // Entity
    public static final RegistryObject<EntityType<StretcherEntity>> STRETCHER_ENTITY =
            ENTITY_TYPES.register("stretcher_entity", () -> EntityType.Builder.<StretcherEntity>of(
                            StretcherEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.5F)
                    .clientTrackingRange(64)      // 修正: 追跡範囲を64ブロックに拡大（プレイヤーと同等）
                    .updateInterval(1)            // 修正: 毎ティック更新（20→1）で滑らかな動作
                    .setShouldReceiveVelocityUpdates(true)  // 速度更新を受信
                    .build("stretcher_entity"));

    // Creative Tab
    public static final RegistryObject<CreativeModeTab> MEDICAL_SYSTEM_CORE_TAB = CREATIVE_MODE_TABS.register("medical_system_core_tab", () -> CreativeModeTab.builder()
            .icon(() -> BANDAGE.get().getDefaultInstance())
            .title(net.minecraft.network.chat.Component.translatable("creativetab.medical_system_core_tab"))
            .displayItems((parameters, output) -> {
                output.accept(BANDAGE.get());
                output.accept(ELECTRODE.get());
                output.accept(DEFIBRILLATOR_BLOCK_ITEM.get());
                output.accept(IV_STAND_ITEM.get());
                output.accept(BLOOD_PACK.get());
                output.accept(ADRENALINE_PACK.get());
                output.accept(TRANEXAMIC_ACID_PACK.get());
                output.accept(FIBRINOGEN_PACK.get());
                output.accept(NORADRENALINE_PACK.get());
                output.accept(GLUCOSE_PACK.get());
                output.accept(TUBE.get());
                output.accept(HEAD_SIDE_MONITOR_BLOCK_ITEM.get());
                output.accept(STRETCHER.get());
            }).build());

    public Medicalsystemcore() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Event listeners
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::addCreative);

        // Register Deferred Registers
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register for server events
        MinecraftForge.EVENT_BUS.register(this);

        // Initialize packet system
        ModPackets.register();

        // Load configuration
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC, "medicalsystemcore-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Medical System Core - Common Setup Complete");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BANDAGE);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Medical System Core - Server Starting");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IPlayerMedicalData.class);
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Entity Renderers
            EntityRenderers.register(STRETCHER_ENTITY.get(), NoopRenderer::new);

            // Block Entity Renderers
            BlockEntityRenderers.register(DEFIBRILLATOR_BLOCK_ENTITY.get(), DefibrillatorBlockEntityRenderer::new);
            BlockEntityRenderers.register(HEAD_SIDE_MONITOR_BLOCK_ENTITY.get(), HeadsideMonitorBlockEntityRenderer::new);
            BlockEntityRenderers.register(IV_STAND_BLOCK_ENTITY.get(), IVStandBlockEntityRenderer::new);

            // Menu Screens
            MenuScreens.register(IV_STAND_MENU.get(), IVStandScreen::new);

            // Render Layers
            event.enqueueWork(() -> {
                ItemBlockRenderTypes.setRenderLayer(HEAD_SIDE_MONITOR_BLOCK.get(), RenderType.cutout());
            });

            // Item Colors
            Minecraft.getInstance().getItemColors().register(new PackColor(0xFF0000), BLOOD_PACK.get());
            Minecraft.getInstance().getItemColors().register(new PackColor(0xFFEA00), ADRENALINE_PACK.get());
            Minecraft.getInstance().getItemColors().register(new PackColor(0x00F7FF), TRANEXAMIC_ACID_PACK.get());
            Minecraft.getInstance().getItemColors().register(new PackColor(0x00FFAA), FIBRINOGEN_PACK.get());
            Minecraft.getInstance().getItemColors().register(new PackColor(0x00FF00), GLUCOSE_PACK.get());

            LOGGER.info("Medical System Core - Client Setup Complete");
        }
    }
}