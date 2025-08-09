package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアントサイドの入力を監視し、必要に応じてブロックするクラス
 */
@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientInputHandler {

    @SubscribeEvent
    public static void onClientPlayerTick(TickEvent.PlayerTickEvent event) {
        // クライアントサイドで、ティックの最後に実行
        if (event.side.isClient() && event.phase == TickEvent.Phase.END) {
            // Minecraftの姿勢計算ロジックが実行された後に、私たちのロジックで上書きする
            Player player = event.player;
            if (player != null && ClientMedicalDataManager.isPlayerIncapacitated(player)) {
                // 行動不能状態の場合、姿勢がSWIMMINGでなければ強制的に設定する
                if (player.getPose() != Pose.SWIMMING) {
                    player.setPose(Pose.SWIMMING);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }

        // ClientMedicalDataManagerからプレイヤーの状態を確認
        if (ClientMedicalDataManager.isPlayerIncapacitated(player)) {
            // 行動不能な場合、全ての移動入力をゼロにする
            event.getInput().forwardImpulse = 0;
            event.getInput().leftImpulse = 0;
            event.getInput().jumping = false;
        }
    }
}