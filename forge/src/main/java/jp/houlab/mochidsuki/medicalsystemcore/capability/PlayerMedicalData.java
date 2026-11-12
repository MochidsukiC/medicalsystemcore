package jp.houlab.mochidsuki.medicalsystemcore.capability;

import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.Optional;

public class PlayerMedicalData implements IPlayerMedicalData {

    // 各ステータスのデフォルト値。フィールドの初期値と NBT のキー欠落時の既定値で同じ定数を使い、食い違わせない。
    private static final float DEFAULT_BLOOD_LEVEL = 100.0f;
    private static final HeartStatus DEFAULT_HEART_STATUS = HeartStatus.NORMAL;
    private static final int DEFAULT_HEART_RATE = 70;
    private static final boolean DEFAULT_HAS_FRACTURE = false;
    private static final int DEFAULT_GUNSHOT_WOUNDS = 0;
    private static final int DEFAULT_TICK_COUNTER = 0;
    private static final float DEFAULT_BLEEDING_SPEED = 0.0f;
    private static final float DEFAULT_RESUSCITATION_CHANCE = 100.0f;
    private static final int DEFAULT_CARDIAC_ARREST_TIMER = 0;
    private static final HeartStatus DEFAULT_PREVIOUS_HEART_STATUS = HeartStatus.NORMAL;
    private static final boolean DEFAULT_DAMAGE_IMMUNE = false;
    private static final boolean DEFAULT_CONSCIOUS = true;
    private static final float DEFAULT_CYCLE_TIME = 0.0f;
    private static final float DEFAULT_HEART_VECTOR = 0.0f;

    private static final String KEY_TRANSFUSING_FROM_STAND_POS = "transfusingFromStandPos";

    private float bloodLevel = DEFAULT_BLOOD_LEVEL;
    private HeartStatus heartStatus = DEFAULT_HEART_STATUS;
    private int heartRate = DEFAULT_HEART_RATE;
    private boolean hasFracture = DEFAULT_HAS_FRACTURE;
    private int gunshotWounds = DEFAULT_GUNSHOT_WOUNDS;
    private int tickCounter = DEFAULT_TICK_COUNTER;
    private float bleedingSpeed = DEFAULT_BLEEDING_SPEED;
    private float resuscitationChance = DEFAULT_RESUSCITATION_CHANCE;
    private int cardiacArrestTimer = DEFAULT_CARDIAC_ARREST_TIMER;
    private HeartStatus previousHeartStatus = DEFAULT_PREVIOUS_HEART_STATUS;
    private Optional<BlockPos> blockPos = Optional.empty();
    private boolean damageImmune = DEFAULT_DAMAGE_IMMUNE;
    private float cycleTime = DEFAULT_CYCLE_TIME;
    private float heartVectorX = DEFAULT_HEART_VECTOR;
    private float heartVectorY = DEFAULT_HEART_VECTOR;

    // --- クライアントへ最後に送った状態(保存しない。新しいインスタンスは必ず「未送信」から始まる) ---
    private HeartStatus syncedHeartStatus = null;
    private boolean syncedConscious = false;
    private long syncedAtGameTime = Long.MIN_VALUE;

    @Override
    public boolean needsCoreStatsSync(long gameTime, int intervalTicks) {
        return syncedHeartStatus == null
                || syncedHeartStatus != this.heartStatus
                || syncedConscious != this.isConscious
                || gameTime - syncedAtGameTime >= intervalTicks;
    }

    @Override
    public void markCoreStatsSynced(long gameTime) {
        this.syncedHeartStatus = this.heartStatus;
        this.syncedConscious = this.isConscious;
        this.syncedAtGameTime = gameTime;
    }

    @Override
    public float getCycleTime() {
        return cycleTime;
    }

    @Override
    public void setCycleTime(float cycleTime) {
        this.cycleTime = cycleTime;
    }

    @Override
    public float getHeartVectorX() {
        return heartVectorX;
    }

    @Override
    public void setHeartVectorX(float heartVectorX) {
        this.heartVectorX = heartVectorX;
    }

    @Override
    public float getHeartVectorY() {
        return heartVectorY;
    }

    @Override
    public void setHeartVectorY(float heartVectorY) {
        this.heartVectorY = heartVectorY;
    }

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
    public int getHeartRate() {
        return this.heartRate;
    }

