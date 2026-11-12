package jp.houlab.mochidsuki.medicalsystemcore;

import jp.houlab.mochidsuki.medicalsystemcore.capability.ModAttachments;
import jp.houlab.mochidsuki.medicalsystemcore.core.ConfigSyncHandler;
import jp.houlab.mochidsuki.medicalsystemcore.core.GiveUpGuard;
import jp.houlab.mochidsuki.medicalsystemcore.core.ModDataComponents;
import jp.houlab.mochidsuki.medicalsystemcore.core.PendingHealManager;
import jp.houlab.mochidsuki.medicalsystemcore.core.RevokedLicenseSavedData;
import jp.houlab.mochidsuki.medicalsystemcore.effect.ModEffects;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Medicalsystemcore implements ModInitializer {
	public static final String MODID = "medicalsystemcore";
	public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	/** 本 MOD の名前空間の Identifier を作る。 */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MODID, path);
	}

	@Override
	public void onInitialize() {
		// 段 1: attachment・data component・effect の登録(各ホルダのクラスロードで登録される)
		ModAttachments.register();
		ModDataComponents.register();
		ModEffects.register();

		// 全 payload の型登録(送受信の両側で必要)
		ModPackets.register();

		// 設定の読込(SERVER_STARTING)と同期(JOIN・設定変更時)
		ConfigSyncHandler.register();
		// ログイン時の失効リスト送信
		RevokedLicenseSavedData.register();
		// メモリ上の記録(ログアウト・サーバー停止で破棄)
		GiveUpGuard.register();
		PendingHealManager.register();

		LOGGER.info("MedicalSystemCore initialized");
	}
}
