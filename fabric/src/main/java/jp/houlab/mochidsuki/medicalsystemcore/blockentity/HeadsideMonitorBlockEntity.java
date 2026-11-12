package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.capability.ModAttachments;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalData;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class HeadsideMonitorBlockEntity extends BlockEntity {
    private boolean isCableTaken = false;
    /**
     * 現在取り出されているケーブルの識別子。取り出すたびに新しく発行し、ケーブルのデータコンポーネントに同じ値を持たせる。
     * 再発行や返却で値が変わると、古いケーブルは無効になる。
     */
    @Nullable
    private UUID cableId = null;
    private Optional<UUID> monitoredPlayerUUID = Optional.empty();

    // 表示用のデータ
    public int heartRate = 0;
    public float bloodLevel = 0.0f;
    public HeartStatus heartStatus = HeartStatus.NORMAL;

    // 心電図データ - プレイヤーから受け取った心電位から3誘導を計算
    public float leadI = 0.0f;
    public float leadII = 0.0f;
    public float leadIII = 0.0f;

    // アラーム関連
    private long lastHeartbeatTick = 0;
    private int vfAlarmPattern = 0; // VFアラームのパターン制御
    private boolean lastAlarmState = false;

    public HeadsideMonitorBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.HEAD_SIDE_MONITOR_BLOCK_ENTITY, pPos, pBlockState);
    }

    /** ケーブルを取り出す。新しい識別子を発行して返す(以前のケーブルはすべて無効になる)。 */
    public UUID issueCable() {
        this.isCableTaken = true;
        this.cableId = UUID.randomUUID();
        setChanged();
        return this.cableId;
    }

    /** ケーブルをモニターに戻した状態にする(接続に使った場合も含む)。 */
    public void returnCable() {
        this.isCableTaken = false;
        this.cableId = null;
        setChanged();
    }

    /** ケーブルが取り出されており、かつその識別子が現在発行中のものと一致するか。 */
    public boolean isCableLinked(@Nullable UUID id) {
        return this.isCableTaken && this.cableId != null && Objects.equals(this.cableId, id);
    }

    public boolean isCableTaken() {
        return this.isCableTaken;
    }

    public void setMonitoredPlayerUUID(Optional<UUID> uuid) {
        this.monitoredPlayerUUID = uuid;
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HeadsideMonitorBlockEntity be) {
        if (level.isClientSide()) return;

        if (be.monitoredPlayerUUID.isPresent()) {
            Player monitoredPlayer = level.getPlayerByUUID(be.monitoredPlayerUUID.get());

            if (monitoredPlayer != null && monitoredPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 100.0) {
                // 基本ステータスは1秒ごとに更新
                if (level.getGameTime() % 20 == 0) {
                    // attachment は常に存在する(Forge の capability の ifPresent と違い、無い場合の分岐は不要)
                    PlayerMedicalData data = monitoredPlayer.getAttachedOrCreate(ModAttachments.MEDICAL_DATA);
                    be.bloodLevel = data.getBloodLevel();
                    be.heartStatus = data.getHeartStatus();
                    be.heartRate = data.getHeartRate();
                }

                // 心電図データは毎ティック更新（プレイヤーのデータから3誘導を計算）
                be.updateECGFromPlayerData(monitoredPlayer, level);

                // アラーム処理
                be.handleAlarms(level, pos);

                // クライアントに同期（毎ティック）
                level.sendBlockUpdated(pos, state, state, 3);
            } else {
                // プレイヤーが無効になったら監視を解除
                be.monitoredPlayerUUID = Optional.empty();
                be.resetData();
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    /**
     * プレイヤーの心電位データから3誘導心電図を計算
     * アルゴリズム：3. ヘッドサイドモニターで心電位から3誘導の値を医学的にシミュレート
     */
    private void updateECGFromPlayerData(Player player, Level level) {
        PlayerMedicalData data = player.getAttachedOrCreate(ModAttachments.MEDICAL_DATA);
        // プレイヤー側で計算された心電位ベクトルを取得
        float Px = data.getHeartVectorX();
        float Py = data.getHeartVectorY();

        // 誘導電圧の計算（レポート4.4節に基づく）
        // ドット積による誘導電圧計算
        this.leadI = Px;
        this.leadII = 0.5f * Px + 0.866f * Py;
        this.leadIII = -0.5f * Px + 0.866f * Py;

        // モニターごとに微小ノイズを追加（リアリティ向上）
        float noise = (level.getRandom().nextFloat() - 0.5f) * 0.02f;
        this.leadI += noise;
        this.leadII += noise;
        this.leadIII += noise;
    }

    /**
     * アラーム処理
     * 仕様：
     * 通常時：心拍のタイミングでピという音
     * VF時：素早くピピピピーンピピピピーンという音
     * 心停止時：トゥーントゥーンと警告音
     */
    private void handleAlarms(Level level, BlockPos pos) {
        long currentTick = level.getGameTime();

        switch (this.heartStatus) {
            case NORMAL -> {
                // 通常時：心拍のタイミングでピッという音
                if (this.heartRate > 0) {
                    long ticksPerBeat = (long)(20 * 60.0 / this.heartRate); // 1拍動あたりのティック数
                    if (currentTick - lastHeartbeatTick >= ticksPerBeat) {
                        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.3f, 0.529732f);
                        lastHeartbeatTick = currentTick;
                    }
                }
            }
            case VF -> {
                // VF時：素早くピピピピーンピピピピーンという音
                if(currentTick % 20 < 10 && currentTick % 2 == 0){
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.BLOCKS, 1.0f, 2.0f);
                }
            }
            case CARDIAC_ARREST -> {
                // 心停止時：トゥーントゥーンと警告音
                if (currentTick % 40 == 0 || currentTick % 40 == 20) { // 1秒間隔でトゥーン
                    level.playSound(null, pos, SoundEvents.ARROW_HIT_PLAYER, SoundSource.BLOCKS, 1.0f, 0f);
                }
            }
        }

        if(this.bloodLevel<85){
            if(currentTick % 40 == 10){
                level.playSound(null,pos,SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.5f,  1.781797f);
            }

            if(currentTick % 40 == 15){
                level.playSound(null,pos,SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.5f,  1.414214f);
            }
        }
    }

    private void resetData() {
        this.heartRate = 0;
        this.bloodLevel = 0.0f;
        this.heartStatus = HeartStatus.NORMAL;
        this.leadI = 0.0f;
        this.leadII = 0.0f;
        this.leadIII = 0.0f;
        this.lastHeartbeatTick = 0;
        this.vfAlarmPattern = 0;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("CableTaken", this.isCableTaken);
        output.storeNullable("CableId", UUIDUtil.CODEC, this.cableId);
        this.monitoredPlayerUUID.ifPresent(uuid -> output.store("MonitoredPlayer", UUIDUtil.CODEC, uuid));
        output.putInt("HeartRate", this.heartRate);
        output.putFloat("BloodLevel", this.bloodLevel);
        output.putInt("HeartStatus", this.heartStatus.ordinal());
        output.putFloat("LeadI", this.leadI);
        output.putFloat("LeadII", this.leadII);
        output.putFloat("LeadIII", this.leadIII);
        output.putLong("LastHeartbeatTick", this.lastHeartbeatTick);
        output.putInt("VfAlarmPattern", this.vfAlarmPattern);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.isCableTaken = input.getBooleanOr("CableTaken", false);
        this.cableId = input.read("CableId", UUIDUtil.CODEC).orElse(null);
        this.monitoredPlayerUUID = input.read("MonitoredPlayer", UUIDUtil.CODEC);
        this.heartRate = input.getIntOr("HeartRate", 0);
        this.bloodLevel = input.getFloatOr("BloodLevel", 0.0f);
        this.heartStatus = HeartStatus.values()[input.getIntOr("HeartStatus", 0)];
        this.leadI = input.getFloatOr("LeadI", 0.0f);
        this.leadII = input.getFloatOr("LeadII", 0.0f);
        this.leadIII = input.getFloatOr("LeadIII", 0.0f);
        this.lastHeartbeatTick = input.getLongOr("LastHeartbeatTick", 0L);
        this.vfAlarmPattern = input.getIntOr("VfAlarmPattern", 0);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    public Optional<UUID> getMonitoredPlayerUUID() {
        return this.monitoredPlayerUUID;
    }
}