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
                    // データを読み取り、このブロックエンティティのフィールドに保存
                    be.bloodLevel = data.getBloodLevel();
                    be.heartStatus = data.getHeartStatus();

                    // 心拍数を計算
                    int heartRate = ModEvents.calculateHeartRate(monitoredPlayer, be.heartStatus);
                    be.heartRate = heartRate;

                    // ▼▼▼ 心電図の計算と保存を追加 ▼▼▼
                    int ticksPerBeat = heartRate > 0 ? 1200 / heartRate : 1;
                    float[] leads = ModEvents.calculateLeadValues(be.heartStatus, data.getCardiacCycleTick(), ticksPerBeat);
                    be.leadI = leads[0];
                    be.leadII = leads[1];
                    be.leadIII = leads[2];

                    // アラームの条件をチェック
                    boolean shouldAlarm = be.heartStatus == HeartStatus.VF || be.heartStatus == HeartStatus.CARDIAC_ARREST || be.bloodLevel < 60.0f;
                    if (shouldAlarm && !be.alarmPlaying) {
                        // アラーム音を鳴らす
                        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.BLOCKS, 2.0f, 2.0f);
                        be.alarmPlaying = true;
                    } else if (!shouldAlarm) {
                        be.alarmPlaying = false;
                    }

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

    // --- セーブとロード ---
    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.putInt("HeartRate", this.heartRate);
        pTag.putFloat("BloodLevel", this.bloodLevel);
        pTag.putInt("HeartStatus", this.heartStatus.ordinal());

        pTag.putFloat("LeadI", this.leadI);
        pTag.putFloat("LeadII", this.leadII);
        pTag.putFloat("LeadIII", this.leadIII);
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        this.heartRate = pTag.getInt("HeartRate");
        this.bloodLevel = pTag.getFloat("BloodLevel");
        this.heartStatus = HeartStatus.values()[pTag.getInt("HeartStatus")];

        this.leadI = pTag.getFloat("LeadI");
        this.leadII = pTag.getFloat("LeadII");
        this.leadIII = pTag.getFloat("LeadIII");
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