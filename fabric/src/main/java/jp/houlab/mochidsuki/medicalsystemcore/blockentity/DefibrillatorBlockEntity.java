package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.block.DefibrillatorBlock;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public class DefibrillatorBlockEntity extends BlockEntity {
    private int chargeProgress = 0;
    private boolean arePadsTaken = false;
    /**
     * 現在取り出されている電極の識別子。取り出すたびに新しく発行し、電極アイテムのデータコンポーネントに同じ値を持たせる。
     * 再発行や収納で値が変わると、古い電極は無効になる。null は識別子導入前に取り出された電極(旧データ)。
     */
    @Nullable
    private UUID padsId = null;
    private long cooldownEndTick = 0;
    /**
     * 保存データから読んだクールダウンの残り tick のうち、まだ cooldownEndTick に換算していない分。
     * loadAdditional() はレベルが設定される前(チャンク読み込み時)に呼ばれ、ゲーム時間を参照できない。
     * その場合は残り tick をここに持ち、レベルが設定された後の setLevel() で換算する。
     */
    private long pendingCooldownTicks = 0;

    public DefibrillatorBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.DEFIBRILLATOR_BLOCK_ENTITY, pPos, pBlockState);
    }

    public boolean arePadsTaken() { return this.arePadsTaken; }

    /** 電極を取り出す。新しい識別子を発行して返す(以前の電極はすべて無効になる)。 */
    public UUID issuePads() {
        this.arePadsTaken = true;
        this.padsId = UUID.randomUUID();
        setChanged();
        return this.padsId;
    }

    /** 電極を収納した状態に戻す。 */
    public void returnPads() {
        this.arePadsTaken = false;
        this.padsId = null;
        setChanged();
    }

    /** 電極が取り出されており、かつその識別子が現在発行中のものと一致するか(識別子なしの旧電極は旧データとだけ一致する)。 */
    public boolean isPadsLinked(@Nullable UUID electrodePadsId) {
        return this.arePadsTaken && Objects.equals(this.padsId, electrodePadsId);
    }

    public boolean isCharging() { return this.chargeProgress > 0; }
    public boolean isCharged() { return this.chargeProgress == -1; }

    // 修正: クールダウンタイマーの計算を改善
    public boolean isOnCooldown() {
        if (level == null) return false;
        resolvePendingCooldown();
        return level.getGameTime() < this.cooldownEndTick;
    }

    public long getCooldownSecondsLeft() {
        if (level == null) return 0;
        resolvePendingCooldown();
        long ticksLeft = this.cooldownEndTick - level.getGameTime();
        return Math.max(0, ticksLeft / 20);
    }

    public void startCharge() {
        this.chargeProgress = 1;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void resetChargeAndStartCooldown() {
        this.chargeProgress = 0;
        returnPads();
        startCooldown();
        if (level != null) {
            level.setBlock(getBlockPos(), getBlockState().setValue(DefibrillatorBlock.CHARGED, false), 3);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DefibrillatorBlockEntity be) {
        if (!state.getValue(DefibrillatorBlock.POWERED)) {
            be.chargeProgress = 0;
            if (state.getValue(DefibrillatorBlock.CHARGED)) {
                level.setBlock(pos, state.setValue(DefibrillatorBlock.CHARGED, false), 3);
            }
            return;
        }

        long currentTick = level.getGameTime();
        int chargeTimeTicks = MedicalConfig.get(MedicalConfigKeys.DEFIBRILLATOR_CHARGE_TIME) * 20;
        if (be.chargeProgress > 0) {

            be.chargeProgress++;

            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), SoundSource.BLOCKS, 0.4f, ((float) be.chargeProgress/chargeTimeTicks + 0.5f)*1.5f);


            if (be.chargeProgress >= chargeTimeTicks) {
                be.chargeProgress = -1;
                level.setBlock(pos, state.setValue(DefibrillatorBlock.CHARGED, true), 3);
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
                level.sendBlockUpdated(pos, state, state.setValue(DefibrillatorBlock.CHARGED, true), 3);
            }
        }else if(be.chargeProgress == -1) {
            if(currentTick % 10 == 1){
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), SoundSource.BLOCKS, 0.4f, 0.594604f);
            }
            if(currentTick % 10 == 6){
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), SoundSource.BLOCKS, 0.4f, 1.189207f);

            }
        }
    }

    public void resetCharge() {
        this.chargeProgress = 0;
        if (level != null) {
            level.setBlock(getBlockPos(), getBlockState().setValue(DefibrillatorBlock.CHARGED, false), 3);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        setChanged();
    }

    public void startCooldown() {
        this.pendingCooldownTicks = 0;
        if (level != null) {
            this.cooldownEndTick = level.getGameTime() + (MedicalConfig.get(MedicalConfigKeys.DEFIBRILLATOR_COOLDOWN) * 20L);
        }
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public float getChargeProgress() {
        if (!isCharging()) return 0.0f;
        int chargeTimeTicks = MedicalConfig.get(MedicalConfigKeys.DEFIBRILLATOR_CHARGE_TIME) * 20;
        return (float) this.chargeProgress / chargeTimeTicks;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ChargeProgress", this.chargeProgress);
        output.putBoolean("ArePadsTaken", this.arePadsTaken);
        output.storeNullable("PadsId", UUIDUtil.CODEC, this.padsId);
        // クールダウンは絶対時刻ではなく残り tick で保存する(ワールドのゲーム時間に依存しない)。
        // 読み込んだ値が未換算(レベル設定前)なら、先に換算するか、換算できなければそのまま書き戻す(0 で上書きしない)。
        resolvePendingCooldown();
        long remainingTicks = level != null
                ? Math.max(0, this.cooldownEndTick - level.getGameTime())
                : this.pendingCooldownTicks;
        output.putLong("CooldownRemainingTicks", remainingTicks);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.chargeProgress = input.getIntOr("ChargeProgress", 0);
        this.arePadsTaken = input.getBooleanOr("ArePadsTaken", false);
        this.padsId = input.read("PadsId", UUIDUtil.CODEC).orElse(null);

        long remainingTicks = Math.max(0, input.getLongOr("CooldownRemainingTicks", 0L));
        if (level != null) {
            // クライアントへの同期パケットなど、レベルが既にある場合はその場で換算する
            this.cooldownEndTick = remainingTicks > 0 ? level.getGameTime() + remainingTicks : 0;
            this.pendingCooldownTicks = 0;
        } else {
            // チャンク読み込み時はレベル未設定。setLevel() で換算する
            this.cooldownEndTick = 0;
            this.pendingCooldownTicks = remainingTicks;
        }
    }

    /**
     * Forge の onLoad に相当する。26.2 の BlockEntity に onLoad は無く、チャンク読み込み時は
     * loadAdditional の後に setLevel が呼ばれるので、ここで未換算のクールダウンを換算する。
     */
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        resolvePendingCooldown();
    }

    /** 未換算の読み込み値があり、レベルが使えるなら cooldownEndTick に換算する。 */
    private void resolvePendingCooldown() {
        if (this.pendingCooldownTicks > 0 && level != null) {
            this.cooldownEndTick = level.getGameTime() + this.pendingCooldownTicks;
            this.pendingCooldownTicks = 0;
        }
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
}
