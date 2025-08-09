package jp.houlab.mochidsuki.medicalsystemcore.core;


/**
 * プレイヤーの心臓の状態を定義するEnum
 * NORMAL: 正常
 * VF: 心室細動 (Ventricular Fibrillation)
 * CARDIAC_ARREST: 心停止
 */

public enum HeartStatus {
    NORMAL,
    VF,
    CARDIAC_ARREST
}
