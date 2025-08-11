package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;

/**
 * クライアントサイドでプレイヤーの医療データをまとめて保持するためのクラス
 */
public class ClientMedicalData {
    public float bloodLevel;
    public HeartStatus heartStatus;
    public float bleedingSpeed;
    public float resuscitationChance;

    public float leadI;
    public float leadII;
    public float leadIII;
    // 他にも表示したいステータスがあればここに追加

    public ClientMedicalData() {
        // 初期値
        this.bloodLevel = 100.0f;
        this.heartStatus = HeartStatus.NORMAL;
        this.bleedingSpeed = 0;
        this.resuscitationChance = 100.0f;

        this.leadI = 0.0f;
        this.leadII = 0.0f;
        this.leadIII = 0.0f;
    }
}