package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.ConfigKey;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigFile;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundConfigSyncPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.file.Path;

/**
 * サーバー側の設定の読み込みとクライアントへの同期。
 * <ul>
 *   <li>サーバー起動前に config/medicalsystemcore.json を読み込む(保管庫は全キーが置き換わる)。</li>
 *   <li>ログイン時に、クライアントが参照するキー(clientSynced)をそのプレイヤーへ送る。</li>
 *   <li>実行中に clientSynced キーが変わったら、サーバー上の全員へ送る。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID)
public class ConfigSyncHandler {
    public static final String CONFIG_FILE_NAME = "medicalsystemcore.json";

    /** サーバーが動いている間だけ true(ServerStarted 〜 ServerStopping)。 */
    private static volatile boolean serverRunning = false;

    /**
     * MedicalConfig の変更リスナーを登録する。MOD 初期化で 1 回だけ呼ぶ
     * (統合サーバーはワールドを開くたびに起動するので、サーバー起動イベントでは登録しない)。
     */
    public static void registerListener() {
        MedicalConfig.addListener(ConfigSyncHandler::onConfigChanged);
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Path path = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
        MedicalConfigFile.load(path);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        serverRunning = true;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        serverRunning = false;
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModPackets.sendToPlayer(ClientboundConfigSyncPacket.ofCurrent(), player);
        }
    }

    private static void onConfigChanged(ConfigKey<?> key) {
        if (!key.isClientSynced() || !serverRunning) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        // 設定はどのスレッドからでも変更できるため、送信はサーバースレッドで行う
        server.execute(() -> ModPackets.sendToAllClients(ClientboundConfigSyncPacket.ofCurrent()));
    }
}
