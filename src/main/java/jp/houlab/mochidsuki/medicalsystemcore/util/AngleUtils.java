// AngleUtils.java の修正版

package jp.houlab.mochidsuki.medicalsystemcore.util;

/**
 * 角度処理に関するユーティリティクラス（修正版）
 * ストレッチャーでの角度反転問題を防ぐための安全な角度操作を提供
 */
public class AngleUtils {

    /**
     * 角度を-180度から180度の範囲に正規化
     * @param angle 正規化する角度
     * @return 正規化された角度
     */
    public static float normalizeAngle(float angle) {
        while (angle > 180.0f) {
            angle -= 360.0f;
        }
        while (angle < -180.0f) {
            angle += 360.0f;
        }
        return angle;
    }

    /**
     * 二つの角度間の最短差分を計算（修正版）
     * 反対方向回転を防ぐための改良版
     * @param from 開始角度
     * @param to 終了角度
     * @return 最短角度差分（-180度から180度の範囲）
     */
    public static float getAngleDifference(float from, float to) {
        float fromNorm = normalizeAngle(from);
        float toNorm = normalizeAngle(to);

        float diff = toNorm - fromNorm;

        // 最短経路を選択（修正：正しい符号を保持）
        if (diff > 180.0f) {
            diff -= 360.0f;
        } else if (diff < -180.0f) {
            diff += 360.0f;
        }

        return diff;
    }

    /**
     * 角度を安全に補間（線形補間）修正版
     * @param from 開始角度
     * @param to 終了角度
     * @param factor 補間係数（0.0f～1.0f）
     * @return 補間された角度
     */
    public static float lerpAngle(float from, float to, float factor) {
        factor = Math.max(0.0f, Math.min(1.0f, factor));
        float diff = getAngleDifference(from, to);
        return normalizeAngle(from + diff * factor);
    }

    /**
     * 角度変化が閾値以下かチェック
     * @param oldAngle 前の角度
     * @param newAngle 新しい角度
     * @param threshold 閾値（度）
     * @return 変化が閾値以下の場合true
     */
    public static boolean isAngleChangeSmall(float oldAngle, float newAngle, float threshold) {
        float diff = Math.abs(getAngleDifference(oldAngle, newAngle));
        return diff <= threshold;
    }

    /**
     * 角度を段階的に変更（修正版）- 反対方向回転を防ぐ
     * @param currentAngle 現在の角度
     * @param targetAngle 目標角度
     * @param maxChangePerTick 1tickあたりの最大変化量（度）
     * @return 段階的に変更された角度
     */
    public static float gradualAngleChange(float currentAngle, float targetAngle, float maxChangePerTick) {
        float diff = getAngleDifference(currentAngle, targetAngle);

        if (Math.abs(diff) <= maxChangePerTick) {
            return normalizeAngle(targetAngle);
        }

        // 修正：符号を正しく保持して段階的変更
        float change = Math.signum(diff) * maxChangePerTick;
        return normalizeAngle(currentAngle + change);
    }

    /**
     * 角度変化の安定性をチェック（視点操作での乱れを防ぐ）
     * @param oldAngle 前の角度
     * @param newAngle 新しい角度
     * @param consecutiveStableFrames 連続して安定している必要があるフレーム数
     * @return 安定している場合true
     */
    public static boolean isAngleStable(float oldAngle, float newAngle, int consecutiveStableFrames) {
        float diff = Math.abs(getAngleDifference(oldAngle, newAngle));
        return diff <= 1.0f; // 1度以下の変化は安定とみなす
    }

    /**
     * 高速回転を検出して防ぐ（修正版）
     * @param angleDifference 角度差分
     * @param maxAllowedChange 許可される最大変化量
     * @return 高速回転が検出された場合true
     */
    public static boolean isFastRotationDetected(float angleDifference, float maxAllowedChange) {
        return Math.abs(angleDifference) > maxAllowedChange;
    }

    /**
     * 角度が同じ方向を向いているかチェック（新規追加）
     * @param angle1 角度1
     * @param angle2 角度2
     * @param tolerance 許容誤差（度）
     * @return 同じ方向を向いている場合true
     */
    public static boolean isSameDirection(float angle1, float angle2, float tolerance) {
        float diff = Math.abs(getAngleDifference(angle1, angle2));
        return diff <= tolerance;
    }

    /**
     * デバッグ用：角度関係の情報を文字列で返す（新規追加）
     * @param carrierYaw 運搬者の角度
     * @param stretcherYaw 担架の角度
     * @param playerYaw プレイヤーの角度
     * @return デバッグ情報
     */
    public static String getAngleDebugInfo(float carrierYaw, float stretcherYaw, float playerYaw) {
        float expectedStretcherYaw = normalizeAngle(carrierYaw + 90.0f);
        boolean stretcherCorrect = isSameDirection(stretcherYaw, expectedStretcherYaw, 2.0f);
        boolean playerCorrect = isSameDirection(playerYaw, stretcherYaw, 2.0f);

        return String.format(
                "A(運搬者)=%.1f°, B(担架)=%.1f°(期待%.1f°%s), C(プレイヤー)=%.1f°%s",
                carrierYaw, stretcherYaw, expectedStretcherYaw,
                stretcherCorrect ? "✓" : "✗",
                playerYaw, playerCorrect ? "✓" : "✗"
        );
    }
}