package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.core.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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

    // 心電図データ - 毎ティック更新
    public float leadI = 0.0f;
    public float leadII = 0.0f;
    public float leadIII = 0.0f;

    // 心拍シミュレーション用の内部状態
    private float cycleTime = 0.0f;
    private float heartVectorX = 0.0f;
    private float heartVectorY = 0.0f;

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
                        be.heartRate = ModEvents.calculateHeartRate(monitoredPlayer, be.heartStatus);
                    });
                }

                // 心電図データは毎ティック更新
                be.updateECGData(monitoredPlayer, level);

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

    private void updateECGData(Player player, Level level) {
        player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
            // 1ティックあたりの時間を加算
            this.cycleTime += 0.05f; // 1/20秒

            HeartStatus status = data.getHeartStatus();
            int heartRate = ModEvents.calculateHeartRate(player, status);
            float cycleDuration = heartRate > 0 ? 60.0f / heartRate : Float.MAX_VALUE;

            // 周期リセット
            if (this.cycleTime >= cycleDuration) {
                this.cycleTime -= cycleDuration;
            }

            // 心臓ベクトルの計算
            float scalarPotential;
            float[] pathVector;

            switch (status) {
                case NORMAL -> {
                    scalarPotential = calculateGaussianSumPotential(this.cycleTime, cycleDuration);
                    pathVector = getHeartVectorPath(this.cycleTime, cycleDuration);
                    scalarPotential *= (data.getBloodLevel() / 100.0f);
                }
                case VF -> {
                    scalarPotential = 1.0f;
                    pathVector = getVFWaveform(level.getGameTime());
                }
                default -> { // CARDIAC_ARREST
                    scalarPotential = 0.0f;
                    pathVector = new float[]{0, 0};
                }
            }

            // 心臓ベクトルを更新
            this.heartVectorX = scalarPotential * pathVector[0];
            this.heartVectorY = scalarPotential * pathVector[1];

            // 誘導電圧の計算（レポート4.4節に基づく）
            float Px = this.heartVectorX;
            float Py = this.heartVectorY;

            // ドット積による誘導電圧計算
            this.leadI = Px;
            this.leadII = 0.5f * Px + 0.866f * Py;
            this.leadIII = -0.5f * Px + 0.866f * Py;

            // 微小ノイズを追加（リアリティ向上）
            float noise = (level.random.nextFloat() - 0.5f) * 0.02f;
            this.leadI += noise;
            this.leadII += noise;
            this.leadIII += noise;
        });
    }

    private void resetData() {
        this.heartRate = 0;
        this.bloodLevel = 0.0f;
        this.heartStatus = HeartStatus.NORMAL;
        this.leadI = 0.0f;
        this.leadII = 0.0f;
        this.leadIII = 0.0f;
        this.cycleTime = 0.0f;
        this.heartVectorX = 0.0f;
        this.heartVectorY = 0.0f;
        setChanged();
    }

    // ガウス関数
    private static float gaussian(float t, float a, float mu, float sigma) {
        return (float) (a * Math.exp(-Math.pow(t - mu, 2) / (2 * Math.pow(sigma, 2))));
    }

    // 正常洞調律のスカラーポテンシャル計算
    private static float calculateGaussianSumPotential(float cycleTime, float cycleDuration) {
        // P波
        float p = gaussian(cycleTime, 0.2f, 0.12f * cycleDuration, 0.04f * cycleDuration);
        // QRS波
        float q = gaussian(cycleTime, -0.15f, 0.28f * cycleDuration, 0.01f * cycleDuration);
        float r = gaussian(cycleTime, 1.2f, 0.30f * cycleDuration, 0.01f * cycleDuration);
        float s = gaussian(cycleTime, -0.3f, 0.32f * cycleDuration, 0.01f * cycleDuration);
        // T波
        float t = gaussian(cycleTime, 0.35f, 0.50f * cycleDuration, 0.08f * cycleDuration);

        return p + q + r + s + t;
    }

    // 心臓ベクトルの経路関数
    private static float[] getHeartVectorPath(float cycleTime, float cycleDuration) {
        float progress = cycleTime / cycleDuration;
        double angleRad = Math.toRadians(60);

        // QRS波の期間では角度を変化させる
        if (progress > 0.27 && progress < 0.33) {
            double qrsProgress = (progress - 0.27) / (0.33 - 0.27);
            angleRad = Math.toRadians(50 + qrsProgress * 20);
        }
        return new float[]{(float) Math.cos(angleRad), (float) Math.sin(angleRad)};
    }

    // VF波形生成
    private static float[] getVFWaveform(long gameTime) {
        float time = (float)gameTime / 20.0f;
        float x = (float)(Math.sin(time * 8) * 0.4 + Math.sin(time * 15) * 0.6 + (Math.random() - 0.5) * 0.3);
        float y = (float)(Math.sin(time * 7) * 0.5 + Math.sin(time * 18) * 0.5 + (Math.random() - 0.5) * 0.3);
        return new float[]{x, y};
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
        pTag.putFloat("CycleTime", this.cycleTime);
        pTag.putFloat("HeartVectorX", this.heartVectorX);
        pTag.putFloat("HeartVectorY", this.heartVectorY);
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
        this.cycleTime = pTag.getFloat("CycleTime");
        this.heartVectorX = pTag.getFloat("HeartVectorX");
        this.heartVectorY = pTag.getFloat("HeartVectorY");
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