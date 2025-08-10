package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * クライアントサイドでプレイヤーの医療データを一時的に保持するクラス
 */
public class ClientMedicalDataManager {
    // HeartStatusの代わりに、ClientMedicalDataを保持するように変更
    private static final Map<UUID, ClientMedicalData> PLAYER_DATA_MAP = new HashMap<>();

    /**
     * プレイヤーのデータを丸ごと取得、なければ新規作成
     */
    public static ClientMedicalData getPlayerData(UUID playerUUID) {
        return PLAYER_DATA_MAP.computeIfAbsent(playerUUID, uuid -> new ClientMedicalData());
    }

    /**
     * Optionalで安全にプレイヤーデータを取得
     */
    public static Optional<ClientMedicalData> getPlayerData(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        return Optional.of(getPlayerData(player.getUUID()));
    }

    /**
     * 指定されたプレイヤーがクライアント側で行動不能状態か判断する
     * @param player チェックするプレイヤー
     * @return 行動不能ならtrue
     */
    public static boolean isPlayerIncapacitated(Player player) {
        if (player == null) {
            return false;
        }
        // プレイヤーのUUIDに対応するデータを取得し、心臓の状態がNORMALでないかチェック
        return getPlayerData(player)
                .map(data -> data.heartStatus != HeartStatus.NORMAL)
                .orElse(false); // データがなければ行動不能ではない
    }
}