    @Override
    public void setHeartRate(int heartRate) {
        this.heartRate = Math.max(0, heartRate); // 0未満にならないように
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
    public float getBleedingSpeed() {
        return this.bleedingSpeed;
    }

    @Override
    public void setBleedingSpeed(float speed) {
        this.bleedingSpeed = Math.max(0.0f, speed); // 0未満にならないように
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

    @Override
    public Optional<BlockPos> getTransfusingFromStandPos(){
        return blockPos;
    }

    @Override
    public void setTransfusingFromStandPos(Optional<BlockPos> pos) {
        this.blockPos = pos;
    }

    @Override
    public boolean isDamageImmune() {
        return this.damageImmune;
    }

    @Override
    public void setDamageImmune(boolean immune) {
        this.damageImmune = immune;
    }

    private boolean isConscious = DEFAULT_CONSCIOUS; // デフォルトは意識清明

    @Override
    public boolean isConscious() {
        return this.isConscious;
    }

    @Override
    public void setConscious(boolean conscious) {
        this.isConscious = conscious;
    }

    /**
     * このデータをNBT形式（Minecraftのデータ保存形式）に変換して保存します。
     * 保存するキーと {@link #loadNBTData} が読むキーは完全に対応させる(Clone でのコピーもこの対を通る)。
     */
    @Override
    public void saveNBTData(CompoundTag nbt) {
        nbt.putFloat("bloodLevel", this.bloodLevel);
        // Enumは序数(NORMAL=0, VF=1, ...)で保存するのが一般的
        nbt.putInt("heartStatus", this.heartStatus.ordinal());
        nbt.putInt("heartRate", this.heartRate);
        nbt.putBoolean("hasFracture", this.hasFracture);
        nbt.putInt("gunshotWounds", this.gunshotWounds);
        nbt.putInt("tickCounter", this.tickCounter);
        nbt.putFloat("bleedingSpeed", this.bleedingSpeed);
        nbt.putFloat("resuscitationChance", this.resuscitationChance);
        nbt.putInt("cardiacArrestTimer", this.cardiacArrestTimer);
        nbt.putInt("previousHeartStatus", this.previousHeartStatus.ordinal());
        nbt.putBoolean("damageImmune", this.damageImmune);
        nbt.putBoolean("isConscious", this.isConscious);

        nbt.putFloat("cycleTime", this.cycleTime);
        nbt.putFloat("heartVectorX", this.heartVectorX);
        nbt.putFloat("heartVectorY", this.heartVectorY);

        // 点滴の接続先。接続していないときはキーを書かない(読み込み側は欠落を「接続なし」として扱う)。
        this.blockPos.ifPresent(pos -> nbt.put(KEY_TRANSFUSING_FROM_STAND_POS, NbtUtils.writeBlockPos(pos)));
    }

    /**
     * NBT形式のデータから、このクラスのフィールドに値を読み込みます。
     * すべてのフィールドを無条件に代入する。キーが無い場合は初期値と同じ DEFAULT_* を使う
     * (古いセーブデータで未保存だったキーが、型の既定値 0/false になって初期値と食い違うのを防ぐ)。
     * 点滴の接続先は、キーが無ければ必ず empty に戻す(既存インスタンスへの再読込でも接続が残らないように)。
     */
    @Override
    public void loadNBTData(CompoundTag nbt) {
        this.bloodLevel = nbt.contains("bloodLevel", Tag.TAG_FLOAT) ? nbt.getFloat("bloodLevel") : DEFAULT_BLOOD_LEVEL;
        this.heartStatus = readHeartStatus(nbt, "heartStatus", DEFAULT_HEART_STATUS);
        this.heartRate = nbt.contains("heartRate", Tag.TAG_INT) ? nbt.getInt("heartRate") : DEFAULT_HEART_RATE;
        this.hasFracture = nbt.contains("hasFracture", Tag.TAG_BYTE) ? nbt.getBoolean("hasFracture") : DEFAULT_HAS_FRACTURE;
        this.gunshotWounds = nbt.contains("gunshotWounds", Tag.TAG_INT) ? nbt.getInt("gunshotWounds") : DEFAULT_GUNSHOT_WOUNDS;
        this.tickCounter = nbt.contains("tickCounter", Tag.TAG_INT) ? nbt.getInt("tickCounter") : DEFAULT_TICK_COUNTER;
        this.bleedingSpeed = nbt.contains("bleedingSpeed", Tag.TAG_FLOAT) ? nbt.getFloat("bleedingSpeed") : DEFAULT_BLEEDING_SPEED;
        this.resuscitationChance = nbt.contains("resuscitationChance", Tag.TAG_FLOAT)
                ? nbt.getFloat("resuscitationChance") : DEFAULT_RESUSCITATION_CHANCE;
        this.cardiacArrestTimer = nbt.contains("cardiacArrestTimer", Tag.TAG_INT)
                ? nbt.getInt("cardiacArrestTimer") : DEFAULT_CARDIAC_ARREST_TIMER;
        this.previousHeartStatus = readHeartStatus(nbt, "previousHeartStatus", DEFAULT_PREVIOUS_HEART_STATUS);
        this.damageImmune = nbt.contains("damageImmune", Tag.TAG_BYTE) ? nbt.getBoolean("damageImmune") : DEFAULT_DAMAGE_IMMUNE;
        this.isConscious = nbt.contains("isConscious", Tag.TAG_BYTE) ? nbt.getBoolean("isConscious") : DEFAULT_CONSCIOUS;

        this.cycleTime = nbt.contains("cycleTime", Tag.TAG_FLOAT) ? nbt.getFloat("cycleTime") : DEFAULT_CYCLE_TIME;
        this.heartVectorX = nbt.contains("heartVectorX", Tag.TAG_FLOAT) ? nbt.getFloat("heartVectorX") : DEFAULT_HEART_VECTOR;
        this.heartVectorY = nbt.contains("heartVectorY", Tag.TAG_FLOAT) ? nbt.getFloat("heartVectorY") : DEFAULT_HEART_VECTOR;

        this.blockPos = nbt.contains(KEY_TRANSFUSING_FROM_STAND_POS, Tag.TAG_COMPOUND)
                ? Optional.of(NbtUtils.readBlockPos(nbt.getCompound(KEY_TRANSFUSING_FROM_STAND_POS)))
                : Optional.empty();
    }

    /** 保存した序数から HeartStatus を復元する。キーが無ければ defaultValue。 */
    private static HeartStatus readHeartStatus(CompoundTag nbt, String key, HeartStatus defaultValue) {
        return nbt.contains(key, Tag.TAG_INT) ? HeartStatus.values()[nbt.getInt(key)] : defaultValue;
    }
}
