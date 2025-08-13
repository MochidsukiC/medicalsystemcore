package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import jp.houlab.mochidsuki.medicalsystemcore.util.AngleUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * クライアントサイドでの姿勢制御管理（完全修正版）
 * サーバーサイドのPoseControllerと同様の優先度システムを使用し、
 * ネットワークレイテンシーに関係なく即座に姿勢制御を適用
 */
public class ClientPoseController {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 姿勢制御の理由（優先度順） - サーバーサイドと同一
     */
    public enum PoseReason {
        STRETCHER(1, Pose.SLEEPING),      // 最高優先度：ストレッチャー
        UNCONSCIOUS(2, Pose.SWIMMING);    // 意識障害

        private final int priority;
        private final Pose targetPose;

        PoseReason(int priority, Pose targetPose) {
            this.priority = priority;
            this.targetPose = targetPose;
        }

        public int getPriority() { return priority; }
        public Pose getTargetPose() { return targetPose; }
    }

    /**
     * プレイヤーごとの姿勢制御状態（クライアントサイド）
     */
    private static class ClientPoseControlState {
        private final Map<PoseReason, Boolean> activeReasons = new HashMap<>();
        private PoseReason currentReason = null;
        private Pose currentPose = null;

        public void setReason(PoseReason reason, boolean active) {
            activeReasons.put(reason, active);
            updateCurrentReason();
        }

        private void updateCurrentReason() {
            PoseReason highestPriority = null;
            int lowestPriorityValue = Integer.MAX_VALUE;

            for (Map.Entry<PoseReason, Boolean> entry : activeReasons.entrySet()) {
                if (entry.getValue() && entry.getKey().getPriority() < lowestPriorityValue) {
                    highestPriority = entry.getKey();
                    lowestPriorityValue = entry.getKey().getPriority();
                }
            }

            this.currentReason = highestPriority;
            this.currentPose = highestPriority != null ? highestPriority.getTargetPose() : null;
        }

        public boolean isControlled() {
            return currentReason != null;
        }

        public Pose getTargetPose() {
            return currentPose;
        }

        public PoseReason getCurrentReason() {
            return currentReason;
        }

        public void clear() {
            activeReasons.clear();
            currentReason = null;
            currentPose = null;
        }
    }

    // プレイヤーごとの姿勢制御状態を管理
    private static final Map<UUID, ClientPoseControlState> playerStates = new HashMap<>();

    /**
     * 状態を取得または作成する（修正：このメソッドが不足していました）
     */
    private static ClientPoseControlState getOrCreateState(UUID playerId) {
        return playerStates.computeIfAbsent(playerId, k -> new ClientPoseControlState());
    }




    /**
     * ストレッチャーシステム用のヘルパーメソッド（単純化版）
     */
    public static void setStretcherPose(Player player, boolean onStretcher) {
        //setPoseControl(player, PoseReason.STRETCHER, onStretcher);
    }

    /**
     * ストレッチャー用の特別な姿勢制御（クライアントサイド・単純化版）
     */
    private static void applyStretcherPose(Player player, Pose targetPose) {
        // 複雑な角度計算とvalidationを削除
        if (player.getVehicle() instanceof StretcherEntity stretcher) {
            // シンプルな向き同期
            float stretcherYaw = stretcher.getYRot();
            player.yBodyRot = stretcherYaw;
            player.yBodyRotO = stretcherYaw;
        }

        // 姿勢を設定
        player.setPose(targetPose);
    }


    /**
     * クライアントサイドでの姿勢制御設定
     */
    public static void setPoseControl(Player player, PoseReason reason, boolean active) {
        if (player == null) return;

        UUID playerId = player.getUUID();
        ClientPoseControlState state = getOrCreateState(playerId);

        boolean wasControlled = state.isControlled();
        Pose previousPose = state.getTargetPose();

        state.setReason(reason, active);

        boolean isNowControlled = state.isControlled();
        Pose newPose = state.getTargetPose();

        // 状態が変化した場合のみ処理
        if (wasControlled != isNowControlled ||
                (previousPose != newPose && (previousPose == null || !previousPose.equals(newPose)))) {

            if (isNowControlled) {
                // 姿勢制御を開始または変更
                applyPoseControl(player, newPose, state.getCurrentReason());
                LOGGER.debug("Client applied pose control: {} -> {} (reason: {})",
                        player.getName().getString(), newPose, state.getCurrentReason());
            } else {
                // 姿勢制御を解除
                releasePoseControl(player);
                LOGGER.debug("Client released pose control: {}", player.getName().getString());
            }
        }
    }

