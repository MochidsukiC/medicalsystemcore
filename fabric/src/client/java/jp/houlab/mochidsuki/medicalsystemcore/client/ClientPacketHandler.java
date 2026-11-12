package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.ConfigKey;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundConfigSyncPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundCoreStatsPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundRevokedLicensesSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * サーバーからクライアントへ送られるパケットの処理を専門に行うクラス。
 * Forge ではパケットクラスの handle にあった処理(CoreStats・ConfigSync・RevokedLicensesSync)も、Fabric では
 * record を main に置く都合でここに集める。呼び出しは MedicalsystemcoreClient の ClientPlayNetworking の受信から
 * (メインスレッド)。
 */
public class ClientPacketHandler {

    /**
     * サーバーからのQTE開始命令を受け取り、ClientQTEManagerを開始させる
     * @param targetId QTEの対象となるエンティティのID
     */
    public static void handleStartQTE(int targetId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity target = mc.level.getEntity(targetId);
        if (target != null) {
            ClientQTEManager.start(target);
        }
    }

    /** 受け取ったデータで、クライアント側のデータを丸ごと更新し、姿勢制御を更新する。 */
    public static void handleCoreStats(ClientboundCoreStatsPacket packet) {
        ClientMedicalData data = ClientMedicalDataManager.getPlayerData(packet.playerUUID());
        boolean wasConscious = data.isConscious;

        data.bloodLevel = packet.bloodLevel();
        data.heartStatus = packet.heartStatus();
        data.bleedingSpeed = packet.bleedingSpeed();
        data.resuscitationChance = packet.resuscitationChance();
        data.isConscious = packet.isConscious();

        // *** クライアントサイドでの姿勢制御更新 ***
        // 自分のデータが更新された場合のみ姿勢制御を更新
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(packet.playerUUID())) {
            // 意識状態が変化した場合は即座に姿勢制御を更新
            if (wasConscious != packet.isConscious()) {
                ClientPoseController.setUnconsciousPose(mc.player, !packet.isConscious());
            }

            // 全体的な姿勢制御状態を更新
            ClientPoseController.updateFromMedicalData(mc.player);
        }

        // 他のプレイヤーのデータ更新の場合
        else {
            Player targetPlayer = mc.level != null ? mc.level.getPlayerByUUID(packet.playerUUID()) : null;
            if (targetPlayer != null) {
                // 他のプレイヤーの姿勢制御も更新（視覚的同期のため）
                ClientPoseController.updateFromMedicalData(targetPlayer);
            }
        }
    }

    /** サーバーから同期された設定値(clientSynced のキーだけ)を検証して適用する。 */
    public static void handleConfigSync(ClientboundConfigSyncPacket packet) {
        Map<String, Object> accepted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : packet.values().entrySet()) {
            ConfigKey<?> key = MedicalConfigKeys.byId(entry.getKey());
            if (key == null) {
                // サーバーの方が新しく、このクライアントが知らないキーを送ってきた場合
                Medicalsystemcore.LOGGER.warn("サーバーから未知の設定キーが送られたため無視する: {}", entry.getKey());
            } else if (!key.isClientSynced()) {
                Medicalsystemcore.LOGGER.warn("サーバーから同期対象ではない設定キーが送られたため無視する: {}", entry.getKey());
            } else {
                accepted.put(entry.getKey(), entry.getValue());
            }
        }
        try {
            MedicalConfig.setAll(accepted);
        } catch (IllegalArgumentException e) {
            // サーバーが送った値が検証に通らない。一部だけ適用せず、全体を捨てる(setAll は全か無か)
            Medicalsystemcore.LOGGER.error("サーバーから送られた設定値が不正なため適用しない", e);
        }
    }

    /** 失効リストを丸ごと置き換える。 */
    public static void handleRevokedLicensesSync(ClientboundRevokedLicensesSyncPacket packet) {
        ClientRevokedLicenses.replaceAll(packet.licenses());
    }
}
