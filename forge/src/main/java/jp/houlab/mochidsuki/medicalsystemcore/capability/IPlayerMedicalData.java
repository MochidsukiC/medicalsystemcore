package jp.houlab.mochidsuki.medicalsystemcore.capability;

import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

public interface IPlayerMedicalData {
    // --- 血液量 ---
    float getBloodLevel();
    void setBloodLevel(float level);

    // --- 心臓状態 ---
    HeartStatus getHeartStatus();
    void setHeartStatus(HeartStatus status);

    // --- 心拍数（プレイヤー側で計算、モニターで共有） ---
    int getHeartRate();
    void setHeartRate(int heartRate);

    // --- 骨折 ---
    boolean hasFracture();
    void setFracture(boolean hasFracture);

    // --- 銃創 ---
    int getGunshotWounds();
    void setGunshotWounds(int count);
    void addGunshotWounds(int amount); // 加算・減算用の便利なメソッド

    // --- 出血速度 ---
    float getBleedingSpeed();
    void setBleedingSpeed(float speed);

    // --- ティックカウンター ---
    int getTickCounter();
    void setTickCounter(int count);

    float getResuscitationChance();
    void setResuscitationChance(float chance);

    int getCardiacArrestTimer();
    void setCardiacArrestTimer(int ticks);

    HeartStatus getPreviousHeartStatus();
    void setPreviousHeartStatus(HeartStatus status);

    Optional<BlockPos> getTransfusingFromStandPos();
    void setTransfusingFromStandPos(Optional<BlockPos> pos);

    boolean isDamageImmune();
    void setDamageImmune(boolean immune);

    boolean isConscious();
    void setConscious(boolean conscious);

    //心臓の周期的タイマー
    float getCycleTime();
    void setCycleTime(float time);
    float getHeartVectorX();
    void setHeartVectorX(float x);
    float getHeartVectorY();
    void setHeartVectorY(float y);

    // --- クライアントへの状態同期の記録(保存しない) ---
    /**
     * ClientboundCoreStatsPacket を今送るべきか。
     * 前回送った時点から heartStatus か意識が変わった、まだ一度も送っていない、または前回から intervalTicks 以上経った場合に true。
     */
    boolean needsCoreStatsSync(long gameTime, int intervalTicks);

    /** 現在の heartStatus と意識を「送信済み」として記録する。 */
    void markCoreStatsSynced(long gameTime);

    // --- データの保存と読み込み ---
    void saveNBTData(CompoundTag nbt);
    void loadNBTData(CompoundTag nbt);
}