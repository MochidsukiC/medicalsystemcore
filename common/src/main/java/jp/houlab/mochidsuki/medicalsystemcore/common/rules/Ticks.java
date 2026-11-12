package jp.houlab.mochidsuki.medicalsystemcore.common.rules;

/** 秒・分を tick(1 秒 = 20 tick)へ換算する。設定の単位換算を 1 か所に集約する。 */
public final class Ticks {
    public static final int PER_SECOND = 20;
    public static final int PER_MINUTE = 60 * PER_SECOND;

    private Ticks() {
    }

    public static int fromSeconds(int seconds) {
        return seconds * PER_SECOND;
    }

    public static int fromMinutes(int minutes) {
        return minutes * PER_MINUTE;
    }
}
