package jp.houlab.mochidsuki.medicalsystemcore.common.rules;

import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;

/** 血液量に関する計算(失血・自然回復・輸血・失血量によるダメージ)。 */
public final class BloodRules {
    private BloodRules() {
    }

    /** 失血量ダメージの閾値表。 */
    public record DamageTable(double threshold85, double threshold70, double threshold65, double threshold60,
                              double damage85to70, double damage70to65, double damage65to60, double damageBelow60,
                              int interval) {
        public static DamageTable fromConfig() {
            return new DamageTable(
                    MedicalConfig.get(MedicalConfigKeys.BLOOD_DAMAGE_THRESHOLD_85),
                    MedicalConfig.get(MedicalConfigKeys.BLOOD_DAMAGE_THRESHOLD_70),
                    MedicalConfig.get(MedicalConfigKeys.BLOOD_DAMAGE_THRESHOLD_65),
                    MedicalConfig.get(MedicalConfigKeys.BLOOD_DAMAGE_THRESHOLD_60),
                    MedicalConfig.get(MedicalConfigKeys.BLOOD_DAMAGE_85_70),
                    MedicalConfig.get(MedicalConfigKeys.BLOOD_DAMAGE_70_65),
                    MedicalConfig.get(MedicalConfigKeys.BLOOD_DAMAGE_65_60),
                    MedicalConfig.get(MedicalConfigKeys.BLOOD_DAMAGE_BELOW_60),
                    MedicalConfig.get(MedicalConfigKeys.BLOOD_DAMAGE_INTERVAL));
        }
    }

    /**
     * 失血量によるダメージ量を返す(0 ならダメージなし)。
     * 心臓が正常な間だけ、interval tick ごとに血液量(0-100)の閾値で段階的に決まる。
     */
    public static double damage(HeartStatus status, int tickCounter, float blood, DamageTable table) {
        if (status != HeartStatus.NORMAL) {
            return 0;
        }
        if (tickCounter % table.interval() != 0) {
            return 0;
        }
        if (blood < table.threshold85() && blood >= table.threshold70()) {
            return table.damage85to70();
        } else if (blood < table.threshold70() && blood >= table.threshold65()) {
            return table.damage70to65();
        } else if (blood < table.threshold65() && blood >= table.threshold60()) {
            return table.damage65to60();
        } else if (blood < table.threshold60()) {
            return table.damageBelow60();
        }
        return 0;
    }

    /**
     * 出血中の 1 tick あたりの失血量(出血速度 &gt; 0 のとき)。
     * 心臓が正常でなければ出血は弱まる(血液量 30 未満ではさらに 1/10)。
     */
    public static float lossPerTick(HeartStatus status, float bleedingSpeed, float blood) {
        if (status == HeartStatus.NORMAL) {
            return bleedingSpeed / 60.0f / 20.0f;
        }
        if (blood < 30.0f) {
            return bleedingSpeed / 200.0f / 60.0f / 20.0f;
        }
        return bleedingSpeed / 20.0f / 60.0f / 20.0f;
    }

    /** 出血による失血、または出血していないときの自然回復を 1 tick ぶん適用した血液量。 */
    public static float nextBlood(HeartStatus status, float blood, float bleedingSpeed, double naturalRecoveryRatePerSecond) {
        if (bleedingSpeed > 0) {
            return Math.max(0, blood - lossPerTick(status, bleedingSpeed, blood));
        }
        float recoveryPerTick = (float) (naturalRecoveryRatePerSecond / 20.0);
        return Math.min(100.0f, blood + recoveryPerTick);
    }

    /** 輸血を 1 tick ぶん適用した血液量。 */
    public static float transfuse(float blood, double recoveryRatePerSecond) {
        double recoveryPerTick = recoveryRatePerSecond / 20.0;
        return Math.min(100.0f, blood + (float) recoveryPerTick);
    }
}