    /**
     * クライアントサイドでの姿勢制御適用
     */
    private static void applyPoseControl(Player player, Pose targetPose, PoseReason reason) {
        // ストレッチャーの場合は特別な処理
        if (reason == PoseReason.STRETCHER) {
            applyStretcherPose(player, targetPose);
        } else {
            // 通常の姿勢制御
            player.setPose(targetPose);
        }
    }


    /**
     * クライアントサイドでの姿勢制御解除
     */
    private static void releasePoseControl(Player player) {
        // 自然な姿勢に戻す
        player.setPose(Pose.STANDING);
    }

    /**
     * プレイヤーの現在の姿勢制御状態を取得
     */
    public static boolean isControlled(Player player) {
        if (player == null) return false;
        ClientPoseControlState state = playerStates.get(player.getUUID());
        return state != null && state.isControlled();
    }

    /**
     * 特定の理由による姿勢制御が有効かどうか
     */
    public static boolean isControlledBy(Player player, PoseReason reason) {
        if (player == null) return false;
        ClientPoseControlState state = playerStates.get(player.getUUID());
        return state != null && state.getCurrentReason() == reason;
    }

    /**
     * プレイヤーの姿勢制御を完全にクリア
     */
    public static void clearPoseControl(Player player) {
        if (player == null) return;

        UUID playerId = player.getUUID();
        ClientPoseControlState state = playerStates.get(playerId);

        if (state != null && state.isControlled()) {
            releasePoseControl(player);
        }

        playerStates.remove(playerId);
        LOGGER.debug("Cleared client pose control for player: {}", player.getName().getString());
    }

    /**
     * 意識障害システム用のヘルパーメソッド
     */
    public static void setUnconsciousPose(Player player, boolean unconscious) {
        setPoseControl(player, PoseReason.UNCONSCIOUS, unconscious);
    }


    /**
     * バニラの姿勢制御を上書きするための強制的な姿勢維持
     * 毎フレーム呼び出される
     */
    public static void maintainPoseControl(Player player) {
        if (player == null) return;

        ClientPoseControlState state = playerStates.get(player.getUUID());
        if (state == null || !state.isControlled()) return;

        Pose targetPose = state.getTargetPose();

        // バニラの姿勢制御による変更を即座に上書き
        if (player.getPose() != targetPose) {
            applyPoseControl(player, targetPose, state.getCurrentReason());
            LOGGER.debug("Client restored pose control for player: {} -> {}",
                    player.getName().getString(), targetPose);
        }
    }

    /**
     * クライアントサイドでの状態更新（医療データ変更時）
     */
    public static void updateFromMedicalData(Player player) {
        if (player == null) return;

        // 意識状態をチェック（既存のClientMedicalDataに合わせて修正）
        ClientMedicalDataManager.getPlayerData(player).ifPresent(data -> {
            boolean isUnconscious = !data.isConscious;  // 既存のフィールド名を使用
            setUnconsciousPose(player, isUnconscious);
        });

        // ストレッチャー状態をチェック
        boolean isOnStretcher = (player.getVehicle() instanceof StretcherEntity) ||
                isPlayerOnStretcherBlock(player);
        setStretcherPose(player, isOnStretcher);
    }

    /**
     * プレイヤーがストレッチャーブロックに乗っているかチェック
     */
    private static boolean isPlayerOnStretcherBlock(Player player) {
        // ストレッチャーブロックの検出ロジック
        // ここでは簡単な実装として、プレイヤーが特定の姿勢かつ低い位置にいるかをチェック
        // より正確な実装が必要な場合は、ブロックエンティティとの通信が必要
        return false; // 一旦false、必要に応じて実装
    }

    /**
     * クライアント参加時/ワールド変更時のクリーンアップ
     */
    public static void onClientDisconnect() {
        playerStates.clear();
        LOGGER.debug("Cleared all client pose control states");
    }
}