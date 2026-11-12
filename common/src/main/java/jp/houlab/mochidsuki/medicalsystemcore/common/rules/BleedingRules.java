package jp.houlab.mochidsuki.medicalsystemcore.common.rules;

import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;

/** 出血に関する計算(被ダメージによる増加・包帯による回復・出血エフェクトのレベル)。 */
public final class BleedingRules {
    /**
     * 出血エフェクトの amplifier の上限。
     * エフェクトの amplifier は同期パケット(ClientboundUpdateMobEffectPacket)と NBT 保存の双方で
     * byte(符号付き)として扱われるため、128 以上は負の値に化ける。表示専用の値なのでこの値で止める。
     * 出血速度(ゲームロジック側)には上限を設けない。
     */
    public static final int MAX_EFFECT_AMPLIFIER = 127;

    /** 出血エフェクトの持続 tick。毎 tick 付け直して延長する。 */
    public static final int EFFECT_DURATION_TICKS = 40;

    private BleedingRules() {
    }

    /** 被ダメージによる出血速度の増加量の設定。 */
    public record IncreaseTable(double chanceLow, double low, double mid, double high, double extreme) {
        public static IncreaseTable fromConfig() {
            return new IncreaseTable(
                    MedicalConfig.get(MedicalConfigKeys.BLEEDING_CHANCE_LOW_DAMAGE),
                    MedicalConfig.get(MedicalConfigKeys.BLEEDING_SPEED_INCREASE_LOW),
                    MedicalConfig.get(MedicalConfigKeys.BLEEDING_SPEED_INCREASE_MID),
                    MedicalConfig.get(MedicalConfigKeys.BLEEDING_SPEED_INCREASE_HIGH),
                    MedicalConfig.get(MedicalConfigKeys.BLEEDING_SPEED_INCREASE_EXTREME));
        }
    }

    /**
     * 被ダメージで増える出血速度(増えなければ 0)。
     *
     * @param damage ダメージ量。0.1 以下は出血なし
     * @param roll   0 以上 1 未満の乱数。軽傷(0.1 超 2 以下)の出血発生判定にだけ使う
     */
    public static double speedIncrease(float damage, double roll, IncreaseTable table) {
        if (damage > 0.1f && damage <= 2f) {
            return roll < table.chanceLow() ? table.low() : 0;
        } else if (damage > 2f && damage <= 5f) {
            return table.mid();
        } else if (damage > 5f && damage <= 10f) {
            return table.high();
        } else if (damage > 10f) {
            return table.extreme();
        }
        return 0;
    }

    /**
     * 包帯による出血速度の回復を 1 tick ぶん適用した出血速度。
     * 回復量/秒 = 基本レート × (包帯レベル × 包帯倍率)。
     *
     * @param bandageLevel 包帯エフェクトのレベル(無ければ 0)
     */
    public static float recover(float speed, int bandageLevel, double baseRate, double bandageMultiplier) {
        if (speed <= 0) {
            return speed;
        }
        double recoveryRatePerSecond = baseRate * (bandageLevel * bandageMultiplier);
        if (recoveryRatePerSecond <= 0) {
            return speed;
        }
        double recoveryPerTick = recoveryRatePerSecond / 20.0;
        return Math.max(0, speed - (float) recoveryPerTick);
    }

    /** 出血速度に対応する出血エフェクトのレベル(1 以上)。出血速度 &gt; 0 のときだけ意味を持つ。 */
    public static int effectLevel(float bleedingSpeed) {
        return (int) Math.ceil(bleedingSpeed);
    }

    /** 出血エフェクトに設定する amplifier(レベル - 1、{@link #MAX_EFFECT_AMPLIFIER} 以下)。 */
    public static int effectAmplifier(float bleedingSpeed) {
        return Math.min(effectLevel(bleedingSpeed) - 1, MAX_EFFECT_AMPLIFIER);
    }
}
