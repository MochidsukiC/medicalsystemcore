package jp.houlab.mochidsuki.medicalsystemcore.common.rules;

import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;

/** 心停止・VF の進行(強制死亡と蘇生確率の減衰)と意識判定。 */
public final class CardiacRules {
    private CardiacRules() {
    }

    /**
     * 心停止/VF の 1 tick ぶんの進行結果。
     *
     * @param timer  更新後のタイマー(心停止/VF になってからの経過 tick)
     * @param chance 更新後の蘇生確率(0 未満の丸めは呼び出し側のデータ型が行う)
     * @param dead   強制死亡させるか
     */
    public record Progress(int timer, float chance, boolean dead) {
    }

    /**
     * 心臓が正常でない間の 1 tick ぶんの進行を計算する。
     * タイマーを先に +1 し、その経過 tick で判定する(心停止になって n 番目の tick で経過 n)。
     * <ul>
     *   <li>経過 tick が死亡時間を超えるか血液量 0 以下なら死亡。</li>
     *   <li>心停止(CARDIAC_ARREST)のときのみ、経過 tick が 1 分の倍数になる tick で蘇生確率を減衰。0 以下なら死亡。
     *       開始直後の最初の tick(経過 1)では減衰しない。</li>
     * </ul>
     * 心臓が正常なら何も変えない。
     */
    public static Progress advance(HeartStatus status, int timer, float blood, float chance,
                                   int deathTimeMinutes, double decayRatePerMinute) {
        if (status == HeartStatus.NORMAL) {
            return new Progress(timer, chance, false);
        }
        int elapsed = timer + 1;
        if (elapsed > Ticks.fromMinutes(deathTimeMinutes) || blood <= 0) {
            return new Progress(elapsed, chance, true);
        }
        if (status == HeartStatus.CARDIAC_ARREST && elapsed % Ticks.PER_MINUTE == 0) {
            float decayed = chance - (float) decayRatePerMinute;
            return new Progress(elapsed, decayed, decayed <= 0);
        }
        return new Progress(elapsed, chance, false);
    }

    /** 意識があるか。体力が 5 未満、または心臓が正常でなければ意識なし。 */
    public static boolean isConscious(float health, HeartStatus status) {
        return !(health < 5 || status != HeartStatus.NORMAL);
    }
}
