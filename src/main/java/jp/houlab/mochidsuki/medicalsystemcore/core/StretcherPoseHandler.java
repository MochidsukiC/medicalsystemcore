package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID)
public class StretcherPoseHandler {

    /**
     * プレイヤーの姿勢変更をインターセプトして、担架に乗っている場合は強制的にSLEEPINGに戻す
     */
    @SubscribeEvent
    public static void onEntityPoseChange(EntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // プレイヤーが担架に乗っているかチェック
        if (player.isPassenger() && player.getVehicle() instanceof StretcherEntity) {
            // 担架に乗っている場合は強制的にSLEEPING姿勢に戻す
            if (player.getPose() != Pose.SLEEPING) {
                player.setPose(Pose.SLEEPING);
                player.setForcedPose(Pose.SLEEPING);

                // 姿勢変更を強制的に同期
                player.refreshDimensions();
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    // 周囲のプレイヤーに更新を送信
                    serverLevel.getChunkSource().broadcast(player,
                            new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                                    player.getId(),
                                    player.getEntityData().packDirty()
                            )
                    );
                }
            }
        }
    }
}