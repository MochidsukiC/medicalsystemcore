package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.item.ModItems;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

/**
 * クリエイティブタブの登録(Forge の MEDICAL_SYSTEM_CORE_TAB と、包帯の TOOLS_AND_UTILITIES への追加)。
 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {
    }

    public static final CreativeModeTab MEDICAL_SYSTEM_CORE_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            Medicalsystemcore.id("medical_system_core_tab"),
            FabricCreativeModeTab.builder()
                    .icon(() -> ModItems.BANDAGE.getDefaultInstance())
                    .title(Component.translatable("creativetab.medical_system_core_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.BANDAGE);
                        output.accept(ModItems.ELECTRODE);
                        output.accept(ModItems.DEFIBRILLATOR_BLOCK_ITEM);
                        output.accept(ModItems.IV_STAND_ITEM);
                        output.accept(ModItems.BLOOD_PACK);
                        output.accept(ModItems.ADRENALINE_PACK);
                        output.accept(ModItems.TRANEXAMIC_ACID_PACK);
                        output.accept(ModItems.FIBRINOGEN_PACK);
                        output.accept(ModItems.NORADRENALINE_PACK);
                        output.accept(ModItems.GLUCOSE_PACK);
                        output.accept(ModItems.TUBE);
                        output.accept(ModItems.HEAD_SIDE_MONITOR_BLOCK_ITEM);
                        output.accept(ModItems.STRETCHER);
                        output.accept(ModItems.RESCUE_TABLET);
                        // Forge の MEDICAL_BAG.isPresent() は登録済みなら常に真なので、無条件に追加する
                        output.accept(ModItems.MEDICAL_BAG);
                    })
                    .build());

    /** タブの登録(クラスのロード)と、包帯の TOOLS_AND_UTILITIES への追加を行う。MOD 初期化で 1 回だけ呼ぶ。 */
    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(output -> output.accept(ModItems.BANDAGE));
    }
}
