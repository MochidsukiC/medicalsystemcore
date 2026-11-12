package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.RescueTabletBEWLR;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundOpenRescuePortalPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public class RescueTabletItem extends Item {
    public RescueTabletItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pHand) {
        if (!pLevel.isClientSide()) {
            // 医師カードによる認証
            if (MedicalAuthorizationUtil.checkMedicalAuthorization(pPlayer, "救急ポータルの閲覧")) {
                ModPackets.sendToPlayer(new ClientboundOpenRescuePortalPacket(), (ServerPlayer) pPlayer);
            }
        }
        return InteractionResultHolder.sidedSuccess(pPlayer.getItemInHand(pHand), pLevel.isClientSide());
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new RescueTabletBEWLR();
                }
                return renderer;
            }
        });
    }
}