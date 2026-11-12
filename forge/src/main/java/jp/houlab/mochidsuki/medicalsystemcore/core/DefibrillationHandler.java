package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.capability.IPlayerMedicalData;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.QTEResult;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.DefibrillationRules;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 除細動の判定結果を患者の医療データへ適用する。
 * 除細動は QTE を経る経路(ServerboundQTETResultPacket)だけで行われる。
 * 成功時の副作用(状態・蘇生確率・タイマー・damageImmune の解除)はここで適用する。
 * メッセージ表示とクライアントへの同期は呼び出し側が行う。
 */
public final class DefibrillationHandler {
    private DefibrillationHandler() {
    }

    /** QTE を経る経路で判定して適用する。 */
    public static DefibrillationRules.Result shockWithQte(IPlayerMedicalData medicalData, QTEResult qte) {
        DefibrillationRules.Result result = DefibrillationRules.attemptWithQte(
                medicalData.getHeartStatus(), medicalData.getResuscitationChance(), qte,
                DefibrillationRules.Params.fromConfig(), ThreadLocalRandom.current());
        apply(medicalData, result);
        return result;
    }

    private static void apply(IPlayerMedicalData medicalData, DefibrillationRules.Result result) {
        medicalData.setHeartStatus(result.newStatus());
        medicalData.setResuscitationChance(result.newResuscitationChance());
        if (result.resetArrestTimer()) {
            medicalData.setCardiacArrestTimer(0);
        }
        if (result.clearDamageImmune()) {
            medicalData.setDamageImmune(false);
        }
    }
}
