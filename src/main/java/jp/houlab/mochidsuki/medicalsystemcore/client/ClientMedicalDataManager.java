package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * クライアントサイドでプレイヤーの医療データを一時的に保持するクラス
 */
public class ClientMedicalDataManager {
    // 各プレイヤーのUUIDと心臓の状態をマッピングして保存
    private static final Map<UUID, HeartStatus> PLAYER_STATUS_MAP = new HashMap<>();

    public static void setPlayerHeartStatus(UUID playerUUID, HeartStatus status) {
        PLAYER_STATUS_MAP.put(playerUUID, status);
    }

    public static HeartStatus getPlayerHeartStatus(UUID playerUUID) {
        // データがなければ、とりあえず正常(NORMAL)として返す
        return PLAYER_STATUS_MAP.getOrDefault(playerUUID, HeartStatus.NORMAL);
    }

    /**
     * ヘルパーメソッド：プレイヤーが行動不能かクライアント側でチェックする
     */
    public static boolean isPlayerIncapacitated(Player player) {
        return getPlayerHeartStatus(player.getUUID()) != HeartStatus.NORMAL;
    }
}