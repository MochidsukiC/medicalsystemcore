package jp.houlab.mochidsuki.medicalsystemcore.block;

import com.mojang.serialization.MapCodec;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.HeadsideMonitorBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.ModBlockEntities;
import jp.houlab.mochidsuki.medicalsystemcore.core.ModDataComponents;
import jp.houlab.mochidsuki.medicalsystemcore.item.HeadsideCableItem;
import jp.houlab.mochidsuki.medicalsystemcore.item.ModItems;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class HeadsideMonitorBlock extends BaseEntityBlock {
    public static final MapCodec<HeadsideMonitorBlock> CODEC = simpleCodec(HeadsideMonitorBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;


    public HeadsideMonitorBlock(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection().getOpposite());
    }


    // Forge の use は手を問わず 1 つのメソッドだったので、26.2 では手を受け取る useItemOn に全体を置く。
    @Override
    protected InteractionResult useItemOn(ItemStack pStack, BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (!pLevel.isClientSide()) {
            if (!MedicalAuthorizationUtil.checkMedicalAuthorization(pPlayer, "ベッドサイドモニターの操作")) {
                return InteractionResult.FAIL;
            }
            if (pLevel.getBlockEntity(pPos) instanceof HeadsideMonitorBlockEntity monitor) {
                ItemStack heldItem = pPlayer.getItemInHand(pHand);

                // このモニターのケーブルを持っていれば返却する
                if (heldItem.is(ModItems.HEAD_SIDE_CABLE)) {
                    if (!HeadsideCableItem.isLinkedTo(heldItem, monitor)) {
                        pPlayer.sendSystemMessage(Component.translatable("message.medicalsystemcore.cable.not_this_monitor").withStyle(ChatFormatting.RED));
                        return InteractionResult.SUCCESS;
                    }
                    monitor.returnCable();
                    heldItem.shrink(1);
                    pPlayer.sendSystemMessage(Component.translatable("message.medicalsystemcore.cable.stored").withStyle(ChatFormatting.YELLOW));
                    return InteractionResult.SUCCESS;
                }

                // ケーブルがまだ取り出されていない場合のみ、ケーブルを与える。
                // 紛失した場合は、しゃがんで素手で右クリックすると再発行する(以前のケーブルは無効になる)
                boolean reissue = monitor.isCableTaken() && heldItem.isEmpty() && pPlayer.isShiftKeyDown();
                if (monitor.isCableTaken() && !reissue) {
                    pPlayer.sendSystemMessage(Component.translatable("message.medicalsystemcore.cable.in_use").withStyle(ChatFormatting.RED));
                    return InteractionResult.SUCCESS;
                }

                ItemStack cableStack = new ItemStack(ModItems.HEAD_SIDE_CABLE);
                // ケーブルに、このモニターの座標と、今回発行した識別子を記録
                cableStack.set(ModDataComponents.MONITOR_POS, pPos);
                cableStack.set(ModDataComponents.CABLE_ID, monitor.issueCable());

                // インベントリが満杯の場合は足元にドロップされ、ケーブルが消えない
                pPlayer.handleExtraItemsCreatedOnUse(cableStack);
                if (reissue) {
                    pPlayer.sendSystemMessage(Component.translatable("message.medicalsystemcore.cable.reissued").withStyle(ChatFormatting.GREEN));
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new HeadsideMonitorBlockEntity(pPos, pState);
    }

    @Override
    protected RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        if (pLevel.isClientSide()) {
            return null;
        }
        return createTickerHelper(pBlockEntityType, ModBlockEntities.HEAD_SIDE_MONITOR_BLOCK_ENTITY, HeadsideMonitorBlockEntity::tick);
    }
}
