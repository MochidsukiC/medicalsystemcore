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
