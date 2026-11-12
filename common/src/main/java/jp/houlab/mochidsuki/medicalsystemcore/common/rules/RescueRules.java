package jp.houlab.mochidsuki.medicalsystemcore.common.rules;

/** 救助要請(通報)の入力制限。クライアントの入力欄とサーバーの検証で同じ値を使う。 */
public final class RescueRules {
    /** 通報メモの最大文字数。 */
    public static final int MEMO_MAX_LENGTH = 256;

    private RescueRules() {
    }
}
