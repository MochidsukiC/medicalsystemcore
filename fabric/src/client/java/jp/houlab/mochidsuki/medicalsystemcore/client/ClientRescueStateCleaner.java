package jp.houlab.mochidsuki.medicalsystemcore.client;

/**
 * 切断時に、通報と医師カード失効に関するクライアント状態を消す(次の接続先・次のワールドへ持ち越さない)。
 *
 * <p>通報の一覧は RescuePortalScreen が画面ごとに持ち、開くたびにサーバーから取り直す。切断で画面も閉じるため、
 * 残るのは追跡中の通報(ClientTrackingManager)と失効リスト(ClientRevokedLicenses)。
 * シングルプレイでもクライアント側の保持分は統合サーバーのデータとは別物なので、接続の種類によらず無条件に消す。</p>
 */
public class ClientRescueStateCleaner {

    public static void onDisconnect() {
        ClientTrackingManager.clearTracking();
        ClientRevokedLicenses.clear();
    }
}
