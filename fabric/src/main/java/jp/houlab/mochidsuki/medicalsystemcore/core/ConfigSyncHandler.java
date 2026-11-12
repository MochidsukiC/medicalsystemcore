package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.common.config.ConfigKey;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigFile;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundConfigSyncPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

/**
 * サーバー側の設定の読み込みとクライアントへの同期。
 * <ul>
 *   <li>サーバー起動前に config/medicalsystemcore.json を読み込む(保管庫は全キーが置き換わる)。</li>
 *   <li>ログイン時に、クライアントが参照するキー(clientSynced)をそのプレイヤーへ送る。</li>
 *   <li>実行中に clientSynced キーが変わったら、サーバー上の全員へ送る。</li>
 * </ul>
 */
public class ConfigSyncHandler {
    public static final String CONFIG_FILE_NAME = "medicalsystemcore.json";

    /** サーバーが動いている間だけ true(SERVER_STARTED 〜 SERVER_STOPPING)。 */
    private static volatile boolean serverRunning = false;

    /** 動作中のサーバー(Forge の ServerLifecycleHooks.getCurrentServer() の代わり)。SERVER_STARTED で保持する。 */
    private static volatile MinecraftServer currentServer = null;

    /**
     * MedicalConfig の変更リスナーとサーバーのイベントを登録する。MOD 初期化で 1 回だけ呼ぶ
     * (統合サーバーはワールドを開くたびに起動するので、サーバー起動イベントでは登録しない)。
     */
    public static void register() {
        MedicalConfig.addListener(ConfigSyncHandler::onConfigChanged);

        ServerLifecycleEvents.SERVER_STARTING.register(ConfigSyncHandler::onServerStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(ConfigSyncHandler::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(ConfigSyncHandler::onServerStopping);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ModPackets.sendToPlayer(ClientboundConfigSyncPacket.ofCurrent(), handler.player));
    }

    private static void onServerStarting(MinecraftServer server) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
        MedicalConfigFile.load(path);
    }

    private static void onServerStarted(MinecraftServer server) {
        currentServer = server;
        serverRunning = true;
    }

    private static void onServerStopping(MinecraftServer server) {
        serverRunning = false;
        currentServer = null;
    }

    private static void onConfigChanged(ConfigKey<?> key) {
        if (!key.isClientSynced() || !serverRunning) {
            return;
        }
        MinecraftServer server = currentServer;
        if (server == null) {
            return;
        }
        // 設定はどのスレッドからでも変更できるため、送信はサーバースレッドで行う
        server.execute(() -> ModPackets.sendToAllClients(ClientboundConfigSyncPacket.ofCurrent(), server));
    }
}
