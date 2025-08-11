package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.core.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
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

    // 表示用のデータ (変更なし)
    public int heartRate = 0;
    public float bloodLevel = 0.0f;
    public HeartStatus heartStatus = HeartStatus.NORMAL;
    // アラーム音を鳴らしたかを記録するフラグ
    private boolean alarmPlaying = false;


    public float leadI = 0.0f;
    public float leadII = 0.0f;
    public float leadIII = 0.0f;


    public float[] lead1Waveform = new float[0];
    public float[] lead2Waveform = new float[0];
    public float[] lead3Waveform = new float[0];


    public HeadsideMonitorBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(Medicalsystemcore.HEAD_SIDE_MONITOR_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    // ケーブルの状態を変更するメソッド
    public void setCableTaken(boolean taken) {
        this.isCableTaken = taken;
        setChanged();
    }
    public boolean isCableTaken() {
        return this.isCableTaken;
    }

    // 監視対象を設定するメソッド
    public void setMonitoredPlayerUUID(Optional<UUID> uuid) {
        this.monitoredPlayerUUID = uuid;
        setChanged();
    }

    // ▼▼▼ このtickメソッドを追加 ▼▼▼
    public static void tick(Level level, BlockPos pos, BlockState state, HeadsideMonitorBlockEntity be) {
        // サーバーサイドでのみ実行
        if (level.isClientSide()) return;

        // 監視対象がいる場合のみ、1秒(20tick)ごとにデータを更新
        if (be.monitoredPlayerUUID.isPresent() && level.getGameTime() % 20 == 0) {
            Player monitoredPlayer = level.getPlayerByUUID(be.monitoredPlayerUUID.get());

            // プレイヤーが有効（オンラインかつ10ブロック以内）か確認
            if (monitoredPlayer != null && monitoredPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 100.0) {
                monitoredPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
                    // --- プレイヤーの基本データを読み取り ---
                    be.bloodLevel = data.getBloodLevel();
                    be.heartStatus = data.getHeartStatus();
                    be.heartRate = ModEvents.calculateHeartRate(monitoredPlayer, be.heartStatus);

                    // --- ▼▼▼ このモニターで、心電図を個別に計算 ▼▼▼ ---
                    int ticksPerBeat = be.heartRate > 0 ? 1200 / be.heartRate : 1;
                    float[] leads = be.calculateLeadValues(be.heartStatus, data.getCardiacCycleTick(), ticksPerBeat, level);
                    be.lead1Waveform = new float[]{leads[0]}; // 配列として保存
                    be.lead2Waveform = new float[]{leads[1]};
                    be.lead3Waveform = new float[]{leads[2]};

                    // (アラームのロジックはここに移動しても良い)

                    // 状態が変化したので、クライアントに同期を要求
                    level.sendBlockUpdated(pos, state, state, 3);
                });
            } else {
                // プレイヤーが無効になったら、監視を解除し、データをリセット
                be.monitoredPlayerUUID = Optional.empty();
                be.heartRate = 0;
                be.bloodLevel = 0.0f;
                be.heartStatus = HeartStatus.NORMAL;
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    // ▼▼▼ 心拍数計算用のヘルパーメソッドを追加 ▼▼▼
    private static int calculateHeartRate(Player player, HeartStatus status) {
        return switch (status) {
            case NORMAL -> {
                int base = 60 + player.level().random.nextInt(10);
                if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                    base += (player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() + 1) * 10;
                }
                if (player.hasEffect(MobEffects.JUMP)) {
                    base += (player.getEffect(MobEffects.JUMP).getAmplifier() + 1) * 10;
                }
                yield base;
            }
            case VF -> 300 + player.level().random.nextInt(100);
            case CARDIAC_ARREST -> 0;
        };
    }

    /**
     * このモニター独自の3誘導値を計算する
     */
    private float[] calculateLeadValues(HeartStatus status, int cycleTick, int ticksPerBeat, Level level) {
        float[] leads = new float[3]; // [I, II, III]
        float progress = (float) cycleTick / ticksPerBeat;

        // (以前ModEventsにあった計算ロジックをここに移動)
        switch (status) {
            case NORMAL:
                // ... (P波, QRS波, T波の計算) ...
                break;
            case VF:
                // ... (VFの計算) ...
                break;
            case CARDIAC_ARREST:
                // ... (フラットライン) ...
                break;
        }

        // ▼▼▼ このモニター独自のノイズを追加 ▼▼▼
        leads[0] += (level.random.nextFloat() - 0.5f) * 0.05f; // ±2.5%のノイズ
        leads[1] += (level.random.nextFloat() - 0.5f) * 0.05f;
        leads[2] += (level.random.nextFloat() - 0.5f) * 0.05f;

        return leads;
    }

    // --- セーブとロード ---
    @Override
    protected void saveAdditional(CompoundTag pTag) {

        if (this.monitoredPlayerUUID.isPresent()) {
            pTag.putUUID("MonitoredPlayer", this.monitoredPlayerUUID.get());
        }

        pTag.putInt("HeartRate", this.heartRate);
        pTag.putFloat("BloodLevel", this.bloodLevel);
        pTag.putInt("HeartStatus", this.heartStatus.ordinal());

        pTag.putFloat("LeadI", this.leadI);
        pTag.putFloat("LeadII", this.leadII);
        pTag.putFloat("LeadIII", this.leadIII);

        ListTag lead1List = new ListTag();
        for (float val : this.lead1Waveform) {
            lead1List.add(FloatTag.valueOf(val));
        }
        pTag.put("Lead1Waveform", lead1List);

        ListTag lead2List = new ListTag();
        for (float val : this.lead2Waveform) {
            lead2List.add(FloatTag.valueOf(val));
        }
        pTag.put("Lead2Waveform", lead2List);

        ListTag lead3List = new ListTag();
        for (float val : this.lead3Waveform) {
            lead3List.add(FloatTag.valueOf(val));
        }
        pTag.put("Lead3Waveform", lead3List);
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);

        if (pTag.hasUUID("MonitoredPlayer")) {
            this.monitoredPlayerUUID = Optional.of(pTag.getUUID("MonitoredPlayer"));
        }
        this.heartRate = pTag.getInt("HeartRate");
        this.bloodLevel = pTag.getFloat("BloodLevel");
        this.heartStatus = HeartStatus.values()[pTag.getInt("HeartStatus")];

        this.leadI = pTag.getFloat("LeadI");
        this.leadII = pTag.getFloat("LeadII");
        this.leadIII = pTag.getFloat("LeadIII");

        // ListTag<FloatTag>を読み込み、float[]に変換して復元
        ListTag lead1List = pTag.getList("Lead1Waveform", Tag.TAG_FLOAT); // TAG_FLOATは「float型」の意
        this.lead1Waveform = new float[lead1List.size()];
        for (int i = 0; i < lead1List.size(); i++) {
            this.lead1Waveform[i] = lead1List.getFloat(i);
        }

        ListTag lead2List = pTag.getList("Lead2Waveform", Tag.TAG_FLOAT);
        this.lead2Waveform = new float[lead2List.size()];
        for (int i = 0; i < lead2List.size(); i++) {
            this.lead2Waveform[i] = lead2List.getFloat(i);
        }

        ListTag lead3List = pTag.getList("Lead3Waveform", Tag.TAG_FLOAT);
        this.lead3Waveform = new float[lead3List.size()];
        for (int i = 0; i < lead3List.size(); i++) {
            this.lead3Waveform[i] = lead3List.getFloat(i);
        }
    }

    // --- サーバーとクライアントのデータ同期 ---
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