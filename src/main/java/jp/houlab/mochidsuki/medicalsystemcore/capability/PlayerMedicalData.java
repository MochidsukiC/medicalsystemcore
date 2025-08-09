package jp.houlab.mochidsuki.medicalsystemcore.capability;

import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
import net.minecraft.nbt.CompoundTag;

public class PlayerMedicalData implements IPlayerMedicalData {

    // 各ステータスのデフォルト値
    private float bloodLevel = 100.0f;
    private HeartStatus heartStatus = HeartStatus.NORMAL;
    private boolean hasFracture = false;
    private int gunshotWounds = 0;
    private int bleedingLevel = 0;
    private int tickCounter = 0;
    private float resuscitationChance = 100.0f;
    private int cardiacArrestTimer = 0;
    private HeartStatus previousHeartStatus = HeartStatus.NORMAL;


    @Override
    public float getBloodLevel() {
        return this.bloodLevel;
    }

    @Override
    public void setBloodLevel(float level) {
        // 0%から100%の間に収める
        this.bloodLevel = Math.max(0, Math.min(100, level));
    }

    @Override
    public HeartStatus getHeartStatus() {
        return this.heartStatus;
    }

    @Override
    public void setHeartStatus(HeartStatus status) {
        this.heartStatus = status;
    }

    @Override
    public boolean hasFracture() {
        return this.hasFracture;
    }

    @Override
    public void setFracture(boolean hasFracture) {
        this.hasFracture = hasFracture;
    }

    @Override
    public int getGunshotWounds() {
        return this.gunshotWounds;
    }

    @Override
    public void setGunshotWounds(int count) {
        // 0未満にはならないようにする
        this.gunshotWounds = Math.max(0, count);
    }

    @Override
    public void addGunshotWounds(int amount) {
        this.setGunshotWounds(this.gunshotWounds + amount);
    }

    @Override
    public int getBleedingLevel() {
        return this.bleedingLevel;
    }

    @Override
    public void setBleedingLevel(int level) {
        // 0から4の間に収める
        this.bleedingLevel = Math.max(0, Math.min(4, level));
    }

    @Override
    public int getTickCounter() {
        return this.tickCounter;
    }

    @Override
    public void setTickCounter(int count) {
        this.tickCounter = count;
    }


    @Override
    public float getResuscitationChance() {
        return this.resuscitationChance;
    }

    @Override
    public void setResuscitationChance(float chance) {
        // 0%から100%の間に収める
        this.resuscitationChance = Math.max(0.0f, Math.min(100.0f, chance));
    }

    @Override
    public int getCardiacArrestTimer() {
        return this.cardiacArrestTimer;
    }

    @Override
    public void setCardiacArrestTimer(int ticks) {
        this.cardiacArrestTimer = ticks;
    }

    @Override
    public HeartStatus getPreviousHeartStatus() {
        return this.previousHeartStatus;
    }

    @Override
    public void setPreviousHeartStatus(HeartStatus status) {
        this.previousHeartStatus = status;
    }

    /**
     * このデータをNBT形式（Minecraftのデータ保存形式）に変換して保存します。
     */
    @Override
    public void saveNBTData(CompoundTag nbt) {
        nbt.putFloat("bloodLevel", this.bloodLevel);
        // Enumは序数(NORMAL=0, VF=1, ...)で保存するのが一般的
        nbt.putInt("heartStatus", this.heartStatus.ordinal());
        nbt.putBoolean("hasFracture", this.hasFracture);
        nbt.putInt("gunshotWounds", this.gunshotWounds);
        nbt.putInt("bleedingLevel", this.bleedingLevel);
    }

    /**
     * NBT形式のデータから、このクラスのフィールドに値を読み込みます。
     */
    @Override
    public void loadNBTData(CompoundTag nbt) {
        this.bloodLevel = nbt.getFloat("bloodLevel");
        // 保存した序数からEnumを復元
        this.heartStatus = HeartStatus.values()[nbt.getInt("heartStatus")];
        this.hasFracture = nbt.getBoolean("hasFracture");
        this.gunshotWounds = nbt.getInt("gunshotWounds");
        this.bleedingLevel = nbt.getInt("bleedingLevel");
    }
}
