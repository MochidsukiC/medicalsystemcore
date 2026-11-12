package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundOpenRescuePortalPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * 救急ポータルを開くタブレット。Forge の BEWLR(initializeClient)は 26.2 に無く、
 * 見た目は items JSON の special model に移る(段 5)。
 */
public class RescueTabletItem extends Item {
    public RescueTabletItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult use(Level pLevel, Player pPlayer, InteractionHand pHand) {
        if (!pLevel.isClientSide()) {
            // 医師カードによる認証
            if (MedicalAuthorizationUtil.checkMedicalAuthorization(pPlayer, "救急ポータルの閲覧")) {
                ModPackets.sendToPlayer(new ClientboundOpenRescuePortalPacket(), (ServerPlayer) pPlayer);
            }
        }
        // Forge の InteractionResultHolder.sidedSuccess(stack, isClientSide) に相当(クライアントで腕を振る)
        return InteractionResult.SUCCESS;
    }
}
