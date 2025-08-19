package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.menu.RescuePortalMenu;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class RescueTabletItem extends Item {
    public RescueTabletItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pHand) {
        if (!pLevel.isClientSide()) {
            // 医師カードによる認証
            if (MedicalAuthorizationUtil.checkMedicalAuthorization(pPlayer, "救急ポータルの閲覧")) {
                MenuProvider menuProvider = new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("gui.medicalsystemcore.rescue_portal.title");
                    }

                    @Nullable
                    @Override
                    public AbstractContainerMenu createMenu(int pContainerId, Inventory pInventory, Player pPlayer) {
                        return new RescuePortalMenu(pContainerId, pInventory);
                    }
                };
                NetworkHooks.openScreen((ServerPlayer) pPlayer, menuProvider);
            }
        }
        return InteractionResultHolder.sidedSuccess(pPlayer.getItemInHand(pHand), pLevel.isClientSide());
    }
}