package jp.houlab.mochidsuki.medicalsystemcore.common.rules;

import jp.houlab.mochidsuki.medicalsystemcore.common.model.QTEResult;

/** 除細動 QTE(スキルチェック)のバー区間と判定。クライアントがこの判定を行い、結果をサーバーへ送る。 */
public final class QteRules {
    /** バー位置は 0.0 〜 1.0。成功範囲の開始。 */
    public static final float SUCCESS_START = 0.6f;
    public static final float GREAT_SUCCESS_START = 0.75f;
    public static final float GREAT_SUCCESS_END = 0.85f;
    public static final float SUCCESS_END = 1.0f;
    /** バーが 1 tick に進む量。 */
    public static final float BAR_SPEED_PER_TICK = 0.02f;

    /**
     * バーが 0.0 から出発して SUCCESS_END を超える(行き過ぎる)までにかかる最大 tick 数。
     * クライアントは 1 tick に 1 回(PlayerTickEvent END)だけバーを進める。+1 は浮動小数の累積誤差の余裕。
     */
    public static final int MAX_DURATION_TICKS = (int) Math.ceil(SUCCESS_END / BAR_SPEED_PER_TICK) + 1;
    /**
     * 通信の遅れの余裕(tick)。サーバーが QTE を開始してから結果が届くまでには、開始パケットの往路、
     * 結果パケットの復路、サーバーとクライアントの tick のずれが加わる。2 秒あれば通常の回線の揺らぎを吸収できる。
     */
    public static final int LATENCY_MARGIN_TICKS = 40;
    /** サーバーが QTE の結果を待つ最大 tick 数。これを過ぎた開始記録は破棄され、除細動器のチャージは解除される。 */
    public static final int RESULT_TIMEOUT_TICKS = MAX_DURATION_TICKS + LATENCY_MARGIN_TICKS;
    /**
     * 結果を受け付けるときの、施術者と患者の最大距離(ブロック)。
     * ElectrodeItem 自体には距離条件がなく、開始は Minecraft のエンティティ操作の判定(バニラ上限 6 ブロック)に依存する。
     * それに合わせて同じ値を結果の受付条件にする。
     */
    public static final double MAX_DISTANCE = 6.0;

    private QteRules() {
    }

    /** バー位置から結果を判定する。大成功の範囲は成功範囲の内側にあり、大成功を優先する。 */
    public static QTEResult judge(float barPosition) {
        if (barPosition >= GREAT_SUCCESS_START && barPosition <= GREAT_SUCCESS_END) {
            return QTEResult.GREAT_SUCCESS;
        } else if (barPosition >= SUCCESS_START && barPosition <= SUCCESS_END) {
            return QTEResult.SUCCESS;
        }
        return QTEResult.FAIL;
    }

    /** バーが行き過ぎて無条件で失敗になる位置か。 */
    public static boolean isOvershoot(float barPosition) {
        return barPosition > SUCCESS_END;
    }
}
