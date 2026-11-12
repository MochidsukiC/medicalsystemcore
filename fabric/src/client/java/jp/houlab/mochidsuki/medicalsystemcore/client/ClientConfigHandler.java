package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.common.config.ConfigKey;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import net.minecraft.network.Connection;

/**
 * クライアント側の設定の後始末。
 * クライアントは設定ファイルを読まず、サーバーから同期された clientSynced キーだけを持つ。
 */
public class ClientConfigHandler {

    /**
     * リモートサーバーから切断したら、同期された値を既定値へ戻す(次の接続先の値が届くまで他サーバーの値を残さない)。
     *
     * <p>シングルプレイ(統合サーバー)では、クライアントとサーバーが同じ JVM で同じ保管庫を共有している。
     * ここで既定値に戻すとサーバー側の設定を壊すため、戻さない。判定は接続の種類で行う:
     * 統合サーバーへの接続はメモリ内接続({@link Connection#isMemoryConnection()})、リモートは違う。
     * {@code Minecraft#hasSingleplayerServer()} は使えない。Minecraft#disconnect が singleplayerServer を
     * null にしてから切断イベントが来る場合があり、その順序が確定していないため。</p>
     *
     * <p>Forge の LoggingOut にあった「接続が null」の場合分けは要らない(Fabric の DISCONNECT は
     * ClientPacketListener を受け取り、常に接続を持つ)。</p>
     */
    public static void onDisconnect(Connection connection) {
        if (connection.isMemoryConnection()) {
            return;
        }
        MedicalConfig.resetToDefaults(ConfigKey::isClientSynced);
    }
}
