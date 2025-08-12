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

    public boolean isCharging() { return this.chargeProgress > 0; }
    public boolean isCharged() { return this.chargeProgress == -1; }

    // 修正: クールダウンタイマーの計算を改善
    public boolean isOnCooldown() {
        if (level == null) return false;
        return level.getGameTime() < this.cooldownEndTick;
    }

    public long getCooldownSecondsLeft() {
        if (level == null) return 0;
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
        this.arePadsTaken = false;
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

        if (be.chargeProgress > 0) {
            be.chargeProgress++;
            int chargeTimeTicks = Config.DEFIBRILLATOR_CHARGE_TIME * 20;

            if (be.chargeProgress >= chargeTimeTicks) {
                be.chargeProgress = -1;
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
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        setChanged();
    }

    public void startCooldown() {
        if (level != null) {
            this.cooldownEndTick = level.getGameTime() + (Config.DEFIBRILLATOR_COOLDOWN * 20L);
        }
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public float getChargeProgress() {
        if (!isCharging()) return 0.0f;
        int chargeTimeTicks = Config.DEFIBRILLATOR_CHARGE_TIME * 20;
        return (float) this.chargeProgress / chargeTimeTicks;
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.putInt("ChargeProgress", this.chargeProgress);
        pTag.putBoolean("ArePadsTaken", this.arePadsTaken);
        // 修正: 相対時間でクールダウンを保存
        if (level != null && isOnCooldown()) {
            long remainingTicks = this.cooldownEndTick - level.getGameTime();
            pTag.putLong("CooldownRemainingTicks", remainingTicks);
        } else {
            pTag.putLong("CooldownRemainingTicks", 0);
        }
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        this.chargeProgress = pTag.getInt("ChargeProgress");
        this.arePadsTaken = pTag.getBoolean("ArePadsTaken");

        // 修正: 相対時間でクールダウンを復元
        long remainingTicks = pTag.getLong("CooldownRemainingTicks");
        if (remainingTicks > 0 && level != null) {
            this.cooldownEndTick = level.getGameTime() + remainingTicks;
        } else {
            this.cooldownEndTick = 0;
        }
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