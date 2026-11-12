package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 切断時に、医療・QTE・治療・ギブアップ・姿勢制御に関するクライアント状態を消す(次の接続先・次のワールドへ持ち越さない)。
 *
 * <p>これらは static で保持されるため、切断してもプロセスが生きている限り残る。
 * 残ったままだと、別のサーバーやワールドに入ったとき、前の接続で意識不明だった状態の操作制限・視点固定・QTE バー・
 * ギブアップの長押し進捗が復活する。通報と医師カード失効の状態は {@link ClientRescueStateCleaner} が消す。
 * シングルプレイでもクライアント側の保持分は統合サーバーのデータとは別物なので、接続の種類によらず無条件に消す。</p>
 */
@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientMedicalStateCleaner {

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMedicalDataManager.clear();
        ClientQTEManager.reset();
        ClientHealingManager.stopHealing();
        ClientGiveUpManager.resetAll();
        ClientPoseController.onClientDisconnect();
        ClientMouseHandler.reset();
        StretcherPoseManager.clear();
    }
}
