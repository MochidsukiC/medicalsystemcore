package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.Config;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.block.DefibrillatorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class DefibrillatorBlockEntity extends BlockEntity {
    private int chargeProgress = 0;
    public boolean arePadsTaken = false;
    private long cooldownEndTick = 0;

    public DefibrillatorBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(Medicalsystemcore.DEFIBRILLATOR_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    // --- 状態を問い合わせるためのヘルパーメソッド ---
    public boolean isCharging() { return this.chargeProgress > 0; }
    public boolean isCharged() { return this.chargeProgress == -1; }
    public boolean isOnCooldown() { return this.level.getGameTime() < this.cooldownEndTick; }
    public long getCooldownSecondsLeft() { return (this.cooldownEndTick - this.level.getGameTime()) / 20; }

    // --- 状態を操作するメソッド（Config値使用版） ---
    public void startCharge() {
        this.chargeProgress = 1;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void resetChargeAndStartCooldown() {
        this.chargeProgress = 0;
        this.arePadsTaken = false;
        // Config値を使用したクールダウン時間設定
        this.cooldownEndTick = this.level.getGameTime() + (Config.DEFIBRILLATOR_COOLDOWN * 20L);
        if (level != null) {
            level.setBlock(getBlockPos(), getBlockState().setValue(DefibrillatorBlock.CHARGED, false), 3);
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DefibrillatorBlockEntity be) {
        // 電源が切れたらリセット
        if (!state.getValue(DefibrillatorBlock.POWERED)) {
            be.chargeProgress = 0;
            if (state.getValue(DefibrillatorBlock.CHARGED)) {
                level.setBlock(pos, state.setValue(DefibrillatorBlock.CHARGED, false), 3);
            }
            return;
        }

        // 充電中の処理（Config値使用版）
        if (be.chargeProgress > 0) {
            be.chargeProgress++;
            int chargeTimeTicks = Config.DEFIBRILLATOR_CHARGE_TIME * 20; // 秒からtickに変換

            if (be.chargeProgress >= chargeTimeTicks) {
                be.chargeProgress = -1; // 充電完了
                level.setBlock(pos, state.setValue(DefibrillatorBlock.CHARGED, true), 3);
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                level.sendBlockUpdated(pos, state, state.setValue(DefibrillatorBlock.CHARGED, true), 3);
            }
        }
    }

    public void resetCharge() {
        this.chargeProgress = 0;
        if (level != null) {
            level.setBlock(getBlockPos(), getBlockState().setValue(DefibrillatorBlock.CHARGED, false), 3);
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.putInt("ChargeProgress", this.chargeProgress);
        pTag.putBoolean("ArePadsTaken", this.arePadsTaken);
        pTag.putLong("CooldownEndTick", this.cooldownEndTick);
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        this.chargeProgress = pTag.getInt("ChargeProgress");
        this.arePadsTaken = pTag.getBoolean("ArePadsTaken");
        this.cooldownEndTick = pTag.getLong("CooldownEndTick");
    }

    public void startCooldown() {
        // Config値を使用したクールダウン時間設定
        this.cooldownEndTick = this.level.getGameTime() + (Config.DEFIBRILLATOR_COOLDOWN * 20L);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /**
     * 充電進行度を取得（Config値使用版）
     */
    public float getChargeProgress() {
        if (!isCharging()) return 0.0f;
        int chargeTimeTicks = Config.DEFIBRILLATOR_CHARGE_TIME * 20;
        return (float) this.chargeProgress / chargeTimeTicks;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            load(pkt.getTag());
        }
    }
}