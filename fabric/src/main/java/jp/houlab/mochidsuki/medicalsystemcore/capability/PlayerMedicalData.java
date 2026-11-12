package jp.houlab.mochidsuki.medicalsystemcore.capability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import net.minecraft.core.BlockPos;

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

    // --- 保存(attachment の永続化) ---

    /** HeartStatus は Forge 時代と同じく序数(NORMAL=0, VF=1, ...)で保存する。範囲外の序数は読み込みエラーにする。 */
    private static final Codec<HeartStatus> HEART_STATUS_CODEC = Codec.INT.flatXmap(
            ordinal -> ordinal >= 0 && ordinal < HeartStatus.values().length
                    ? DataResult.success(HeartStatus.values()[ordinal])
                    : DataResult.error(() -> "HeartStatus の序数が範囲外: " + ordinal),
            status -> DataResult.success(status.ordinal()));

    /**
     * 保存する 15 項目 + 点滴の接続先。キー名は Forge 時代の saveNBTData と同じ。
     * キーが無い場合は初期値と同じ DEFAULT_* を使う(古いセーブデータで未保存だったキーが、型の既定値 0/false になって初期値と食い違うのを防ぐ)。
     * 読み込みはセッターを通さず全フィールドへ直接代入する(範囲の丸めを通さない点も Forge と同じ)。
     * 点滴の接続先は、キーが無ければ empty(接続なし)。
     * クライアントへの同期の記録(synced*)は保存しない。新しいインスタンスは必ず「未送信」から始まる。
     */
    public static final Codec<PlayerMedicalData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("bloodLevel", DEFAULT_BLOOD_LEVEL).forGetter(PlayerMedicalData::getBloodLevel),
            HEART_STATUS_CODEC.optionalFieldOf("heartStatus", DEFAULT_HEART_STATUS).forGetter(PlayerMedicalData::getHeartStatus),
            Codec.INT.optionalFieldOf("heartRate", DEFAULT_HEART_RATE).forGetter(PlayerMedicalData::getHeartRate),
            Codec.BOOL.optionalFieldOf("hasFracture", DEFAULT_HAS_FRACTURE).forGetter(PlayerMedicalData::hasFracture),
            Codec.INT.optionalFieldOf("gunshotWounds", DEFAULT_GUNSHOT_WOUNDS).forGetter(PlayerMedicalData::getGunshotWounds),
            Codec.INT.optionalFieldOf("tickCounter", DEFAULT_TICK_COUNTER).forGetter(PlayerMedicalData::getTickCounter),
            Codec.FLOAT.optionalFieldOf("bleedingSpeed", DEFAULT_BLEEDING_SPEED).forGetter(PlayerMedicalData::getBleedingSpeed),
            Codec.FLOAT.optionalFieldOf("resuscitationChance", DEFAULT_RESUSCITATION_CHANCE).forGetter(PlayerMedicalData::getResuscitationChance),
            Codec.INT.optionalFieldOf("cardiacArrestTimer", DEFAULT_CARDIAC_ARREST_TIMER).forGetter(PlayerMedicalData::getCardiacArrestTimer),
            HEART_STATUS_CODEC.optionalFieldOf("previousHeartStatus", DEFAULT_PREVIOUS_HEART_STATUS).forGetter(PlayerMedicalData::getPreviousHeartStatus),
            Codec.BOOL.optionalFieldOf("damageImmune", DEFAULT_DAMAGE_IMMUNE).forGetter(PlayerMedicalData::isDamageImmune),
            Codec.BOOL.optionalFieldOf("isConscious", DEFAULT_CONSCIOUS).forGetter(PlayerMedicalData::isConscious),
            Codec.FLOAT.optionalFieldOf("cycleTime", DEFAULT_CYCLE_TIME).forGetter(PlayerMedicalData::getCycleTime),
            Codec.FLOAT.optionalFieldOf("heartVectorX", DEFAULT_HEART_VECTOR).forGetter(PlayerMedicalData::getHeartVectorX),
            Codec.FLOAT.optionalFieldOf("heartVectorY", DEFAULT_HEART_VECTOR).forGetter(PlayerMedicalData::getHeartVectorY),
            BlockPos.CODEC.optionalFieldOf(KEY_TRANSFUSING_FROM_STAND_POS).forGetter(PlayerMedicalData::getTransfusingFromStandPos)
    ).apply(instance, PlayerMedicalData::fromSaved));

    private static PlayerMedicalData fromSaved(float bloodLevel, HeartStatus heartStatus, int heartRate, boolean hasFracture,
                                               int gunshotWounds, int tickCounter, float bleedingSpeed, float resuscitationChance,
                                               int cardiacArrestTimer, HeartStatus previousHeartStatus, boolean damageImmune,
                                               boolean isConscious, float cycleTime, float heartVectorX, float heartVectorY,
                                               Optional<BlockPos> transfusingFromStandPos) {
        PlayerMedicalData data = new PlayerMedicalData();
        data.bloodLevel = bloodLevel;
        data.heartStatus = heartStatus;
        data.heartRate = heartRate;
        data.hasFracture = hasFracture;
        data.gunshotWounds = gunshotWounds;
        data.tickCounter = tickCounter;
        data.bleedingSpeed = bleedingSpeed;
        data.resuscitationChance = resuscitationChance;
        data.cardiacArrestTimer = cardiacArrestTimer;
        data.previousHeartStatus = previousHeartStatus;
        data.damageImmune = damageImmune;
        data.isConscious = isConscious;
        data.cycleTime = cycleTime;
        data.heartVectorX = heartVectorX;
        data.heartVectorY = heartVectorY;
        data.blockPos = transfusingFromStandPos;
        return data;
    }
}
