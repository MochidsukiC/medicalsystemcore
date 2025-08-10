package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.block.DefibrillatorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DefibrillatorBlockEntity extends BlockEntity {
    private static final int CHARGE_TIME_TICKS = 10 * 20;
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

    // --- 状態を操作するメソッド ---
    public void startCharge() {
        this.chargeProgress = 1;
        setChanged();
    }

    public void resetChargeAndStartCooldown() {
        this.chargeProgress = 0;
        this.arePadsTaken = false;
        this.cooldownEndTick = this.level.getGameTime() + (30 * 20);
        if (level != null) {
            level.setBlock(getBlockPos(), getBlockState().setValue(DefibrillatorBlock.CHARGED, false), 3);
        }
        setChanged();
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

        // 充電中の処理
        if (be.chargeProgress > 0) {
            be.chargeProgress++;
            if (be.chargeProgress >= CHARGE_TIME_TICKS) {
                be.chargeProgress = -1; // 充電完了
                level.setBlock(pos, state.setValue(DefibrillatorBlock.CHARGED, true), 3);
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        }
    }

    public void resetCharge() {
        this.chargeProgress = 0;
        if (level != null) {
            level.setBlock(getBlockPos(), getBlockState().setValue(DefibrillatorBlock.CHARGED, false), 3);
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.putInt("ChargeProgress", this.chargeProgress);
        pTag.putBoolean("ArePadsTaken", this.arePadsTaken);
        pTag.putLong("CooldownEndTick", this.cooldownEndTick); // 追加
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        this.chargeProgress = pTag.getInt("ChargeProgress");
        this.arePadsTaken = pTag.getBoolean("ArePadsTaken");
        this.cooldownEndTick = pTag.getLong("CooldownEndTick"); // 追加
    }

    public void startCooldown() {
        this.cooldownEndTick = this.level.getGameTime() + (30 * 20); // 現在時間 + 30秒
        setChanged();
    }
}