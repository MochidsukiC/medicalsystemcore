package jp.houlab.mochidsuki.medicalsystemcore.client;

/**
 * ワールドの参加・離脱時の処理を行うクライアントサイドハンドラー
 */
public class ClientWorldHandler {

    /**
     * クライアントのレベルが切り替わった直後(ディメンション移動・ワールド参加)。
     * Forge の LevelEvent.Unload(クライアント)は旧レベルの破棄時に発火していたが、Fabric の
     * ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE は新しいレベルが設定された後に発火する(null への切替では発火しない)。
     * サーバー切断時の破棄は ClientMedicalStateCleaner が同じ ClientPoseController.onClientDisconnect を呼んで担う。
     */
    public static void onLevelChange() {
        // クライアントサイドの姿勢制御状態をクリア
        ClientPoseController.onClientDisconnect();
    }
}
