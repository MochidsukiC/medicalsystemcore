package jp.houlab.mochidsuki.medicalsystemcore.client;

/**
 * 切断時に、医療・QTE・治療・ギブアップ・姿勢制御に関するクライアント状態を消す(次の接続先・次のワールドへ持ち越さない)。
 *
 * <p>これらは static で保持されるため、切断してもプロセスが生きている限り残る。
 * 残ったままだと、別のサーバーやワールドに入ったとき、前の接続で意識不明だった状態の操作制限・視点固定・QTE バー・
 * ギブアップの長押し進捗が復活する。通報と医師カード失効の状態は {@link ClientRescueStateCleaner} が消す。
 * シングルプレイでもクライアント側の保持分は統合サーバーのデータとは別物なので、接続の種類によらず無条件に消す。</p>
 */
public class ClientMedicalStateCleaner {

    public static void onDisconnect() {
        ClientMedicalDataManager.clear();
        ClientQTEManager.reset();
        ClientHealingManager.stopHealing();
        ClientGiveUpManager.resetAll();
        ClientPoseController.onClientDisconnect();
        ClientMouseHandler.reset();
        StretcherPoseManager.clear();
    }
}
