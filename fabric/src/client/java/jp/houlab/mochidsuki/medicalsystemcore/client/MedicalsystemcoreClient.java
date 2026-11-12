package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundConfigSyncPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundCoreStatsPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundRevokedLicensesSyncPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundStartQTEPacket;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.ModBlockEntities;
import jp.houlab.mochidsuki.medicalsystemcore.client.model.StretcherModel;
import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.RescueTabletOverlayRenderer;
import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.StretcherRenderer;
import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity.DefibrillatorBlockEntityRenderer;
import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity.HeadsideMonitorBlockEntityRenderer;
import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity.IVStandBlockEntityRenderer;
import jp.houlab.mochidsuki.medicalsystemcore.client.screen.IVStandScreen;
import jp.houlab.mochidsuki.medicalsystemcore.client.screen.MedicalBagScreen;
import jp.houlab.mochidsuki.medicalsystemcore.entity.ModEntities;
import jp.houlab.mochidsuki.medicalsystemcore.menu.ModMenus;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundOpenRescuePortalPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundRemoveRescueDataPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundSyncAllRescueDataPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundUpdateRescueDataPacket;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.network.Connection;

/**
 * クライアントの初期化(段 4: 受信・入力・HUD、段 5: レンダラー・Screen・special model・Screen に触る受信)。
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

		// 救急ポータル画面に触る受信(段 5)
		ClientPlayNetworking.registerGlobalReceiver(ClientboundOpenRescuePortalPacket.TYPE,
				(payload, context) -> ClientRescuePortalHandler.openScreen(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientboundSyncAllRescueDataPacket.TYPE,
				(payload, context) -> ClientRescuePortalHandler.handleSyncAll(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientboundUpdateRescueDataPacket.TYPE,
				(payload, context) -> ClientRescuePortalHandler.handleUpdate(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientboundRemoveRescueDataPacket.TYPE,
				(payload, context) -> ClientRescuePortalHandler.handleRemove(payload));

		// レンダラー(Forge の FMLClientSetupEvent / EntityRenderersEvent.RegisterRenderers。Fabric の EntityRendererRegistry などは非推奨なので、AW で公開されたバニラの register を使う。ストレッチャーは 1 回だけ登録する)
		EntityRenderers.register(ModEntities.STRETCHER_ENTITY, StretcherRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.DEFIBRILLATOR_BLOCK_ENTITY, DefibrillatorBlockEntityRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.HEAD_SIDE_MONITOR_BLOCK_ENTITY, HeadsideMonitorBlockEntityRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.IV_STAND_BLOCK_ENTITY, IVStandBlockEntityRenderer::new);

		// ストレッチャーのモデルのレイヤー定義(形状)
		ModelLayerRegistry.registerModelLayer(StretcherModel.LAYER_LOCATION, StretcherModel::createBodyLayer);

		// メニューの画面(救急ポータルは Forge と同じく MenuScreens に登録せず、受信パケットで直接開く)
		MenuScreens.register(ModMenus.IV_STAND_MENU, IVStandScreen::new);
		MenuScreens.register(ModMenus.MEDICAL_BAG_MENU, MedicalBagScreen::new);

		// 救急タブレットの追跡オーバーレイ(items/rescue_tablet.json の minecraft:special から参照される special model)
		SpecialModelRenderers.ID_MAPPER.put(Medicalsystemcore.id("rescue_tablet_overlay"), RescueTabletOverlayRenderer.Unbaked.MAP_CODEC);

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
