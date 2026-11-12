package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundConfigSyncPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundCoreStatsPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundRevokedLicensesSyncPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundStartQTEPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.network.Connection;

/**
 * クライアントの初期化(段 4)。Screen に触る受信ハンドラ・レンダラー・Screen の登録は段 5。
 */
public class MedicalsystemcoreClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// main の BandageItem が呼ぶ受け口に、クライアントの治療開始処理を差し込む
		ClientHealingHook.setStartHealing(ClientHealingManager::startHealing);

		// サーバー -> クライアントの受信(ハンドラはメインスレッドで呼ばれる)
		ClientPlayNetworking.registerGlobalReceiver(ClientboundCoreStatsPacket.TYPE,
				(payload, context) -> ClientPacketHandler.handleCoreStats(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientboundStartQTEPacket.TYPE,
				(payload, context) -> ClientPacketHandler.handleStartQTE(payload.targetEntityId()));
		ClientPlayNetworking.registerGlobalReceiver(ClientboundConfigSyncPacket.TYPE,
				(payload, context) -> ClientPacketHandler.handleConfigSync(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientboundRevokedLicensesSyncPacket.TYPE,
				(payload, context) -> ClientPacketHandler.handleRevokedLicensesSync(payload));

		// 毎 tick: 自分のタイマー → クライアント上の意識不明者の処理
		ClientTickEvents.END_CLIENT_TICK.register(ClientInputHandler::onClientTick);

		// 切断時の後始末。DISCONNECT は Fabric が接続の channelInactive(netty スレッド)からも直接呼ぶため、
		// static な状態を触る処理はメインスレッドへ回す。接続の種類(統合サーバーか)は回す前に読む。
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Connection connection = handler.getConnection();
			client.execute(() -> {
				ClientConfigHandler.onDisconnect(connection);
				ClientMedicalStateCleaner.onDisconnect();
				ClientRescueStateCleaner.onDisconnect();
			});
		});

		// 上の後始末は、リモートが先に切った場合に Minecraft#disconnect の dropAllTasks() でキューごと捨てられることがある。
		// 前の接続の状態を次の接続へ持ち越さないよう、次の接続の開始時(JOIN。メインスレッドで、サーバーからの同期パケットより前)にも消す。
		// どちらも冪等。設定は「これから入る接続」がメモリ内接続(統合サーバー)なら、サーバーが読んだ値を共有しているので戻さない。
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ClientConfigHandler.onDisconnect(handler.getConnection());
			ClientMedicalStateCleaner.onDisconnect();
			ClientRescueStateCleaner.onDisconnect();
		});

		// ディメンション移動(クライアントのレベルの切り替え)
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> ClientWorldHandler.onLevelChange());

		// クロスヘアの HUD 要素を包む(治療バー・ギブアップバー・QTE モニター・ダウン中のヒント)
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, ClientHudHandler::wrapCrosshair);

		Medicalsystemcore.LOGGER.info("MedicalSystemCore client initialized");
	}
}
