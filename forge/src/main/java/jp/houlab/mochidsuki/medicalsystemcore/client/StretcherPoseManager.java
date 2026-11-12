
package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import jp.houlab.mochidsuki.medicalsystemcore.util.AngleUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ストレッチャー姿勢管理 - レンダリング拡張版
 */
public class StretcherPoseManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, StretcherPoseData> playerPoses = new HashMap<>();

    /**
     * ストレッチャー姿勢データ（拡張版）
     */
    private static class StretcherPoseData {
        float targetYaw;
        float currentYaw;
        float renderYaw;  // レンダリング用の滑らかな角度
        boolean isOnStretcher;
        boolean shouldRenderCustom;  // カスタムレンダリングを行うか
        int tickCount = 0;

        StretcherPoseData(float yaw, boolean onStretcher) {
            this.targetYaw = yaw;
            this.currentYaw = yaw;
            this.renderYaw = yaw;
            this.isOnStretcher = onStretcher;
            this.shouldRenderCustom = onStretcher;
        }
    }

    /**
     * プレイヤーのストレッチャー姿勢を設定
     */
    public static void setStretcherPose(Player player, boolean onStretcher, float stretcherYaw) {
        if (player == null) return;

        UUID playerId = player.getUUID();

        if (onStretcher) {
            StretcherPoseData data = playerPoses.get(playerId);
            if (data == null) {
                data = new StretcherPoseData(stretcherYaw, true);
                playerPoses.put(playerId, data);
                LOGGER.info("Started custom stretcher rendering for player: {}", player.getName().getString());
            } else {
                data.targetYaw = stretcherYaw;
                data.isOnStretcher = true;
                data.shouldRenderCustom = true;
            }

            // プレイヤーの姿勢をSTANDINGに設定（カスタムレンダリングで制御）
            player.setPose(Pose.STANDING);

        } else {
            StretcherPoseData data = playerPoses.get(playerId);
            if (data != null) {
                data.isOnStretcher = false;
                data.shouldRenderCustom = false;
            }
            player.setPose(Pose.STANDING);
            LOGGER.info("Stopped custom stretcher rendering for player: {}", player.getName().getString());
        }
    }

    /**
     * カスタムレンダリングが必要かチェック
     */
    public static boolean shouldCustomRender(Player player) {
        if (player == null) return false;

        StretcherPoseData data = playerPoses.get(player.getUUID());
        return data != null && data.shouldRenderCustom && data.isOnStretcher;
    }

    /**
     * レンダリング用の角度を取得
     */
    public static float getRenderYaw(Player player) {
        if (player == null) return 0.0f;

        StretcherPoseData data = playerPoses.get(player.getUUID());
        return data != null ? data.renderYaw : 0.0f;
    }

    /**
     * ストレッチャー姿勢の角度を更新
     */
    public static void updateStretcherYaw(Player player, float newYaw) {
        if (player == null) return;

        StretcherPoseData data = playerPoses.get(player.getUUID());
        if (data != null && data.isOnStretcher) {
            data.targetYaw = AngleUtils.normalizeAngle(newYaw);
        }
    }

    /**
     * 毎tick呼び出される更新処理（拡張版）
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // 全プレイヤーの姿勢データを更新
        playerPoses.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            StretcherPoseData data = entry.getValue();

            Player player = mc.level.getPlayerByUUID(playerId);
            if (player == null) {
                return true; // プレイヤーが存在しない場合は削除
            }

            // ストレッチャーに実際に乗っているかチェック
            boolean actuallyOnStretcher = player.getVehicle() instanceof StretcherEntity;

            if (!actuallyOnStretcher && data.isOnStretcher) {
                // ストレッチャーから降りた
                data.isOnStretcher = false;
                data.shouldRenderCustom = false;
                player.setPose(Pose.STANDING);
                return true; // データを削除
            }

            // レンダリング用角度の滑らかな補間
            if (data.isOnStretcher) {
                // 目標角度への補間
                if (Math.abs(AngleUtils.getAngleDifference(data.currentYaw, data.targetYaw)) > 1.0f) {
                    data.currentYaw = AngleUtils.lerpAngle(data.currentYaw, data.targetYaw, 0.1f);
                }

                // レンダリング用のさらに滑らかな補間
                if (Math.abs(AngleUtils.getAngleDifference(data.renderYaw, data.currentYaw)) > 0.5f) {
                    data.renderYaw = AngleUtils.lerpAngle(data.renderYaw, data.currentYaw, 0.2f);
                }
            }

            data.tickCount++;
            return false; // データを保持
        });
    }

    /**
     * プレイヤーがストレッチャーに乗っているかチェック
     */
    public static boolean isOnStretcher(Player player) {
        if (player == null) return false;

        StretcherPoseData data = playerPoses.get(player.getUUID());
        return data != null && data.isOnStretcher;
    }

    /**
     * 全データをクリア
     */
    public static void clear() {
        playerPoses.clear();
    }

    /**
     * デバッグ情報を取得
     */
    public static String getDebugInfo(Player player) {
        if (player == null) return "No player";

        StretcherPoseData data = playerPoses.get(player.getUUID());
        if (data == null) return "No data";

        return String.format("Target: %.1f°, Current: %.1f°, Render: %.1f°, OnStretcher: %s, CustomRender: %s",
                data.targetYaw, data.currentYaw, data.renderYaw, data.isOnStretcher, data.shouldRenderCustom);
    }
}