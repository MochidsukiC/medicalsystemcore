package jp.houlab.mochidsuki.medicalsystemcore.common.rules;

/** 点滴(パック残量・有効範囲)に関する計算。 */
public final class IvRules {
    /** 点滴の薬効エフェクトを付与する持続 tick(毎 tick 付け直す)。 */
    public static final int EFFECT_DURATION_TICKS = 40;
    /** ブドウ糖の満腹度エフェクトの持続 tick。 */
    public static final int GLUCOSE_EFFECT_DURATION_TICKS = 100;

    private IvRules() {
    }

    /** パック 1 個ぶんの容量(tick)。 */
    public static int packCapacityTicks(int packDurationSeconds) {
        return Ticks.fromSeconds(packDurationSeconds);
    }

    /** 点滴の有効範囲内か(距離の 2 乗で比較)。 */
    public static boolean isInRange(double distanceSq, double range) {
        return distanceSq < range * range;
    }

    /** パック 1 スロットの 1 tick の処理内容。 */
    public enum PackAction {
        /** 残量を 1 tick 減らして薬効を与える。 */
        DRAIN,
        /** スタックから 1 個消費し、新しいパックを満タンにして薬効を与える。 */
        REFILL_FROM_STACK,
        /** 最後の 1 個が空になったのでスロットを空にする。 */
        REMOVE_EMPTY
    }

    public record PackStep(PackAction action, int newRemainingTicks) {
    }

    /**
     * パックの残量とスタック数から、1 tick の処理内容と新しい残量を決める。
     * 残量が空のとき、スタックに 2 個以上あれば 1 個消費して満タンに戻す。
     */
    public static PackStep stepPack(int remainingTicks, int stackCount, int capacityTicks) {
        if (remainingTicks > 0) {
            return new PackStep(PackAction.DRAIN, remainingTicks - 1);
        }
        if (stackCount > 1) {
            return new PackStep(PackAction.REFILL_FROM_STACK, capacityTicks);
        }
        return new PackStep(PackAction.REMOVE_EMPTY, 0);
    }
}
