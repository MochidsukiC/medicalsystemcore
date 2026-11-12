package jp.houlab.mochidsuki.medicalsystemcore.common.rules;

/** 包帯エフェクトのレベル更新と時間換算。自分への使用と他人への使用で共通。 */
public final class BandageRules {
    private BandageRules() {
    }

    /** エフェクトの amplifier から包帯レベルを得る(レベル = amplifier + 1)。 */
    public static int levelFromAmplifier(int amplifier) {
        return amplifier + 1;
    }

    /** エフェクトに設定する amplifier(レベル - 1)。 */
    public static int amplifierForLevel(int level) {
        return level - 1;
    }

    /** 使用後の包帯レベル(最大レベルで頭打ち)。 */
    public static int nextLevel(int currentLevel, int levelIncrease, int maxLevel) {
        return Math.min(maxLevel, currentLevel + levelIncrease);
    }

    /** 秒を tick へ換算する(使用時間・エフェクト持続時間)。 */
    public static int durationTicks(int seconds) {
        return Ticks.fromSeconds(seconds);
    }
}
