package jp.houlab.mochidsuki.medicalsystemcore.menu;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * MenuType の登録ホルダ(Forge の Medicalsystemcore.IV_STAND_MENU などに相当)。ID は Forge と同じ。
 * IV スタンドは BlockPos、医療バッグは InteractionHand を開くときにクライアントへ渡す(Forge の IForgeMenuType に相当)。
 */
public final class ModMenus {
    private ModMenus() {
    }

    public static final MenuType<IVStandMenu> IV_STAND_MENU = register("iv_stand_menu",
            new ExtendedMenuType<IVStandMenu, BlockPos>(IVStandMenu::new, BlockPos.STREAM_CODEC));
    // 救急ポータルはデータを渡さない通常のメニュー
    public static final MenuType<RescuePortalMenu> RESCUE_PORTAL_MENU = register("rescue_portal_menu",
            new MenuType<>(RescuePortalMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<MedicalBagMenu> MEDICAL_BAG_MENU = register("medical_bag_menu",
            new ExtendedMenuType<MedicalBagMenu, InteractionHand>(MedicalBagMenu::new, InteractionHand.STREAM_CODEC));

    private static <T extends AbstractContainerMenu> MenuType<T> register(String name, MenuType<T> type) {
        return Registry.register(BuiltInRegistries.MENU, Medicalsystemcore.id(name), type);
    }

    /** クラスのロード(= 登録)を ModInitializer から確実に起こすための呼び出し口。 */
    public static void register() {
    }
}
