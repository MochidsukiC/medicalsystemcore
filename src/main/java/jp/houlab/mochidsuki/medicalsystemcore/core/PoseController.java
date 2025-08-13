package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤーの姿勢制御を一元管理するクラス
 * 複数のシステム（意識障害、ストレッチャー等）からの姿勢制御要求を調整し、
 * 適切な優先度で姿勢を設定・解除する
 */
public class PoseController {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 姿勢制御の理由（優先度順）
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
     * プレイヤーごとの姿勢制御状態
     */
    private static class PoseControlState {
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
    private static final Map<UUID, PoseControlState> playerStates = new HashMap<>();

    /**
     * プレイヤーの姿勢制御を設定
     */
    public static void setPoseControl(ServerPlayer player, PoseReason reason, boolean active) {
        UUID playerId = player.getUUID();
        PoseControlState state = playerStates.computeIfAbsent(playerId, k -> new PoseControlState());

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
                LOGGER.debug("Applied pose control: {} -> {} (reason: {})",
                        player.getName().getString(), newPose, state.getCurrentReason());
            } else {
                // 姿勢制御を解除
                releasePoseControl(player);
                LOGGER.debug("Released pose control: {}", player.getName().getString());
            }
        }
    }

    /**
     * 姿勢制御を適用
     */
    private static void applyPoseControl(ServerPlayer player, Pose targetPose, PoseReason reason) {
        // ストレッチャーの場合は特別な処理
        if (reason == PoseReason.STRETCHER) {
            applyStretcherPose(player, targetPose);
        } else {
            // 通常の姿勢制御
            player.setPose(targetPose);
            player.setForcedPose(targetPose);
        }

        // 全クライアントに姿勢変更を強制同期
        forceSyncPoseToClients(player);
    }

    /**
     * ストレッチャー用の特別な姿勢制御
     */
    private static void applyStretcherPose(ServerPlayer player, Pose targetPose) {
        // ストレッチャーエンティティから向きを取得
        if (player.getVehicle() instanceof StretcherEntity stretcher) {
            float yaw = stretcher.getYRot();


            // 体の向きを担架に合わせる
            player.setYRot(yaw);
            player.setXRot(0);
            player.yBodyRot = StretcherEntity.calculatePlayerBodyYaw(yaw);
            player.yBodyRotO = yaw;
            player.setYHeadRot(yaw);
            player.xRotO = 0;
        }

        // 姿勢を設定
        player.setPose(targetPose);
        player.setForcedPose(targetPose);
    }

    /**
     * 姿勢制御を解除
     */
    private static void releasePoseControl(ServerPlayer player) {
        // 強制姿勢を解除
        player.setForcedPose(null);

        // 自然な姿勢に戻す
        player.setPose(Pose.STANDING);

        // 全クライアントに姿勢解除を強制同期
        forceSyncPoseToClients(player);
    }

    /**
     * 姿勢変更を全クライアントに強制同期
     */
    private static void forceSyncPoseToClients(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        // ディメンション更新（姿勢変更による当たり判定変更を反映）
        player.refreshDimensions();

        // エンティティデータの同期
        serverLevel.getChunkSource().broadcast(player,
                new ClientboundSetEntityDataPacket(
                        player.getId(),
                        player.getEntityData().packDirty()
                )
        );

        // 位置・回転の同期（ストレッチャーの場合など）
        serverLevel.getChunkSource().broadcast(player,
                new ClientboundTeleportEntityPacket(player)
        );
    }

    /**
     * プレイヤーの現在の姿勢制御状態を取得
     */
    public static boolean isControlled(ServerPlayer player) {
        PoseControlState state = playerStates.get(player.getUUID());
        return state != null && state.isControlled();
    }

    /**
     * 特定の理由による姿勢制御が有効かどうか
     */
    public static boolean isControlledBy(ServerPlayer player, PoseReason reason) {
        PoseControlState state = playerStates.get(player.getUUID());
        return state != null && state.getCurrentReason() == reason;
    }

    /**
     * プレイヤーの姿勢制御を完全にクリア（ログアウト時など）
     */
    public static void clearPoseControl(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PoseControlState state = playerStates.get(playerId);

        if (state != null && state.isControlled()) {
            releasePoseControl(player);
        }

        playerStates.remove(playerId);
        LOGGER.debug("Cleared pose control for player: {}", player.getName().getString());
    }

    /**
     * 意識障害システム用のヘルパーメソッド
     */
    public static void setUnconsciousPose(ServerPlayer player, boolean unconscious) {
        setPoseControl(player, PoseReason.UNCONSCIOUS, unconscious);
    }

    /**
     * ストレッチャーシステム用のヘルパーメソッド
     */
    public static void setStretcherPose(ServerPlayer player, boolean onStretcher) {
        setPoseControl(player, PoseReason.STRETCHER, onStretcher);
    }

    /**
     * 定期的な姿勢制御の維持（他のシステムによる上書きを防ぐ）
     */
    public static void maintainPoseControl(ServerPlayer player) {
        PoseControlState state = playerStates.get(player.getUUID());
        if (state == null || !state.isControlled()) return;

        Pose targetPose = state.getTargetPose();
        if (player.getPose() != targetPose || player.getForcedPose() != targetPose) {
            // 姿勢が変更されてしまった場合は再適用
            applyPoseControl(player, targetPose, state.getCurrentReason());
            LOGGER.debug("Restored pose control for player: {} -> {}",
                    player.getName().getString(), targetPose);
        }
    }
}