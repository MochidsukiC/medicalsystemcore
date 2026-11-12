package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.menu.MedicalBagMenu;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class MedicalBagItem extends Item {
    public MedicalBagItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult use(Level pLevel, Player pPlayer, InteractionHand pHand) {
        if (!pLevel.isClientSide()) {
            if (MedicalAuthorizationUtil.checkMedicalAuthorization(pPlayer, "医療バッグの使用")) {
                // 開く手(InteractionHand)をクライアントのメニュー生成に渡す(Forge では buf.writeVarInt(pHand.ordinal()))
                ExtendedMenuProvider<InteractionHand> menuProvider = new ExtendedMenuProvider<>() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("container.medicalsystemcore.medical_bag");
                    }

                    @Nullable
                    @Override
                    public AbstractContainerMenu createMenu(int pContainerId, Inventory pInventory, Player pPlayer) {
                        return new MedicalBagMenu(pContainerId, pInventory, pHand);
                    }

                    @Override
                    public InteractionHand getScreenOpeningData(ServerPlayer player) {
                        return pHand;
                    }
                };
                ((ServerPlayer) pPlayer).openMenu(menuProvider);
            }
        }
        // Forge の InteractionResultHolder.sidedSuccess(stack, isClientSide) に相当(クライアントで腕を振る)
        return InteractionResult.SUCCESS;
    }
}
