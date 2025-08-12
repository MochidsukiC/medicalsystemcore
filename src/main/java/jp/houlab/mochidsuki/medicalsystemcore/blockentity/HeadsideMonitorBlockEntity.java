package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.core.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class HeadsideMonitorBlockEntity extends BlockEntity {
    private boolean isCableTaken = false;
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
        super(Medicalsystemcore.HEAD_SIDE_MONITOR_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    public void setCableTaken(boolean taken) {
        this.isCableTaken = taken;
        setChanged();
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
                    monitoredPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
                        be.bloodLevel = data.getBloodLevel();
                        be.heartStatus = data.getHeartStatus();
                        be.heartRate = data.getHeartRate();
                    });
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
        player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
            // プレイヤー側で計算された心電位ベクトルを取得
            float Px = data.getHeartVectorX();
            float Py = data.getHeartVectorY();

            // 誘導電圧の計算（レポート4.4節に基づく）
            // ドット積による誘導電圧計算
            this.leadI = Px;
            this.leadII = 0.5f * Px + 0.866f * Py;
            this.leadIII = -0.5f * Px + 0.866f * Py;

            // モニターごとに微小ノイズを追加（リアリティ向上）
            float noise = (level.random.nextFloat() - 0.5f) * 0.02f;
            this.leadI += noise;
            this.leadII += noise;
            this.leadIII += noise;
        });
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
                        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.BLOCKS, 0.3f, 2.0f);
                        lastHeartbeatTick = currentTick;
                    }
                }
            }
            case VF -> {
                // VF時：素早くピピピピーンピピピピーンという音
                int pattern = (int)(currentTick % 40); // 2秒周期
                if (pattern < 8) { // 最初の0.4秒：ピピピピ
                    if (pattern % 2 == 0) {
                        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.BLOCKS, 0.5f, 1.8f);
                    }
                } else if (pattern == 10) { // ーン
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.BLOCKS, 0.7f, 0.5f);
                } else if (pattern >= 20 && pattern < 28) { // 次のピピピピ
                    if (pattern % 2 == 0) {
                        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.BLOCKS, 0.5f, 1.8f);
                    }
                } else if (pattern == 30) { // ーン
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.BLOCKS, 0.7f, 0.5f);
                }
            }
            case CARDIAC_ARREST -> {
                // 心停止時：トゥーントゥーンと警告音
                if (currentTick % 40 == 0 || currentTick % 40 == 20) { // 1秒間隔でトゥーン
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.BLOCKS, 1.0f, 0.3f);
                }
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
    protected void saveAdditional(CompoundTag pTag) {
        pTag.putBoolean("CableTaken", this.isCableTaken);
        if (this.monitoredPlayerUUID.isPresent()) {
            pTag.putUUID("MonitoredPlayer", this.monitoredPlayerUUID.get());
        }
        pTag.putInt("HeartRate", this.heartRate);
        pTag.putFloat("BloodLevel", this.bloodLevel);
        pTag.putInt("HeartStatus", this.heartStatus.ordinal());
        pTag.putFloat("LeadI", this.leadI);
        pTag.putFloat("LeadII", this.leadII);
        pTag.putFloat("LeadIII", this.leadIII);
        pTag.putLong("LastHeartbeatTick", this.lastHeartbeatTick);
        pTag.putInt("VfAlarmPattern", this.vfAlarmPattern);
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        this.isCableTaken = pTag.getBoolean("CableTaken");
        if (pTag.hasUUID("MonitoredPlayer")) {
            this.monitoredPlayerUUID = Optional.of(pTag.getUUID("MonitoredPlayer"));
        } else {
            this.monitoredPlayerUUID = Optional.empty();
        }
        this.heartRate = pTag.getInt("HeartRate");
        this.bloodLevel = pTag.getFloat("BloodLevel");
        this.heartStatus = HeartStatus.values()[pTag.getInt("HeartStatus")];
        this.leadI = pTag.getFloat("LeadI");
        this.leadII = pTag.getFloat("LeadII");
        this.leadIII = pTag.getFloat("LeadIII");
        this.lastHeartbeatTick = pTag.getLong("LastHeartbeatTick");
        this.vfAlarmPattern = pTag.getInt("VfAlarmPattern");
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public Optional<UUID> getMonitoredPlayerUUID() {
        return this.monitoredPlayerUUID;
    }
}