package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DefibrillatorBlockEntity extends BlockEntity {
    // lastUseTickを削除し、arePadsTakenに変更
    public boolean arePadsTaken = false;

    public DefibrillatorBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(Medicalsystemcore.DEFIBRILLATOR_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.putBoolean("ArePadsTaken", this.arePadsTaken); // 新しいフラグを保存
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        this.arePadsTaken = pTag.getBoolean("ArePadsTaken"); // 新しいフラグを読み込み
    }
}
