package jp.houlab.mochidsuki.medicalsystemcore.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * ストレッチャーの位置計算を行う共通ユーティリティクラス
 * サーバーサイドとクライアントサイドで同じ計算ロジックを使用するため
 */
public class StretcherPositionCalculator {

    // ストレッチャーの設定値
    private static final double STRETCHER_DISTANCE = 1.2;  // 運搬者からの距離
    private static final float STRETCHER_YAW_OFFSET = 90.0f;  // 向きのオフセット（垂直）

    // 補間設定
    private static final double POSITION_LERP_FACTOR = 0.3;  // 位置補間係数
    private static final float ROTATION_LERP_FACTOR = 0.2f;   // 回転補間係数

    /**
     * 運搬者の位置と向きから、ストレッチャーの目標位置を計算
     * @param carrier 運搬者
     * @return ストレッチャーの目標位置
     */
    public static Vec3 calculateTargetPosition(Player carrier) {
        if (carrier == null) return Vec3.ZERO;

        Vec3 carrierPos = carrier.position();
        float carrierYaw = carrier.getYRot();

        // 前方への位置計算
        double radians = Math.toRadians(carrierYaw);
        double offsetX = -Math.sin(radians) * STRETCHER_DISTANCE;
        double offsetZ = Math.cos(radians) * STRETCHER_DISTANCE;

        return carrierPos.add(offsetX, 0, offsetZ);
    }

    /**
     * 運搬者の向きからストレッチャーの目標向きを計算
     * @param carrier 運搬者
     * @return ストレッチャーの目標向き
     */
    public static float calculateTargetYaw(Player carrier) {
        if (carrier == null) return 0.0f;
        return carrier.getYRot() + STRETCHER_YAW_OFFSET;
    }

    /**
     * 現在位置から目標位置への補間位置を計算
     * @param currentPos 現在位置
     * @param targetPos 目標位置
     * @param immediate 即座に移動するかどうか
     * @return 補間された位置
     */
    public static Vec3 interpolatePosition(Vec3 currentPos, Vec3 targetPos, boolean immediate) {
        if (immediate) {
            return targetPos;
        }

        double newX = currentPos.x + (targetPos.x - currentPos.x) * POSITION_LERP_FACTOR;
        double newY = targetPos.y; // Y座標は即座に更新
        double newZ = currentPos.z + (targetPos.z - currentPos.z) * POSITION_LERP_FACTOR;

        return new Vec3(newX, newY, newZ);
    }

    /**
     * 現在の向きから目標向きへの補間角度を計算
     * @param currentYaw 現在の向き
     * @param targetYaw 目標の向き
     * @param immediate 即座に回転するかどうか
     * @return 補間された角度
     */
    public static float interpolateYaw(float currentYaw, float targetYaw, boolean immediate) {
        if (immediate) {
            return targetYaw;
        }

        float yawDiff = targetYaw - currentYaw;

        // 角度の正規化（-180度から180度の範囲に調整）
        while (yawDiff > 180.0f) yawDiff -= 360.0f;
        while (yawDiff < -180.0f) yawDiff += 360.0f;

        return currentYaw + yawDiff * ROTATION_LERP_FACTOR;
    }

    /**
     * ストレッチャーの完全な位置・向き情報を計算
     * @param carrier 運搬者
     * @param currentPos 現在の位置
     * @param currentYaw 現在の向き
     * @param immediate 即座に更新するかどうか（主にクライアントサイドで使用）
     * @return 計算結果
     */
    public static PositionResult calculateStretcherTransform(Player carrier, Vec3 currentPos, float currentYaw, boolean immediate) {
        Vec3 targetPos = calculateTargetPosition(carrier);
        float targetYaw = calculateTargetYaw(carrier);

        Vec3 newPos = interpolatePosition(currentPos, targetPos, immediate);
        float newYaw = interpolateYaw(currentYaw, targetYaw, immediate);

        return new PositionResult(newPos, newYaw, targetPos, targetYaw);
    }

    /**
     * 位置計算の結果を格納するクラス
     */
    public static class PositionResult {
        public final Vec3 position;      // 補間された位置
        public final float yaw;          // 補間された向き
        public final Vec3 targetPosition; // 目標位置
        public final float targetYaw;    // 目標向き

        public PositionResult(Vec3 position, float yaw, Vec3 targetPosition, float targetYaw) {
            this.position = position;
            this.yaw = yaw;
            this.targetPosition = targetPosition;
            this.targetYaw = targetYaw;
        }
    }
}