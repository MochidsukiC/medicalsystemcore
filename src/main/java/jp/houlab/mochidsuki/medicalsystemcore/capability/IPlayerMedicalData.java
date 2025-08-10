package jp.houlab.mochidsuki.medicalsystemcore.capability;

import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
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

    // --- データの保存と読み込み ---
    void saveNBTData(CompoundTag nbt);
    void loadNBTData(CompoundTag nbt);
}
