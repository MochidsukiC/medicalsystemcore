package jp.houlab.mochidsuki.medicalsystemcore.block;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.StretcherBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.core.PoseController;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class StretcherBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // ベッドのような低い当たり判定
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);

    public StretcherBlock(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (pLevel.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(pLevel.getBlockEntity(pPos) instanceof StretcherBlockEntity stretcherBE)) {
            return InteractionResult.FAIL;
        }

        // Shiftクリックで担架を回収
        if (pPlayer.isShiftKeyDown()) {
            ServerPlayer occupyingPlayer = stretcherBE.getOccupyingPlayer();

            if (occupyingPlayer != null) {
                // 単純化されたストレッチャー作成
                StretcherEntity stretcherEntity = StretcherEntity.create(
                        pLevel, pPlayer, occupyingPlayer
                );

                // 姿勢制御を更新
                PoseController.setStretcherPose(occupyingPlayer, true);

                pPlayer.sendSystemMessage(Component.literal("§a担架を回収しました。"));
                occupyingPlayer.sendSystemMessage(Component.literal("§e担架が移動式になりました。"));
            } else {
                // プレイヤーが乗っていない場合、アイテムとして回収
                pPlayer.getInventory().add(new ItemStack(Medicalsystemcore.STRETCHER.get()));
                pPlayer.sendSystemMessage(Component.literal("§a担架を回収しました。"));
            }

            pLevel.removeBlock(pPos, false);
            return InteractionResult.SUCCESS;
        }

        // 通常のクリックでプレイヤーを乗せる
        if (pPlayer instanceof ServerPlayer serverPlayer) {
            ServerPlayer occupyingPlayer = stretcherBE.getOccupyingPlayer();

            if (occupyingPlayer != null) {
                pPlayer.sendSystemMessage(Component.literal("§c担架は既に使用されています。"));
                return InteractionResult.FAIL;
            }

            // プレイヤーを担架に乗せる
            stretcherBE.setOccupyingPlayer(serverPlayer);
            serverPlayer.teleportTo(pPos.getX() + 0.5, pPos.getY() + 0.3, pPos.getZ() + 0.5);

            // 体の向きをブロックの向きに合わせる（単純化）
            float blockYaw = pState.getValue(FACING).toYRot();
            serverPlayer.setYRot(blockYaw);
            serverPlayer.setXRot(0);
            serverPlayer.yBodyRot = blockYaw;
            serverPlayer.yBodyRotO = blockYaw;
            serverPlayer.setYHeadRot(blockYaw);
            serverPlayer.xRotO = 0;

            // 姿勢制御を設定
            PoseController.setStretcherPose(serverPlayer, true);

            serverPlayer.sendSystemMessage(Component.literal("§e担架に乗りました。"));
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new StretcherBlockEntity(pPos, pState);
    }
}