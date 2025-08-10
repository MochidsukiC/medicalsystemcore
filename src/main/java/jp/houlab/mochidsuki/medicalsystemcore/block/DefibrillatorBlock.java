package jp.houlab.mochidsuki.medicalsystemcore.block;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.DefibrillatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class DefibrillatorBlock extends BaseEntityBlock {
    public DefibrillatorBlock(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (!pLevel.isClientSide) {
            if (pLevel.getBlockEntity(pPos) instanceof DefibrillatorBlockEntity be) {
                ItemStack heldItem = pPlayer.getItemInHand(pHand);

                // ケース1: プレイヤーが電極を持っていて、ブロックをクリック -> 電極を戻す
                if (heldItem.is(Medicalsystemcore.ELECTRODE.get())) {
                    be.arePadsTaken = false;
                    heldItem.shrink(1); // 手に持っている電極を消す
                    pPlayer.sendSystemMessage(Component.literal("§e電極を収納しました。"));
                    return InteractionResult.SUCCESS;
                }

                // ケース2: プレイヤーが素手で、かつブロックに電極がある -> 電極を取り出す
                if (heldItem.isEmpty() && !be.arePadsTaken) {
                    be.arePadsTaken = true;
                    pPlayer.setItemInHand(pHand, new ItemStack(Medicalsystemcore.ELECTRODE.get())); // プレイヤーに電極を与える
                    pPlayer.sendSystemMessage(Component.literal("§a電極を準備しました。対象に使用してください。"));
                    return InteractionResult.SUCCESS;
                }

                // ケース3: プレイヤーが素手だが、電極が既に取り出されている
                if (heldItem.isEmpty() && be.arePadsTaken) {
                    pPlayer.sendSystemMessage(Component.literal("§c電極は既に使用中です。"));
                    return InteractionResult.FAIL;
                }
            }
        }
        return InteractionResult.SUCCESS; // クライアント側の腕振り用にSUCCESSを返す
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new DefibrillatorBlockEntity(pPos, pState);
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }
}