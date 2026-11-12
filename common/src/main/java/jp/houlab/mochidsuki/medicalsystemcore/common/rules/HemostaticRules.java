package jp.houlab.mochidsuki.medicalsystemcore.common.rules;

/** 止血剤(フィブリノゲン・トラネキサム酸)による出血速度の減少。両者は設定値だけが違う同一の式。 */
public final class HemostaticRules {
    private HemostaticRules() {
    }

    /**
     * 出血速度を 1 tick ぶん減らした値(0 未満にならない)。
     * 減少量は出血速度に依らず、毎秒 {@code reductionPerSecond}(絶対値)× エフェクトレベル。
     *
     * @param amplifier エフェクトの amplifier(レベル = amplifier + 1)
     */
    public static float reduceBleeding(float currentSpeed, double reductionPerSecond, int amplifier) {
        double reductionPerTick = reductionPerSecond / 20.0;
        double adjustedReduction = reductionPerTick * (amplifier + 1);
        return Math.max(0.0f, currentSpeed - (float) adjustedReduction);
    }

    /** アドレナリンによる蘇生確率の回復を 1 tick ぶん適用した値(上限 100)。 */
    public static float recoverResuscitationChance(float currentChance, double recoveryRatePerSecond) {
        double recoveryPerTick = recoveryRatePerSecond / 20.0;
        return Math.min(100.0f, currentChance + (float) recoveryPerTick);
    }
}
