package jp.houlab.mochidsuki.medicalsystemcore.common.rules;

import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.QTEResult;

import java.util.random.RandomGenerator;

/**
 * 除細動の成功判定と状態遷移。QTE を経る経路と経ない経路で同じ判定を共有する。
 */
public final class DefibrillationRules {
    /** 蘇生成功後の蘇生確率。 */
    public static final float CHANCE_AFTER_SUCCESS = 100.0f;

    private DefibrillationRules() {
    }

    /** 判定に使う設定値。 */
    public record Params(double vfSuccessMultiplier, double transitionChance,
                         double qteGreatSuccessBonus, double qteSuccessBonus) {
        public static Params fromConfig() {
            return new Params(
                    MedicalConfig.get(MedicalConfigKeys.DEFIBRILLATOR_VF_SUCCESS_MULTIPLIER),
                    MedicalConfig.get(MedicalConfigKeys.DEFIBRILLATOR_TRANSITION_CHANCE),
                    MedicalConfig.get(MedicalConfigKeys.QTE_GREAT_SUCCESS_BONUS),
                    MedicalConfig.get(MedicalConfigKeys.QTE_SUCCESS_BONUS));
        }
    }

    /** 判定の結果種別。 */
    public enum Outcome {
        /** 蘇生成功。 */
        SUCCESS,
        /** 蘇生失敗で、心停止と VF の間で状態が移行した。 */
        FAILED_TRANSITIONED,
        /** 蘇生失敗で、状態は変わらない。 */
        FAILED,
        /** QTE が失敗範囲だったため判定そのものを行わなかった。 */
        QTE_FAILED,
        /** 心臓が正常で、除細動の対象ではない。 */
        NOT_APPLICABLE
    }

    /**
     * 判定結果と、呼び出し側が適用すべき状態。
     *
     * @param newStatus              判定後の心臓の状態
     * @param newResuscitationChance 判定後の蘇生確率
     * @param resetArrestTimer       心停止タイマーを 0 に戻すか
     * @param clearDamageImmune      ダメージ無効(心停止中の保護)を解除するか
     */
    public record Result(Outcome outcome, HeartStatus newStatus, float newResuscitationChance,
                         boolean resetArrestTimer, boolean clearDamageImmune) {
        public boolean isSuccess() {
            return outcome == Outcome.SUCCESS;
        }
    }

    /**
     * QTE を経る経路の判定。QTE が FAIL なら判定せず終了。
     * GREAT_SUCCESS / SUCCESS は、設定の補正値(%)を蘇生確率に加えて判定する。
     *
     * @param rng 乱数。成功判定に 1 回、失敗時の移行判定にもう 1 回、必要なときだけ消費する
     */
    public static Result attemptWithQte(HeartStatus status, float chance, QTEResult qte, Params params, RandomGenerator rng) {
        return switch (qte) {
            case FAIL -> new Result(Outcome.QTE_FAILED, status, chance, false, false);
            case GREAT_SUCCESS -> judge(status, chance + params.qteGreatSuccessBonus(), chance, params, rng);
            case SUCCESS -> judge(status, chance + params.qteSuccessBonus(), chance, params, rng);
        };
    }

    /** QTE を経ない経路の判定(補正なし)。 */
    public static Result attemptWithoutQte(HeartStatus status, float chance, Params params, RandomGenerator rng) {
        return judge(status, chance, chance, params, rng);
    }

    private static Result judge(HeartStatus status, double effectiveChance, float originalChance,
                                Params params, RandomGenerator rng) {
        double successProbability;
        HeartStatus transitionTarget;
        switch (status) {
            case CARDIAC_ARREST -> {
                successProbability = effectiveChance / 100.0;
                transitionTarget = HeartStatus.VF;
            }
            case VF -> {
                successProbability = (effectiveChance * params.vfSuccessMultiplier()) / 100.0;
                transitionTarget = HeartStatus.CARDIAC_ARREST;
            }
            default -> {
                return new Result(Outcome.NOT_APPLICABLE, status, originalChance, false, false);
            }
        }

        if (rng.nextDouble() < successProbability) {
            return new Result(Outcome.SUCCESS, HeartStatus.NORMAL, CHANCE_AFTER_SUCCESS, true, true);
        }
        if (rng.nextDouble() < params.transitionChance()) {
            return new Result(Outcome.FAILED_TRANSITIONED, transitionTarget, originalChance, false, false);
        }
        return new Result(Outcome.FAILED, status, originalChance, false, false);
    }
}
