package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.block.ModBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Function;

/**
 * Item の登録ホルダ(Forge の Medicalsystemcore.BANDAGE などに相当)。ID は Forge と同じ。
 * Item.Properties には setId が必須なので、登録のたびにここで ResourceKey を渡す。
 * ブロックのアイテムは ModBlocks のクラスロード後(= ブロックの登録後)に登録される。
 * Forge の RegistryObject と違い、値そのもの(.get() は不要)を公開する。
 */
public final class ModItems {
    private ModItems() {
    }

    public static final Item DEFIBRILLATOR_BLOCK_ITEM = registerBlockItem("defibrillator_block", ModBlocks.DEFIBRILLATOR_BLOCK);
    public static final Item BANDAGE = register("bandage", BandageItem::new, new Item.Properties().stacksTo(64));
    public static final Item IV_STAND_ITEM = registerBlockItem("iv_stand", ModBlocks.IV_STAND);
    public static final Item HEAD_SIDE_MONITOR_BLOCK_ITEM = registerBlockItem("head_side_monitor", ModBlocks.HEAD_SIDE_MONITOR_BLOCK);
    public static final Item HEAD_SIDE_CABLE = register("head_side_cable", HeadsideCableItem::new, new Item.Properties().stacksTo(1));
    public static final Item ELECTRODE = register("electrode", ElectrodeItem::new, new Item.Properties().stacksTo(16));
    public static final Item STRETCHER = register("stretcher", StretcherItem::new, new Item.Properties().stacksTo(1));
    public static final Item DOCTOR_CARD = register("doctor_card", DoctorCardItem::new, new Item.Properties());
    public static final Item RESCUE_TABLET = register("rescue_tablet", RescueTabletItem::new, new Item.Properties().stacksTo(1));
    public static final Item MEDICAL_BAG = register("medical_bag", MedicalBagItem::new, new Item.Properties().stacksTo(1));

    // Medical Packs
    public static final Item BLOOD_PACK = register("blood_pack", FluidPackItem::new, new Item.Properties().stacksTo(16));
    public static final Item ADRENALINE_PACK = register("adrenaline_pack", FluidPackItem::new, new Item.Properties().stacksTo(16));
    public static final Item TRANEXAMIC_ACID_PACK = register("tranexamic_acid_pack", FluidPackItem::new, new Item.Properties().stacksTo(16));
    public static final Item FIBRINOGEN_PACK = register("fibrinogen_pack", FluidPackItem::new, new Item.Properties().stacksTo(16));
    public static final Item NORADRENALINE_PACK = register("noradrenaline_pack", FluidPackItem::new, new Item.Properties().stacksTo(16));
    public static final Item GLUCOSE_PACK = register("glucose_pack", FluidPackItem::new, new Item.Properties().stacksTo(16));
    public static final Item TUBE = register("tube", TubeItem::new, new Item.Properties().stacksTo(64));

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Medicalsystemcore.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    /** ブロックのアイテム。名前(翻訳キー)はブロック側のものを使う。 */
    private static Item registerBlockItem(String name, Block block) {
        return register(name, properties -> new BlockItem(block, properties), new Item.Properties().useBlockDescriptionPrefix());
    }

    /** クラスのロード(= 登録)を ModInitializer から確実に起こすための呼び出し口。 */
    public static void register() {
    }
}
