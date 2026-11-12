package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.ConfigKey;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアント側の設定の後始末。
 * クライアントは設定ファイルを読まず、サーバーから同期された clientSynced キーだけを持つ。
 */
@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientConfigHandler {

    /**
     * リモートサーバーから切断したら、同期された値を既定値へ戻す(次の接続先の値が届くまで他サーバーの値を残さない)。
     *
     * <p>シングルプレイ(統合サーバー)では、クライアントとサーバーが同じ JVM で同じ保管庫を共有している。
     * ここで既定値に戻すとサーバー側の設定を壊すため、戻さない。判定は接続の種類で行う:
     * 統合サーバーへの接続はメモリ内接続({@link Connection#isMemoryConnection()})、リモートは違う。
     * {@code Minecraft#hasSingleplayerServer()} は使えない。Minecraft#disconnect が singleplayerServer を
     * null にしてからこのイベントを発火するため、切断中は常に「サーバー無し」に見えてしまう。</p>
     *
     * <p>接続が null になるのはプレイヤー未生成のままログアウトした場合で、サーバーからの同期は
     * プレイヤー生成後に届くため、戻すべき同期済みの値は無い。</p>
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Connection connection = event.getConnection();
        if (connection == null || connection.isMemoryConnection()) {
            return;
        }
        MedicalConfig.resetToDefaults(ConfigKey::isClientSynced);
    }
}
